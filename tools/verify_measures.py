#!/usr/bin/env python3
"""Comparacion por compas: detecta barras de compas y cabezas rellenas en cada sistema."""
import sys, glob, re
import numpy as np
from PIL import Image
sys.path.insert(0, '.')
from build_songs import parse_file
import verify_pitches as vp

def barlines(im, ls, sp):
    dark = im < 150
    top = int(round(ls[0])); bot = int(round(ls[4]))
    H, W = dark.shape
    cols = []
    for x in range(W):
        seg = dark[top:bot + 1, x]
        if seg.mean() < 0.93: continue
        up = dark[max(0, top - int(sp * 1.2)):top - 1, x].mean() if top > 2 else 0
        dn = dark[bot + 2:min(H, bot + int(sp * 1.2)), x].mean()
        if up < 0.25 and dn < 0.25: cols.append(x)
    groups = []
    for x in cols:
        if groups and x - groups[-1][-1] <= 2: groups[-1].append(x)
        else: groups.append([x])
    return [np.mean(g) for g in groups]

def run(fn, pid, img):
    song = parse_file('songs/' + fn)
    part = [p for p in song['parts'] if p['id'] == pid][0]
    crops = sorted(glob.glob('../../work/' + img + '_s*.png'), key=lambda f: int(re.search(r'_s(\d+)', f).group(1)))
    crops = [c for c in crops if '/m_' not in c]
    bad = 0
    for si, (cp, sysm) in enumerate(zip(crops, part['systems'])):
        im = np.array(Image.open(cp).convert('L'))
        r = vp.detect(cp)
        if r is None: continue
        res, sp = r
        dark = im < 150
        ls = vp.staff_lines(dark)
        bars = barlines(im, ls, sp)
        heads = [h for h in vp.collapse(res, sp) if h[2] == 'f' and -6 <= h[1] <= 12.6]
        # fronteras: barras entre inicio y fin
        edges = [b for b in bars]
        # asignar por posicion
        nm = len(sysm)
        # esperamos nm+1 barras (con la inicial/final) o nm-1 interiores; usamos interiores robustas
        interior = [b for b in edges if b > 4.0 * sp]
        if len(interior) < nm - 1:
            print(f'  ? sistema {si+1}: barras detectadas {len(interior)} < {nm-1}'); 
        bounds = interior[:nm - 1] if len(interior) >= nm - 1 else interior
        def meas_of(x):
            k = 0
            for b in bounds:
                if x > b: k += 1
            return min(k, nm - 1)
        det = {n: [] for n in sysm}
        for x, pos, k in heads:
            det[sysm[meas_of(x)]].append(int(round(pos)))
        for n in sysm:
            m = [x for x in part['measures'] if x['n'] == n][0]
            mine = [nt[0] - 30 for nt in m['notes'] if not (nt[5] & 1) and nt[4] < 96]
            if det[n] != mine:
                bad += 1
                print(f'  DIF {fn} {pid} compas {n}: imagen {det[n]} vs transcrito {mine}')
    return bad

spec = [('triste_payaso.txt', 'bar1', 'hi/tp-3'), ('triste_payaso.txt', 'bar2', 'hi/tp-4'),
        ('condor.txt', 'bar1', 'hi/cp-10'), ('condor.txt', 'bar2', 'hi/cp-11'),
        ('jinetes.txt', 'bar1', 'hi/jc-12'), ('jinetes.txt', 'bar2', 'hi/jc-13')]
tot = 0
for s in spec:
    print('==', s[0], s[1]); tot += run(*s)
print('compases con diferencias:', tot)
