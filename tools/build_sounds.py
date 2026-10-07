#!/usr/bin/env python3
"""Convierte muestras MP3 (FluidR3 GM, CC BY 3.0) a PCM 16 bits mono 22050 Hz con bucle sin saltos."""
import subprocess, json, os, sys, numpy as np
SRC = sys.argv[1]; DST = sys.argv[2]
RATE = 22050
NOTE = {'C':0,'Db':1,'D':2,'Eb':3,'E':4,'F':5,'Gb':6,'G':7,'Ab':8,'A':9,'Bb':10,'B':11}
LOOPED = {'trombone','trumpet','alto_sax','clarinet','tuba'}
def midi_of(name):
    l = name[:-1]; o = int(name[-1]); return 12*(o+1) + NOTE[l]
index = {}
os.makedirs(DST, exist_ok=True)
for inst in sorted(os.listdir(SRC)):
    d = os.path.join(SRC, inst)
    if not os.path.isdir(d): continue
    items = []
    datas = {}
    for f in sorted(os.listdir(d)):
        if not f.endswith('.mp3'): continue
        raw = subprocess.run(['ffmpeg','-v','error','-i',os.path.join(d,f),'-ac','1','-ar',str(RATE),'-f','s16le','-'],capture_output=True).stdout
        a = np.frombuffer(raw, np.int16).astype(np.float32)/32768.0
        nz = np.where(np.abs(a) > 0.003)[0]
        if len(nz): a = a[max(0, nz[0]-20):]
        datas[f[:-4]] = a
    # normalizar el instrumento completo por el RMS medio del sostenido
    rms = np.mean([np.sqrt(np.mean(x[int(0.2*RATE):int(0.9*RATE)]**2)) for x in datas.values()])
    gain = 0.22 / max(rms, 1e-6)
    for name, a in datas.items():
        a = a * gain
        if inst in LOOPED:
            L = int(0.05*RATE); ls = int(0.55*RATE); le = int(1.25*RATE)
            a = a[:le].copy()
            w = np.linspace(0, 1, L, dtype=np.float32)
            a[le-L:le] = a[le-L:le]*(1-w) + a[ls-L:ls]*w
        else:
            a = a[:int(1.6*RATE)].copy()
            fade = int(0.1*RATE); a[-fade:] *= np.linspace(1, 0, fade)
            ls = le = -1
        peak = np.max(np.abs(a))
        if peak > 0.98: a = a*0.98/peak
        pcm = (np.clip(a, -1, 1)*32767).astype('<i2')
        fn = f'{inst}_{midi_of(name)}.pcm'
        pcm.tofile(os.path.join(DST, fn))
        items.append(dict(root=midi_of(name), file=fn, ls=int(ls), le=int(le if le > 0 else len(pcm))))
    index[inst] = sorted(items, key=lambda x: x['root'])
json.dump(dict(rate=RATE, inst=index), open(os.path.join(DST, 'index.json'), 'w'))
print({k: len(v) for k, v in index.items()})
