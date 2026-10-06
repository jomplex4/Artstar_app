#!/usr/bin/env python3
"""Cruza las alturas transcritas con las cabezas de nota detectadas en las imagenes de origen."""
import sys, glob, re, json
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
sys.path.insert(0, '.')
from build_songs import parse_file

def staff_lines(dark):
    H, W = dark.shape
    frac = dark[:, int(W*0.08):int(W*0.92)].mean(axis=1)
    rows = np.where(frac > 0.30)[0]
    lines = []; s = None; p = None
    for r in rows:
        if s is None: s = p = r
        elif r - p <= 2: p = r
        else: lines.append((s + p) / 2.0); s = p = r
    if s is not None: lines.append((s + p) / 2.0)
    best = None
    for i in range(len(lines) - 4):
        g = lines[i:i+5]; d = np.diff(g)
        if d.std() < 2.0 and (best is None or d.mean() > 5): best = g if best is None else best
    return best

def detect(path):
    im = np.array(Image.open(path).convert('L'))
    dark = im < 150
    ls = staff_lines(dark)
    if ls is None: return None
    sp = (ls[-1] - ls[0]) / 4.0
    d = dark.copy()
    t = max(1, int(round(sp * 0.09)))
    for y in ls:
        y0, y1 = int(round(y)) - t - 1, int(round(y)) + t + 1
        for yy in range(y0, y1 + 1):
            if yy < 0 or yy >= d.shape[0]: continue
            up = dark[max(0, y0 - 3), :]; dn = dark[min(d.shape[0] - 1, y1 + 3), :]
            d[yy, :] &= (up | dn)
    # cabezas rellenas: erosion
    k = max(3, int(round(sp * 0.62)))
    er = ndi.binary_erosion(d, structure=np.ones((k, k)))
    lab, n = ndi.label(er)
    heads = []
    for i in range(1, n + 1):
        ys, xs = np.where(lab == i)
        if len(ys) < 6: continue
        h = ys.max() - ys.min() + 1; w = xs.max() - xs.min() + 1
        if h > sp * 1.1 or w > sp * 1.6: continue
        heads.append((xs.mean(), ys.mean(), 'f'))
    # cabezas huecas (blancas): huecos pequenos
    filled = ndi.binary_fill_holes(d)
    holes = filled & ~d
    lab2, n2 = ndi.label(holes)
    for i in range(1, n2 + 1):
        ys, xs = np.where(lab2 == i)
        a = len(ys)
        if a < 0.08 * sp * sp or a > 0.55 * sp * sp: continue
        h = ys.max() - ys.min() + 1; w = xs.max() - xs.min() + 1
        if w < 0.45 * sp or w > 1.4 * sp or h > 0.9 * sp: continue
        # el borde alrededor debe ser oscuro (anillo): comprobar
        heads.append((xs.mean(), ys.mean(), 'h'))
    # descartar zona de clave/armadura/compas
    xmin = 4.2 * sp
    left = None
    heads = [h for h in heads if h[0] > xmin]
    # quitar duplicados cercanos
    heads.sort()
    res = []
    for x, y, k in heads:
        pos = (ls[4] - y) / (sp / 2.0)
        res.append((x, pos, k))
    # pos relativo a la linea inferior = E4 (0)
    return res, sp

def collapse(res, sp):
    out = []
    for x, pos, k in sorted(res):
        if out and abs(x - out[-1][0]) < sp * 0.7 and abs(pos - out[-1][1]) < 1.2:
            continue
        out.append((x, pos, k))
    return out

NAMES = 'CDEFGAB'
def dia_to_name(dia):
    return f"{NAMES[dia % 7]}{dia // 7}"

def main():
    spec = [
        ('condor.txt', 'bar1', 'hi/cp-10'), ('condor.txt', 'bar2', 'hi/cp-11'),
        ('jinetes.txt', 'bar1', 'hi/jc-12'), ('jinetes.txt', 'bar2', 'hi/jc-13'),
        ('triste_payaso.txt', 'bar1', 'hi/tp-3'), ('triste_payaso.txt', 'bar2', 'hi/tp-4'),
    ]
    only = sys.argv[1] if len(sys.argv) > 1 else None
    total_bad = 0
    for fn, pid, img in spec:
        if only and only not in (fn + pid):
            pass
        song = parse_file('songs/' + fn)
        part = [p for p in song['parts'] if p['id'] == pid][0]
        crops = sorted(glob.glob('../../work/' + img + '_s*.png'), key=lambda f: int(re.search(r'_s(\d+)', f).group(1)))
        crops = [c for c in crops if '/m_' not in c]
        print(f"== {fn} {pid}: {len(crops)} sistemas de imagen, {len(part['systems'])} de transcripcion")
        for si, (cp, sysm) in enumerate(zip(crops, part['systems'])):
            r = detect(cp)
            if r is None: print('  sistema', si + 1, 'sin pentagrama'); continue
            res, sp = r
            det = [h for h in collapse(res, sp) if h[2] == 'f' and -6 <= h[1] <= 12.6]
            mine = []
            for n in sysm:
                m = [x for x in part['measures'] if x['n'] == n][0]
                for nt in m['notes']:
                    if nt[5] & 1 or nt[4] >= 96: continue
                    mine.append((n, nt[0] - 30))
            dp = [int(round(p)) for _, p, _ in det]
            mp = [p for _, p in mine]
            import difflib
            ok = (dp == mp)
            tag = 'OK ' if ok else 'DIF'
            if not ok: total_bad += 1
            print(f"  {tag} sistema {si+1} (compases {sysm[0]}-{sysm[-1]}): detectadas {len(dp)}, transcritas {len(mp)}")
            if not ok:
                sm = difflib.SequenceMatcher(a=dp, b=mp, autojunk=False)
                for op, i1, i2, j1, j2 in sm.get_opcodes():
                    if op == 'equal': continue
                    ms = sorted(set(mine[j][0] for j in range(j1, max(j2, j1 + 1)) if j < len(mine)))
                    print(f'     {op}: imagen {dp[i1:i2]} vs transcrito {mp[j1:j2]}  compases {ms}')
    print('sistemas con diferencias:', total_bad)

if __name__ == '__main__':
    main()
