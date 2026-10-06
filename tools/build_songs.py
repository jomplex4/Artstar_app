#!/usr/bin/env python3
"""Convierte los .txt de partituras (formato ART STAR) a JSON para la app y valida cada compas."""
import re, json, sys, glob, os

TICKS = {'w':192,'h':96,'q':48,'e':24,'s':12,'t':6}
LET = {'C':0,'D':1,'E':2,'F':3,'G':4,'A':5,'B':6}
SEMI = {'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}
SHARP_ORDER = ['F','C','G','D','A','E','B']
FLAT_ORDER = ['B','E','A','D','G','C','F']
FL = dict(rs=1, re=2, v1=4, v2=8, seg=16, coda=32)
NF = dict(rest=1, accent=2, stac=4, tie=8, trip=16)

def key_alters(k):
    d = {}
    if k > 0:
        for l in SHARP_ORDER[:k]: d[l] = 1
    elif k < 0:
        for l in FLAT_ORDER[:-k]: d[l] = -1
    return d

def parse_dur(code):
    m = re.fullmatch(r'([whqest])(\.|3)?', code)
    if not m: raise ValueError('duracion invalida: ' + code)
    t = TICKS[m.group(1)]
    if m.group(2) == '.': t = t * 3 // 2
    elif m.group(2) == '3': t = t * 2 // 3
    return t, m.group(2) == '3'

def parse_ranges(s):
    out = []
    for part in s.split(','):
        part = part.strip()
        if not part: continue
        if '-' in part:
            a, b = part.split('-'); out += list(range(int(a), int(b) + 1))
        else:
            out.append(int(part))
    return out

def parse_file(path):
    song = dict(sections=[], parts=[])
    cur_part = None; sysno = 0; sysmap = {}
    for ln, raw in enumerate(open(path, encoding='utf-8'), 1):
        line = raw.rstrip('\n')
        if not line.strip() or line.strip().startswith('#'): continue
        if line.startswith('part:'):
            pid, name = [x.strip() for x in line[5:].split('|')]
            cur_part = dict(id=pid, name=name, measures=[], systems=[]); song['parts'].append(cur_part); continue
        if line.strip() == 'sys':
            cur_part['systems'].append([]); continue
        m = re.match(r'^(\d+):\s*(.*)$', line)
        if m and cur_part is not None:
            n = int(m.group(1)); body = m.group(2)
            flags = 0; text = ''; rm = ''
            fm = re.match(r'^\{([^}]*)\}\s*(.*)$', body)
            if fm:
                for f in fm.group(1).split():
                    if f in FL: flags |= FL[f]
                    elif f.startswith('t='): text = f[2:].replace('_', ' ')
                    elif f.startswith('rm='): rm = f[3:]
                    else: raise ValueError(f'{path}:{ln} flag desconocido {f}')
                body = fm.group(2)
            notes = []; acc_state = {}
            klt = key_alters(song['key'])
            mlen = song['num'] * (192 // song['den'])
            for tok in body.split():
                if tok == 'R':
                    notes.append([0, 0, 0, 0, mlen, NF['rest'] | 32]); continue
                dm = re.match(r"^([whqest][.3]?):(.*)$", tok)
                if not dm: raise ValueError(f'{path}:{ln} token invalido {tok}')
                dur, trip = parse_dur(dm.group(1)); rest = dm.group(2)
                fl = 0
                while rest and rest[-1] in ">'~":
                    c = rest[-1]; rest = rest[:-1]
                    fl |= {'>': NF['accent'], "'": NF['stac'], '~': NF['tie']}[c]
                if trip: fl |= NF['trip']
                if rest == 'r':
                    notes.append([0, 0, 0, 0, dur, fl | NF['rest']]); continue
                pm = re.fullmatch(r'([A-G])([#bn]?)(\d)', rest)
                if not pm: raise ValueError(f'{path}:{ln} nota invalida {tok}')
                L, acc, octv = pm.group(1), pm.group(2), int(pm.group(3))
                keyid = (L, octv)
                show = 0
                if acc == '#': alter = 1; show = 1; acc_state[keyid] = 1
                elif acc == 'b': alter = -1; show = 2; acc_state[keyid] = -1
                elif acc == 'n': alter = 0; show = 3; acc_state[keyid] = 0
                else: alter = acc_state.get(keyid, klt.get(L, 0))
                dia = octv * 7 + LET[L]
                midi = 12 * (octv + 1) + SEMI[L] + alter
                notes.append([dia, alter, show, midi, dur, fl])
            tot = sum(x[4] for x in notes)
            if tot != mlen:
                raise ValueError(f'{path}: parte {cur_part["id"]} compas {n}: suma {tot} != {mlen}  ({body})')
            cur_part['measures'].append(dict(n=n, f=flags, t=text, rm=rm, notes=notes))
            cur_part['systems'][-1].append(n)
            continue
        m = re.match(r'^(\w+):\s*(.*)$', line)
        if m and cur_part is None:
            k, v = m.group(1), m.group(2).strip()
            if k == 'time':
                a, b = v.split('/'); song['num'] = int(a); song['den'] = int(b)
            elif k == 'key': song['key'] = int(v)
            elif k == 'bpm': song['bpm'] = int(v)
            elif k == 'order': song['order'] = parse_ranges(v)
            elif k == 'section':
                nm, rg = [x.strip() for x in v.split('|')]
                song['sections'].append(dict(name=nm, order=parse_ranges(rg)))
            else: song[k] = v
            continue
        raise ValueError(f'{path}:{ln} linea no reconocida: {line}')
    return song

def check_song(song, path):
    for p in song['parts']:
        nums = [m['n'] for m in p['measures']]
        if len(set(nums)) != len(nums): raise ValueError(f'{path}: compases repetidos en {p["id"]}')
        if nums != list(range(nums[0], nums[0] + len(nums))):
            raise ValueError(f'{path}: numeracion no consecutiva en {p["id"]}: {nums}')
        have = set(nums)
        for n in song.get('order', nums):
            if n not in have: raise ValueError(f'{path}: order menciona compas {n} inexistente en {p["id"]}')
        for s in song['sections']:
            for n in s['order']:
                if n not in have: raise ValueError(f'{path}: seccion {s["name"]} usa compas {n} inexistente')
        # ligaduras: la nota siguiente en el orden de ejecucion debe poder igualar
    return True

def to_json(song):
    out = dict(id=song['id'], title=song['title'], subtitle=song.get('subtitle', ''), meta=song.get('meta', ''),
               cat=song.get('cat', 'song'), group=song.get('group', ''), desc=song.get('desc', ''),
               num=song['num'], den=song['den'], key=song['key'], bpm=song['bpm'],
               sections=song['sections'], parts=[])
    for p in song['parts']:
        order = song.get('order') or [m['n'] for m in p['measures']]
        out['parts'].append(dict(id=p['id'], name=p['name'], order=order,
                                 measures=[dict(n=m['n'], f=m['f'], t=m['t'], rm=m['rm'], notes=m['notes']) for m in p['measures']]))
    return out

if __name__ == '__main__':
    dst = sys.argv[-1]
    os.makedirs(dst, exist_ok=True)
    index = []
    paths = []
    for src in sys.argv[1:-1]: paths += sorted(glob.glob(os.path.join(src, '*.txt')))
    for path in paths:
        song = parse_file(path); check_song(song, path)
        js = to_json(song)
        json.dump(js, open(os.path.join(dst, song['id'] + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
        nm = sum(len(p['measures']) for p in song['parts']) // len(song['parts'])
        print(f"OK {song['id']}: {len(song['parts'])} partes, {nm} compases por parte")
        index.append(dict(id=song['id'], title=song['title'], subtitle=song.get('subtitle', ''), meta=song.get('meta', ''),
                          cat=song.get('cat', 'song'), group=song.get('group', ''), parts=[dict(id=p['id'], name=p['name']) for p in song['parts']],
                          bars=nm))
    json.dump(index, open(os.path.join(dst, 'index.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
