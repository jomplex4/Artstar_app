#!/usr/bin/env python3
"""Revisa que las partes suenen bien juntas: busca choques de medio tono sostenidos entre instrumentos."""
import sys, json
sys.path.insert(0, '.')
from build_songs import parse_file
TR = dict(bar=-14, tpt=-2, alto=-9, clar=-2, tbn=0, tuba=0, glock=0)
def timeline(part, mlen):
    ev = {}
    for m in part['measures']:
        t = 0
        for n in m['notes']:
            if not (n[5] & 1):
                for g in range(t, t + n[4], 12):
                    ev[(m['n'], g)] = n[3] + TR[part['inst']]
            t += n[4]
    return ev
for fn in sys.argv[1:]:
    s = parse_file('songs/' + fn)
    mlen = s['num'] * (192 // s['den'])
    tls = [(p['id'], timeline(p, mlen)) for p in s['parts'] if p.get('attrs', {}).get('hidden') != '1']
    bad = {}
    keys = set(k for _, tl in tls for k in tl)
    for k in sorted(keys):
        notes = [(pid, tl[k]) for pid, tl in tls if k in tl]
        for i in range(len(notes)):
            for j in range(i + 1, len(notes)):
                iv = abs(notes[i][1] - notes[j][1]) % 12
                if iv in (1, 11):
                    bad.setdefault(k[0], set()).add((notes[i][0], notes[j][0]))
    # se reportan los compases con choque en al menos 2 posiciones de semicorchea
    count = {}
    for k in sorted(keys):
        notes = [(pid, tl[k]) for pid, tl in tls if k in tl]
        if any(abs(a[1]-b[1]) % 12 in (1, 11) for x, a in enumerate(notes) for b in notes[x+1:]):
            count[k[0]] = count.get(k[0], 0) + 1
    flagged = {m: sorted(bad[m]) for m in bad if count.get(m, 0) >= 2}
    print(f"== {fn}: {len(flagged)} compases con choques de medio tono")
    for m in sorted(flagged): print('  compás', m, flagged[m])
