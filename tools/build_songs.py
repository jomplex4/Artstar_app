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
    m = re.fullmatch(r'([whqest])(\.\.|\.|3)?', code)
    if not m: raise ValueError('duracion invalida: ' + code)
    t = TICKS[m.group(1)]
    if m.group(2) == '..': t = t * 7 // 4
    elif m.group(2) == '.': t = t * 3 // 2
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
            fs = [x.strip() for x in line[5:].split('|')]
            pid, name = fs[0], fs[1]
            attrs = dict(kv.split('=', 1) for kv in fs[2:] if '=' in kv)
            cur_part = dict(id=pid, name=name, measures=[], systems=[],
                            key=int(attrs.get('key', song.get('key', 0))), inst=attrs.get('inst', 'bar'), attrs=attrs)
            song['parts'].append(cur_part); continue
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
            klt = key_alters(cur_part['key'])
            mlen = song['num'] * (192 // song['den'])
            for tok in body.split():
                if tok == 'R':
                    notes.append([0, 0, 0, 0, mlen, NF['rest'] | 32]); continue
                dm = re.match(r"^([whqest](?:\.\.|\.|3)?):(.*)$", tok)
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
    # partes derivadas: copia transpuesta de otra parte (para instrumentos sin partitura propia)
    byid = {p['id']: p for p in song['parts']}
    for p in list(song['parts']):
        a = p.get('attrs', {})
        if 'from' in a:
            src = byid[a['from']]
            d = transpose_part(src, int(a['semis']), int(a['steps']), src['key'] + int(a.get('keyd', 0)), p['id'], p['name'], p['inst'])
            d['attrs'] = a
            p.update(d)
    song['parts'] = [p for p in song['parts'] if p.get('attrs', {}).get('hidden') != '1']
    return song


LETTERS = 'CDEFGAB'
def transpose_part(p, semis, steps, new_key, pid, name, inst):
    """Transpone una parte ya resuelta (notas con dia/alter/midi) y recalcula alteraciones visibles."""
    ka = key_alters(new_key)
    out = dict(id=pid, name=name, key=new_key, inst=inst, systems=p['systems'], measures=[])
    for m in p['measures']:
        state = {}
        notes = []
        for n in m['notes']:
            dia, alter, show, midi, dur, fl = n
            if fl & 1:
                notes.append(list(n)); continue
            d2 = dia + steps; m2 = midi + semis
            L = LETTERS[d2 % 7]; octv = d2 // 7
            nat = 12 * (octv + 1) + SEMI[L]
            a2 = max(-2, min(2, m2 - nat))
            cur = state.get((L, octv), ka.get(L, 0))
            sh = 0 if a2 == cur else {1: 1, -1: 2, 0: 3}.get(a2, 0)
            state[(L, octv)] = a2
            notes.append([d2, a2, sh, m2, dur, fl])
        out['measures'].append(dict(m, notes=notes))
    return out

def shift_range(p, semis, steps, key, pid, name, inst, lo, hi):
    """Transpone y, si queda fuera del registro del instrumento, mueve la parte de octava."""
    q = transpose_part(p, semis, steps, key, pid, name, inst)
    mids = [n[3] for m in q['measures'] for n in m['notes'] if not n[5] & 1]
    if mids and max(mids) > hi: q = transpose_part(p, semis - 12, steps - 7, key, pid, name, inst)
    elif mids and min(mids) < lo: q = transpose_part(p, semis + 12, steps + 7, key, pid, name, inst)
    return q


STD = [192, 144, 96, 72, 48, 36, 24, 18, 12, 9, 6]
RANGES = dict(bar=(54, 86), tpt=(54, 86), alto=(58, 89), tbn=(40, 70), tuba=(34, 65))

def split_std(d):
    """Parte una duracion en figuras normales (se unen con ligadura)."""
    out = []
    while d > 0:
        for v in STD:
            if v <= d:
                out.append(v); d -= v; break
        else:
            raise ValueError(f'duracion no representable: {d}')
    return out

def make_continuous(song):
    """Melodia continua para Aprender: quita compases iniciales vacios, convierte el silencio inicial en anacrusa (lead)
    y absorbe los demas silencios alargando la nota anterior (con ligadura si cruza la barra)."""
    base = song['parts'][0]
    ms = base['measures']
    while ms and all(n[5] & 1 for n in ms[0]['notes']): ms.pop(0)
    for i, m in enumerate(ms): m['n'] = i + 1
    base['systems'] = [[m['n'] for m in ms]]
    mlen = song['num'] * (192 // song['den'])
    first = ms[0]['notes']
    lead = 0
    while first and first[0][5] & 1:
        if first[0][5] & 16: raise ValueError('silencio de tresillo inicial')
        lead += first[0][4]; first.pop(0)
    song['lead'] = lead
    prev = None                      # ultima nota logica (dict)
    allm = []
    for mi, m in enumerate(ms):
        logical = []
        for n in m['notes']:
            if n[5] & 1:
                if n[5] & 16: raise ValueError('silencio de tresillo')
                if prev is None: raise ValueError('silencio sin nota previa')
                if prev['n'][5] & 16: raise ValueError('silencio junto a un tresillo')
                if logical and logical[-1] is prev:
                    prev['dur'] += n[4]; prev['ext'] = True
                else:                # el silencio abre el compas: continua la nota del compas anterior
                    prev['tie_out'] = True
                    cont = dict(n=[prev['n'][0], prev['n'][1], 0, prev['n'][3], 0, 0], dur=n[4], ext=False, cont=True, tie_out=False)
                    logical.append(cont); prev = cont
            else:
                cur = dict(n=list(n), dur=n[4], ext=False, cont=False, tie_out=False)
                logical.append(cur); prev = cur
        allm.append(logical)
    for m, logical in zip(ms, allm):
        notes = []
        for e in logical:
            fl_orig = e['n'][5]
            pieces = [e['dur']] if fl_orig & 16 else split_std(e['dur'])
            for k, d in enumerate(pieces):
                fl = fl_orig & ~(8 | 4 | 2) if k > 0 else fl_orig & ~8
                if e['ext']: fl &= ~4                          # un staccato alargado deja de serlo
                if k == 0 and (fl_orig & 2): fl |= 2
                if k < len(pieces) - 1 or e['tie_out'] or (fl_orig & 8 and not e['cont']): fl |= 8
                shown = e['n'][2] if k == 0 else 0
                notes.append([e['n'][0], e['n'][1], shown, e['n'][3], d, fl])
        m['notes'] = notes
    for i, m in enumerate(ms):
        tot = sum(x[4] for x in m['notes'])
        want = mlen - (lead if i == 0 else 0)
        if tot != want: raise ValueError(f"{song['id']}: compas {m['n']} suma {tot} != {want}")

def check_ranges(song):
    for p in song['parts']:
        lo, hi = RANGES[p['inst']]
        mids = [n[3] for m in p['measures'] for n in m['notes'] if not n[5] & 1]
        if mids and (min(mids) < lo or max(mids) > hi):
            print(f"  AVISO rango {song['id']} {p['id']}: {min(mids)}..{max(mids)} fuera de {lo}..{hi}")

def lesson_parts(song):
    """Una leccion se escribe para instrumento en Si bemol (nota escrita). Se generan las versiones de los 5 instrumentos."""
    base = song['parts'][0]
    if song.get('concert') == '1':          # la melodia esta en altura real (piano): se pasa a nota escrita en Si bemol
        base = transpose_part(base, 2, 1, base['key'] + 2, 'bar1', 'Bajo / Barítono', 'bar')
    bar = shift_range(base, 0, 0, base['key'], 'bar1', 'Bajo / Barítono', 'bar', 54, 86)
    tpt = shift_range(base, 0, 0, base['key'], 'tpt1', 'Trompeta', 'tpt', 54, 86)
    mx = max((n[3] for m in base['measures'] for n in m['notes'] if not n[5] & 1), default=60)
    semis, steps = (7, 4) if mx + 7 <= 86 else (-5, -3)
    alto = shift_range(base, semis, steps, base['key'] + 1, 'alto1', 'Saxo alto', 'alto', 58, 89)
    tbn = shift_range(base, -2, -1, base['key'] - 2, 'tbn1', 'Trombón', 'tbn', 40, 70)
    tuba = shift_range(base, -26, -15, base['key'] - 2, 'tuba1', 'Tuba', 'tuba', 34, 65)
    song['parts'] = [bar, tpt, alto, tbn, tuba]

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
               num=song['num'], den=song['den'], key=song['key'], bpm=song['bpm'], lead=song.get('lead', 0),
               sections=song['sections'], parts=[])
    for p in song['parts']:
        order = song.get('order') or [m['n'] for m in p['measures']]
        out['parts'].append(dict(id=p['id'], name=p['name'], key=p['key'], inst=p['inst'], order=order,
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
        if song.get('cat') == 'lesson':
            if song.get('group') == 'Canciones': make_continuous(song)
            lesson_parts(song); check_ranges(song)
        js = to_json(song)
        json.dump(js, open(os.path.join(dst, song['id'] + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
        nm = sum(len(p['measures']) for p in song['parts']) // len(song['parts'])
        print(f"OK {song['id']}: {len(song['parts'])} partes, {nm} compases por parte")
        index.append(dict(id=song['id'], title=song['title'], subtitle=song.get('subtitle', ''), meta=song.get('meta', ''),
                          cat=song.get('cat', 'song'), group=song.get('group', ''), parts=[dict(id=p['id'], name=p['name'], key=p['key'], inst=p['inst']) for p in song['parts']],
                          bars=nm))
    json.dump(index, open(os.path.join(dst, 'index.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    usage = {}
    for path in paths:
        song = parse_file(path); check_song(song, path)
        if song.get('cat', 'song') != 'song': continue
        for pt in song['parts']:
            u = usage.setdefault(pt['inst'], {})
            for m in pt['measures']:
                for n in m['notes']:
                    if not n[5] & 1: u.setdefault(str(n[3]), set()).add(song['title'])
    json.dump({i: {k: sorted(v) for k, v in d.items()} for i, d in usage.items()}, open(os.path.join(dst, 'usage.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
