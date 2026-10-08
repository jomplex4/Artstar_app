import json, sys
TR=dict(bar=-14,tpt=-2,alto=-9,tbn=0,tuba=0)
def events(p):
    ev=[]
    for m in p['measures']:
        t=0
        for n in m['notes']:
            d=n[4]
            if not n[5]&1: ev.append((m['n'],t,t+d,n[3]+TR[p['inst']],d))
            t+=d
    return ev
j=json.load(open(sys.argv[1])); minlen=int(sys.argv[2]) if len(sys.argv)>2 else 48
P={p['id']:events(p) for p in j['parts']}
ids=list(P); out={}
for i,a in enumerate(ids):
    for b in ids[i+1:]:
        for (ma,s1,e1,x,d1) in P[a]:
            if d1<minlen: continue
            for (mb,s2,e2,y,d2) in P[b]:
                if mb!=ma or d2<minlen: continue
                ov=min(e1,e2)-max(s1,s2)
                if ov>=minlen and abs(x-y)%12 in (1,11):
                    out.setdefault(ma,[]).append((a,b,x%12,y%12,s1,s2))
for m in sorted(out): print(m, out[m][:6])
