#!/usr/bin/env python3
"""Banda grabada para los temas de rock, folk y house.

Bancos CC0 de Karoryfer Lecolds (https://github.com/sfzinstruments), además de la orquesta VSCO 2 CE:
  karoryfer.big-rusty-drums             batería acústica completa
  karoryfer.emilyguitar                 guitarra eléctrica (DI), también como bajo una octava abajo
  karoryfer.meatbass                    contrabajo (pizzicato)
Las muestras no se incluyen en el repositorio; se descargan aparte.

Uso: python3 tools/banda.py <carpeta_VSCO> <carpeta_bancos> <salida> [game|neutron|duro|all]
"""
import glob
import os
import re
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import orquesta as oq  # noqa: E402

mm = oq.mm
SR = oq.SR
Mix = oq.Mix
Inst = oq.Inst
hit = oq.hit
triad = oq.triad
MIN, MAJ = oq.MIN, oq.MAJ
RNG = np.random.default_rng(23)
PC = {"c": 0, "d": 2, "e": 4, "f": 5, "g": 7, "a": 9, "b": 11}


def read_any(path):
    """Cualquier formato (wav/flac) a mono float al ritmo del motor, sin silencio inicial y con nivel parejo."""
    try:
        raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                             capture_output=True, check=True).stdout
        a = np.frombuffer(raw, "<f4").astype(np.float64)
    except subprocess.CalledProcessError:
        a = wav_manual(path)
    pk = np.max(np.abs(a)) + 1e-9
    on = int(np.argmax(np.abs(a) > pk * 0.02))
    a = a[max(0, on - int(0.003 * SR)):]
    return a


def wav_manual(path):
    """WAV que ffmpeg rechaza (cabecera extensible rara): se lee a mano."""
    b = open(path, "rb").read()
    p, ch, sr, bits, fmt, data = 12, 1, 44100, 16, 1, None
    while p + 8 <= len(b):
        cid = b[p:p + 4]; ln = int.from_bytes(b[p + 4:p + 8], "little")
        if cid == b"fmt ":
            fmt = int.from_bytes(b[p + 8:p + 10], "little"); ch = int.from_bytes(b[p + 10:p + 12], "little")
            sr = int.from_bytes(b[p + 12:p + 16], "little"); bits = int.from_bytes(b[p + 22:p + 24], "little")
            if fmt == 0xFFFE and ln >= 26:
                fmt = int.from_bytes(b[p + 32:p + 34], "little")
        elif cid == b"data":
            data = b[p + 8:p + 8 + ln]; break
        p += 8 + ln + (ln & 1)
    if fmt == 3:
        a = np.frombuffer(data[: len(data) // 4 * 4], "<f4" if bits == 32 else "<f8").astype(np.float64)
    elif bits == 24:
        u = np.frombuffer(data[: len(data) // 3 * 3], np.uint8).reshape(-1, 3).astype(np.int32)
        a = (u[:, 0] | (u[:, 1] << 8) | (u[:, 2] << 16)); a = np.where(a >= 1 << 23, a - (1 << 24), a) / 8388608.0
    else:
        a = np.frombuffer(data[: len(data) // 2 * 2], "<i2") / 32768.0
    a = a[: len(a) // ch * ch].reshape(-1, ch).mean(axis=1)
    if sr != SR:
        a = np.interp(np.arange(0, len(a) - 1, sr / SR), np.arange(len(a)), a)
    return a


def norm(a, target=0.2):
    win = int(0.15 * SR)
    e = np.convolve(a * a, np.ones(win) / win, mode="valid") if len(a) > win else [np.mean(a * a)]
    return a * (target / max(np.sqrt(np.max(e)), 1e-6))


def pitch_of(a):
    """Frecuencia fundamental por autocorrelación (para calibrar la octava de cada banco)."""
    seg = a[int(0.05 * SR):int(0.35 * SR)]
    seg = seg - seg.mean()
    ac = np.fft.irfft(np.abs(np.fft.rfft(seg, 2 * len(seg))) ** 2)[:len(seg)]
    lo, hi = int(SR / 1000), int(SR / 35)
    lag = lo + int(np.argmax(ac[lo:hi]))
    return SR / lag


def name_midi(n):
    m = re.search(r"(?:^|_)([a-g])([b#]?)(-?\d)(?:_|\.)", n.lower())
    if not m:
        return None
    acc = -1 if m.group(2) == "b" else 1 if m.group(2) == "#" else 0
    return 12 * (int(m.group(3)) + 1) + PC[m.group(1)] + acc


def inst_from(pattern, shift=0, level=0.2):
    """Instrumento por notas; calibra la octava del banco comparando con la altura medida."""
    files = sorted(glob.glob(pattern))
    items, seen = [], set()
    for p in files:
        m = name_midi(os.path.basename(p))
        if m is None or m in seen:
            continue
        seen.add(m)
        items.append((m, norm(read_any(p), level)))
    if not items:
        raise SystemExit("sin muestras: " + pattern)
    mid = items[len(items) // 2]
    f = pitch_of(mid[1])
    measured = 69 + 12 * np.log2(f / 440.0)
    off = int(round((measured - mid[0]) / 12.0)) * 12
    return Inst([(m + off + shift, a) for m, a in items])


class Kit:
    """Batería: micrófono cercano + aéreos, varias intensidades y alternancia de golpes."""

    def __init__(self, root):
        R = lambda p: os.path.join(root, "karoryfer.big-rusty-drums/Samples", p)
        self.cache = {}
        self.sets = {
            "kick": R("kick_24/sc/cl/k_sc_vl{v}_rr{r}.flac"),
            "snare": R("snare_14/center/top/sn_center_vl{v}_rr{r}.flac"),
            "rim": R("snare_14/rimshot/top/sn_rimshot_vl{v}_rr{r}.flac"),
            "side": R("snare_14/sidestick/top/sn_sidestick_vl{v}_rr{r}.flac"),
            "brush": R("snare_14/brush/top/sn_brush_vl{v}_rr{r}.flac"),
            "hh": R("hihat_14/cl/cl/ht_cl_vl{v}_rr{r}.flac"),
            "hhb": R("hihat_14/cl_brush/cl/ht_cl_b_vl{v}_rr{r}.flac"),
            "ho": R("hihat_14/ho/cl/ht_ho_vl{v}_rr{r}.flac"),
            "crash": R("crash_17/cr/cl/cr_vl{v}_rr{r}.flac"),
            "ride": R("ride_22/rd/cl/rd_vl{v}_rr{r}.flac"),
            "t1": R("tom_14/sc/cl/t14_sc_vl{v}_rr{r}.flac"),
            "t2": R("tom_15/sc/cl/t15_sc_vl{v}_rr{r}.flac"),
            "t3": R("tom_18/sc/cl/t18_sc_vl{v}_rr{r}.flac"),
        }
        self.maxv = {}
        for k, pat in self.sets.items():
            vs = [int(re.search(r"vl(\d+)", p).group(1)) for p in glob.glob(pat.replace("{v}", "*").replace("{r}", "*"))]
            if not vs:
                # nombre distinto: busca cualquier archivo en la carpeta
                d = os.path.dirname(pat)
                alt = sorted(glob.glob(os.path.join(d, "*.flac")))
                if alt:
                    pre = re.sub(r"_vl\d+_rr\d+\.flac$", "", os.path.basename(alt[0]))
                    self.sets[k] = os.path.join(d, pre + "_vl{v}_rr{r}.flac")
                    vs = [int(re.search(r"vl(\d+)", p).group(1)) for p in alt]
            self.maxv[k] = max(vs) if vs else 0
        self.lvl = {}

    def get(self, k, vel=1.0, dur=None):
        mv = self.maxv.get(k, 0)
        if not mv:
            return np.zeros(10)
        v = int(np.clip(round(vel * mv), 1, mv))
        r = int(RNG.integers(1, 5))
        key = (k, v, r)
        if key not in self.cache:
            p = self.sets[k].format(v=v, r=r)
            if not os.path.exists(p):
                p = self.sets[k].format(v=v, r=1)
            a = read_any(p)
            if k not in self.lvl:  # nivel de referencia de la pieza a su intensidad máxima
                ref = read_any(self.sets[k].format(v=mv, r=1))
                self.lvl[k] = 0.5 / (np.max(np.abs(ref)) + 1e-9)
            self.cache[key] = a * self.lvl[k]
        return hit(self.cache[key], dur)


class Band:
    def __init__(self, root):
        self.kit = Kit(root)
        E = os.path.join(root, "karoryfer.emilyguitar/notes")
        self.egtr = inst_from(os.path.join(E, "*_f_rr1.wav"))
        self.egtr_m = inst_from(os.path.join(E, "*_mp_rr2.wav"))
        self.ebass = inst_from(os.path.join(E, "*_f_rr2.wav"), shift=-12, level=0.25)
        # guitarra limpia (las muestras del Gretsch del repositorio vienen dañadas): Emily en dinámicas suaves
        self.gretsch = inst_from(os.path.join(E, "*_mf_rr1.wav"))
        self.gretsch_p = inst_from(os.path.join(E, "*_p_rr2.wav"))
        B = os.path.join(root, "karoryfer.meatbass/Samples/pizz")
        self.pizz = inst_from(os.path.join(B, "*_vl3_rr1.wav"), level=0.25)


def lowpass(x, fc):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X / np.sqrt(1 + (f / fc) ** 4), len(x))


def highpass(x, fc):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    r = (f / fc) ** 4
    return np.fft.irfft(X * np.sqrt(r / (1 + r)), len(x))


def dip(x, fc, db, q=1.2):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = 1 + (10 ** (db / 20) - 1) * np.exp(-((f - fc) / (fc / q)) ** 2)
    return np.fft.irfft(X * g, len(x))


def place(buf, t, sig, g=1.0):
    i = int(t * SR)
    if i >= len(buf):
        return
    seg = sig[: len(buf) - i]
    buf[i:i + len(seg)] += seg * g


def chord_gtr(inst, buf, t, notes, dur, gain, strum=0.008, up=False):
    seq = notes[::-1] if up else notes
    for i, n in enumerate(seq):
        place(buf, t + i * strum + RNG.uniform(0, 0.003), inst.note(n, dur, att=0.002, rel=0.08), gain)


# ------------------------------------------------------------------ Épica en clave rock (todo grabado)
def game_rock(lib, band):
    k = band.kit
    bpm = 112
    beat = 60.0 / bpm
    bar = 4 * beat
    m = Mix(32 * bar, tail=4.0)
    D, Bb, F, C, G, A = 38, 34, 41, 36, 31, 33
    prog = [(D, MIN), (D, MIN), (Bb, MAJ), (A, MAJ),
            (D, MIN), (Bb, MAJ), (F, MAJ), (C, MAJ), (D, MIN), (G, MIN), (Bb, MAJ), (A, MAJ),
            (Bb, MAJ), (C, MAJ), (D, MIN), (A, MAJ),
            (D, MIN), (Bb, MAJ), (F, MAJ), (C, MAJ), (G, MIN), (Bb, MAJ), (A, MAJ), (A, MAJ),
            (Bb, MAJ), (F, MAJ), (G, MIN), (A, MAJ),
            (D, MIN), (Bb, MAJ), (C, MAJ), (A, MAJ)]
    sec = lambda b: "intro" if b < 4 else "A" if b < 12 else "up" if b < 16 else "B" if b < 24 else "bridge" if b < 28 else "ret"
    gl = np.zeros(len(m.L)); gr = np.zeros(len(m.L)); bass = np.zeros(len(m.L)); lead = np.zeros(len(m.L))
    drums = np.zeros(len(m.L))
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        s = sec(b)
        full = s in ("A", "B", "ret", "up")
        r = root + 12 if root < 36 else root  # cuerda grave de la guitarra
        pc = [r, r + 7, r + 12]
        tri = triad(root, q)
        # guitarras rítmicas dobladas (L/R distintas tomas)
        if full:
            for kk in range(8):
                mute = s != "B" and kk not in (0, 3, 6) or s == "B" and kk % 2 == 1
                d = beat * (0.16 if mute else 0.95)
                inst = band.egtr_m if mute else band.egtr
                chord_gtr(inst, gl, t0 + kk * beat / 2, pc, d, 0.9 if not mute else 0.75)
                chord_gtr(inst, gr, t0 + kk * beat / 2 + 0.011, pc, d, 0.9 if not mute else 0.75)
        elif s == "bridge":
            for kk in (0, 2):
                chord_gtr(band.egtr, gl, t0 + kk * beat, [r, r + 7, r + 12, r + 12 + q + 7 - 7], beat * 2, 0.5, strum=0.03)
        # bajo eléctrico
        if s != "intro":
            for kk in range(8):
                n = root if kk != 7 else root + (7 if s == "B" else 0)
                place(bass, t0 + kk * beat / 2, band.ebass.note(n, beat * 0.45, att=0.003, rel=0.06), 0.9 if kk % 2 == 0 else 0.7)
        else:
            place(bass, t0, band.ebass.note(root, bar, att=0.003, rel=0.4), 0.8)
        # batería
        if full:
            for kk in range(8):
                place(drums, t0 + kk * beat / 2, k.get("hh" if s != "B" else "ride", 0.55 + 0.35 * (kk % 2 == 0)), 0.5)
            for bt in ((0, 1.5, 2, 3.5) if s == "B" else (0, 1.75, 2.5)):
                place(drums, t0 + bt * beat, k.get("kick", 0.95), 1.0)
            place(drums, t0 + beat, k.get("snare", 0.9), 0.85)
            place(drums, t0 + 3 * beat, k.get("snare", 1.0), 0.9)
        elif s == "bridge":
            place(drums, t0, k.get("t3", 0.7, 2.0), 0.7)
            place(drums, t0 + 2 * beat, k.get("t3", 0.5, 2.0), 0.55)
            for kk in range(4):
                place(drums, t0 + kk * beat, k.get("ride", 0.4), 0.35)
        elif s == "intro":
            place(drums, t0, k.get("t3", 0.8, 2.0), 0.8)
            if b == 3:
                for kk in range(8):
                    place(drums, t0 + 2 * beat + kk * beat / 4, k.get("snare", 0.4 + kk * 0.07), 0.7)
        # rellenos de toms antes de cada sección
        if b in (11, 15, 23, 31):
            for kk, tom in enumerate(["t1", "t1", "t2", "t2", "t3", "t3", "t3", "t3"]):
                place(drums, t0 + 2 * beat + kk * beat / 4, k.get(tom, 0.8 + 0.02 * kk, 1.0), 0.85)
        if b in (4, 12, 16, 20, 24, 28):
            place(drums, t0, k.get("crash", 1.0), 0.75)
            place(drums, t0, k.get("kick", 1.0), 1.0)
        # orquesta en segundo plano
        if s != "intro":
            for i, n in enumerate(tri):
                m.add(t0, lib.vla.note(n + 24 if n + 24 < 70 else n + 12, bar + 0.2, att=0.18, rel=0.6), 0.12, pan=0.3 - 0.1 * i)
            m.add(t0, lib.vln.note(tri[2] + 36, bar + 0.2, att=0.2, rel=0.6), 0.11, pan=0.45)
        else:
            m.add(t0, lib.vc.note(root + 12, bar + 0.2, att=0.25, rel=0.8), 0.35, pan=-0.3)
            m.add(t0 + 2 * beat, lib.vln.note(tri[2] + 48, 2 * beat + 0.3, att=0.3, rel=0.8), 0.15, pan=0.5)
        if s == "up":
            for bt in (0, 1.5, 3):
                for n in (root + 12, root + 19, root + 24):
                    m.add(t0 + bt * beat, lib.hn_st.note(n, 0.5, att=0.003, rel=0.25), 0.22, pan=-0.2)
    # melodías: trompas en A, guitarra solista en el clímax, violines doblando suave
    hn_mel = [(62, 0, 2), (69, 2, 2), (65, 4, 1), (67, 5, 1), (69, 6, 1.5), (70, 7.5, 0.5), (72, 8, 3), (69, 11, 1),
              (67, 12, 2), (64, 14, 1), (67, 15, 1), (74, 16, 2), (69, 18, 1), (65, 19, 1), (67, 20, 1.5), (69, 21.5, 0.5),
              (70, 22, 2), (74, 24, 2), (77, 26, 2), (76, 28, 3), (73, 31, 1)]
    for n, bt, d in hn_mel:
        m.add(4 * bar + bt * beat, lib.hn.note(n, d * beat + 0.1, att=0.04, rel=0.4), 0.45, pan=0.05)
        m.add(4 * bar + bt * beat, lib.hn.note(n - 12, d * beat + 0.1, att=0.04, rel=0.4), 0.25, pan=-0.05)
    vl_mel = [(74, 0, 1), (81, 1, 1), (86, 2, 2), (84, 4, 1.5), (82, 5.5, 0.5), (81, 6, 2), (81, 8, 1), (84, 9, 1),
              (89, 10, 2), (88, 12, 2), (79, 14, 2), (79, 16, 1), (82, 17, 1), (86, 18, 2), (89, 20, 2), (86, 22, 2),
              (88, 24, 3), (85, 27, 1), (81, 28, 4)]
    for n, bt, d in vl_mel:
        st = 16 * bar + bt * beat
        place(lead, st, band.egtr.note(n - 12, d * beat + 0.2, att=0.003, rel=0.25), 1.0)
        m.add(st, lib.vln.note(n, d * beat + 0.15, att=0.05, rel=0.5), 0.15, pan=-0.2)
    for n, bt in [(81, 0), (86, 2), (77, 4), (81, 6), (82, 8), (86, 10), (85, 12)]:
        m.add(bt * beat, lib.piano.note(n, 2 * beat, att=0.002, rel=1.2), 0.25, pan=0.3)
    m.add(16 * bar - 2 * beat, oq.riser(lib, 2 * beat), 0.25)
    # amplificadores
    m.add(0, dip(mm.amp(highpass(gl, 110), drive=7.5, cut=5200), 400, -5), 0.16, pan=-0.8)
    m.add(0, dip(mm.amp(highpass(gr, 110), drive=7.5, cut=5200), 400, -5), 0.16, pan=0.8)
    m.add(0, mm.amp(lead, drive=10.0, cut=5600), 0.27, pan=0.1)
    m.add(0, np.tanh(lowpass(bass, 1800) * 2.0) * 0.6, 0.8)
    m.add(0, drums, 1.15)
    return m.finish(wet=0.24, rt60=1.8, bass=0.15, room=True, sat=0.95)


# ------------------------------------------------------------------ Neutron: folk-pop alegre y nostálgico (todo grabado)
def neutron(lib, band):
    """Folk-pop alegre con piano vertical real al frente: mano izquierda saltarina y melodía en la derecha."""
    k = band.kit
    bpm = 104
    beat = 60.0 / bpm
    bar = 4 * beat
    sw = 0.63
    swt = lambda bt: bt + (sw - 0.5) if abs(bt % 1 - 0.5) < 1e-6 else bt
    hum = lambda: RNG.uniform(-0.007, 0.007)
    m = Mix(32 * bar, tail=3.0)
    G, D, Em, C, Bm, Am, E, A = 43, 38, 40, 36, 35, 45, 40, 45
    A_ = [(G, MAJ), (D, MAJ), (Em, MIN), (C, MAJ), (G, MAJ), (E, MAJ), (A, MAJ), (D, MAJ)]
    B_ = [(C, MAJ), (D, MAJ), (Bm, MIN), (Em, MIN), (Am, MIN), (D, MAJ), (G, MAJ), (D, MAJ)]
    prog = A_ + A_ + B_ + A_
    pn = lib.piano
    drums = np.zeros(len(m.L))

    def pno(t, n, d, g, pan=0.15):
        m.add(t + hum(), pn.note(n, d, att=0.002, rel=0.9), g * RNG.uniform(0.88, 1.08), pan=pan)

    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        sec = b // 8
        lo = root - 12 if root >= 41 else root  # bajo del piano (octava 2)
        chord = [root + 12, root + 12 + q, root + 19]  # acorde cerrado en el centro del teclado
        chord = [n if n < 64 else n - 12 for n in chord]
        # mano izquierda: bajo en 1 y 3, acorde en 2 y 4 (con un empujón en el "y" del 4)
        pno(t0, lo, beat * 0.9, 0.34, pan=-0.2)
        pno(t0 + 2 * beat, lo + 7 if lo + 7 < 50 else lo - 5, beat * 0.9, 0.3, pan=-0.2)
        for bt in (1, 3):
            for n in chord:
                pno(t0 + bt * beat, n, beat * 0.45, 0.15, pan=0.0)
        if sec >= 1:
            for n in chord:
                pno(t0 + swt(3.5) * beat, n, beat * 0.3, 0.09, pan=0.0)
        # contrabajo pizzicato real desde la segunda sección
        if sec >= 1:
            nxt = prog[(b + 1) % len(prog)][0]
            rb = root - 12 if root >= 40 else root
            nb = nxt - 12 if nxt >= 40 else nxt
            for bt, n in ((0, rb), (2, rb + 7 if rb + 7 < 50 else rb - 5), (3, nb - 1 if nb > rb else nb + 1)):
                m.add(t0 + bt * beat + hum(), band.pizz.note(n, beat * 0.8, att=0.003, rel=0.2), 0.42, pan=-0.05)
        # escobillas suaves
        if sec >= 1:
            for i in range(8):
                place(drums, t0 + swt(i / 2) * beat + hum(), k.get("hhb", 0.35 + 0.25 * (i % 2 == 0)), 0.38)
            place(drums, t0 + beat, k.get("brush", 0.7), 0.6)
            place(drums, t0 + 3 * beat, k.get("brush", 0.8), 0.65)
            place(drums, t0, k.get("kick", 0.4), 0.5)
        if b in (16, 24):
            place(drums, t0, k.get("ride", 0.5), 0.35)
        # cuerdas cálidas en B y en el final
        if sec >= 2:
            tri = triad(root, q)
            for i, n in enumerate(tri):
                m.add(t0, lib.vla.note(n + 24 if n + 24 < 70 else n + 12, bar + 0.3, att=0.5, rel=0.9), 0.07, pan=0.35 - 0.15 * i)
            m.add(t0, lib.vc.note(root + 12 if root + 12 < 55 else root, bar + 0.3, att=0.5, rel=0.9), 0.1, pan=-0.3)
    melA = [(71, 0, 1), (74, 1, 1), (71, 2, .5), (69, 2.5, .5), (67, 3, 1),
            (69, 4, 1.5), (66, 5.5, .5), (69, 6, 1), (74, 7, 1),
            (67, 8, 1), (71, 9, 1), (76, 10, 1.5), (74, 11.5, .5),
            (72, 12, 1), (71, 13, 1), (69, 14, 1), (67, 15, 1),
            (71, 16, 1), (74, 17, 1), (79, 18, 1.5), (76, 19.5, .5),
            (76, 20, 1), (71, 21, 1), (68, 22, 2),
            (73, 24, 1), (76, 25, 1), (73, 26, 1), (71, 27, .5), (69, 27.5, .5),
            (66, 28, 3)]
    melB = [(76, 0, 1.5), (74, 1.5, .5), (72, 2, 1), (76, 3, 1),
            (78, 4, 1.5), (76, 5.5, .5), (74, 6, 2),
            (71, 8, 1), (74, 9, 1), (78, 10, 1.5), (76, 11.5, .5),
            (79, 12, 2), (76, 13, 1), (71, 15, 1),
            (72, 16, 1), (76, 17, 1), (81, 18, 1.5), (79, 19.5, .5),
            (78, 20, 1), (76, 21, 1), (74, 22, 1), (69, 23, 1),
            (71, 24, 1), (74, 25, 1), (79, 26, 2),
            (78, 28, 3)]
    for base, mel in ((0, melA), (8, melA), (16, melB), (24, melA)):
        for n, bt, d in mel:
            st = base * bar + swt(bt) * beat
            g = 0.36 if base != 0 else 0.33
            pno(st, n + 12, d * beat, g, pan=0.25)
            if base >= 8 and d >= 1:  # en las repeticiones la mano derecha dobla en tercera abajo
                pno(st, n + 12 - (3 if (n % 12) in (11, 4, 9) else 4), d * beat, g * 0.45, pan=0.25)
            if base == 16:
                m.add(st, lib.hn.note(n - 12, d * beat + 0.15, att=0.08, rel=0.5), 0.2, pan=-0.1)
            if base == 24:
                m.add(st, lib.vln.note(n, d * beat + 0.15, att=0.1, rel=0.5), 0.12, pan=-0.25)
    m.add(0, drums, 0.55)
    return m.finish(wet=0.3, rt60=1.6, bass=0.15, room=True, sat=0.9)


def epica_coro(lib, band):
    """Épica coral para el menú: himno grave y largo con coro masculino a cuatro voces (solo vocales)."""
    bpm = 54
    beat = 60.0 / bpm
    bar = 4 * beat
    m = Mix(16 * bar, tail=6.0)
    D, Bb, F, C, G, A = 38, 34, 41, 36, 31, 33
    prog = [(D, MIN), (Bb, MAJ), (F, MAJ), (C, MAJ), (D, MIN), (G, MIN), (Bb, MAJ), (A, MAJ)] * 2
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        tri = triad(root, q)
        m.add(t0, lib.cb.note(root, bar + 0.6, att=0.6, rel=1.5), 0.35)
        m.add(t0, lib.vc.note(root + 12, bar + 0.6, att=0.7, rel=1.5), 0.22, pan=-0.3)
        # coro a cuatro voces: bajo profundo, barítono, tenor y tenor alto (en la segunda vuelta)
        voices = [(root, 0.2, 0.0), (root + 7 if root + 7 < 46 else root + 7 - 12 + 12, 0.15, -0.3),
                  (root + 12 + q, 0.13, 0.3)] + ([(root + 19, 0.11, -0.15)] if b >= 8 else [])
        for n, g, pan in voices:
            v1 = mm.OH if b % 2 == 0 else mm.AH
            v2 = mm.AH if b % 2 == 0 else mm.OH
            sig = mm.voice(mm.midi(n), bar + 0.8, v1, a=1.4, r=1.8, ens=9, depth=0.0025, nh=32, tilt=1.25, vowel2=v2)
            m.add(t0, sig, g, pan=pan)
        if b >= 8:
            for i, nn in enumerate(tri):
                m.add(t0, lib.vla.note(nn + 24 if nn + 24 < 70 else nn + 12, bar + 0.6, att=0.8, rel=1.5), 0.07, pan=0.4 - 0.2 * i)
        if b in (0, 8):
            m.add(t0, hit(lib.timp_lo, 6.0), 0.2, pan=-0.2)
        if b == 15:
            m.add(t0, hit(lib.timp_roll, bar), 0.15)
    # la melodía de la Épica cantada por tenores y barítonos en unísono, frases largas de himno
    mel = [(62, 0, 2), (69, 2, 2), (65, 4, 1), (67, 5, 1), (69, 6, 2), (72, 8, 3), (69, 11, 1),
           (67, 12, 2), (64, 14, 2), (74, 16, 2), (69, 18, 2), (67, 20, 2), (70, 22, 2),
           (74, 24, 2), (77, 26, 2), (76, 28, 3), (73, 31, 1)]
    for base in (0, 8):
        for n, bt, d in mel:
            st = base * bar + bt * beat
            for oc, g in ((-12, 0.2), (-24, 0.12)):
                sig = mm.voice(mm.midi(n + oc), d * beat + 0.4, mm.OH, a=0.35, r=0.8, ens=8, depth=0.002, nh=30, tilt=1.15)
                m.add(st, sig, g, pan=0.05)
    m.add(8 * bar, hit(lib.susp), 0.12, pan=0.3)
    return m.finish(wet=0.55, rt60=5.0, bass=0.2, room=True, sat=0.85)


def accordion(freq, dur, att=0.03, rel=0.08):
    """Acordeón: tres lengüetas por nota (una afinada y dos desafinadas, efecto "musette"), timbre de lengüeta
    con resonancias de caja y fuelle con leve vibración. Modelo de un instrumento acústico, sin sonido electrónico."""
    n = int((dur + rel) * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for cents in (0.0, 14.0, -12.0):
        f = freq * 2 ** (cents / 1200)
        ph = (t * f + RNG.uniform()) % 1.0
        # lengüeta: pulso asimétrico (rico en armónicos impares y pares)
        reed = np.where(ph < 0.32, 1.0, -0.47) + 0.3 * (2 * ph - 1)
        out += reed
    out /= 3
    out = lowpass(out, 3800)
    X = np.fft.rfft(out)
    fr = np.fft.rfftfreq(n, 1 / SR)
    X *= 1 + 1.2 * np.exp(-((fr - 1200) / 500) ** 2) + 0.6 * np.exp(-((fr - 2600) / 700) ** 2)
    out = np.fft.irfft(X, n)
    env = np.ones(n)
    na = max(1, int(att * SR)); env[:na] = np.linspace(0, 1, na)
    nd = int(dur * SR); env[nd:] = np.linspace(1, 0, n - nd)
    bellows = 1 + 0.06 * np.sin(2 * np.pi * 5.5 * t)
    return out * env * bellows / (np.max(np.abs(out)) + 1e-9) * 0.6


def regio(lib, band):
    """Cumbia regia (estilo Monterrey): acordeón al frente, güiro/guacharaca en corcheas, bajo sincopado de cumbia,
    caja de aro en el contratiempo y metales en los clímax. Tono menor con saltos emotivos. Melodía original."""
    k = band.kit
    bpm = 94
    beat = 60.0 / bpm
    bar = 4 * beat
    m = Mix(32 * bar, tail=3.0)
    n = len(m.L)
    Am, Dm, G, C, E, F = 45, 38, 43, 36, 40, 41
    A_ = [(Am, MIN), (Am, MIN), (Dm, MIN), (G, MAJ), (C, MAJ), (F, MAJ), (E, MAJ), (E, MAJ)]
    B_ = [(F, MAJ), (G, MAJ), (C, MAJ), (Am, MIN), (Dm, MIN), (E, MAJ), (Am, MIN), (E, MAJ)]
    prog = A_ + A_ + B_ + A_
    gtr = np.zeros(n); perc = np.zeros(n)
    scrape_long = mm.shaker(0.16) * 0.8
    scrape = mm.shaker(0.07)
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        sec = b // 8
        nb = root - 12 if root > 40 else root
        # bajo de cumbia: 1, "y" del 2, 3 y "y" del 4 (tónica-quinta)
        for bt, nn, g in ((0, nb, 0.6), (1.5, nb + 7 if nb + 7 < 45 else nb - 5, 0.4), (2, nb, 0.55), (3.5, nb + 7 if nb + 7 < 45 else nb - 5, 0.42)):
            m.add(t0 + bt * beat, band.pizz.note(nn, beat * 0.55, att=0.003, rel=0.12), g)
            m.add(t0 + bt * beat, band.ebass.note(nn, beat * 0.5, att=0.003, rel=0.1), g * 0.5)
        # guacharaca: larga en el tiempo, cortas en las semicorcheas siguientes
        for x in range(8):
            place(perc, t0 + x * beat / 2, scrape_long if x % 2 == 0 else scrape, 0.35 if x % 2 == 0 else 0.25)
        # caja de aro en el contratiempo y bombo suave
        if sec >= 1 or b >= 4:
            for bt in (1, 3):
                m.add(t0 + bt * beat + beat / 2, k.get("side", 0.7), 0.32, pan=0.15)
            m.add(t0, k.get("kick", 0.55), 0.4)
            m.add(t0 + 2 * beat, k.get("kick", 0.5), 0.35)
        # guitarra: acordes rasgueados cortos en el "y" de cada tiempo
        tri = sorted(x if x < 64 else x - 12 for x in (root + 12, root + 12 + q, root + 19))
        for bt in (0.5, 1.5, 2.5, 3.5):
            for i, nn in enumerate(tri):
                place(gtr, t0 + bt * beat + i * 0.007, oq._acoustic(nn, beat * 0.35), 0.5)
        # cuerdas de fondo en el estribillo para emoción
        if sec >= 2:
            for i, nn in enumerate(triad(root, q)):
                m.add(t0, lib.vla.note(nn + 24 if nn + 24 < 72 else nn + 12, bar + 0.3, att=0.4, rel=0.8), 0.08, pan=0.35 - 0.15 * i)
        if b in (8, 16, 24):
            m.add(t0, k.get("crash", 0.6), 0.3, pan=0.3)
    # melodía en menor con saltos de sexta y octava al inicio de frase (lo emotivo)
    mel = [(64, 0, .5), (69, .5, 1), (72, 1.5, .5), (71, 2, .5), (69, 2.5, 1.5),
           (69, 4, .5), (76, 4.5, 1), (74, 5.5, .5), (72, 6, .5), (71, 6.5, 1.5),
           (62, 8, .5), (69, 8.5, 1), (72, 9.5, .5), (74, 10, 1), (71, 11, 1),
           (67, 12, .5), (74, 12.5, 1), (72, 13.5, .5), (71, 14, 1), (67, 15, 1),
           (64, 16, .5), (72, 16.5, 1), (76, 17.5, .5), (79, 18, 1.5), (76, 19.5, .5),
           (77, 20, .5), (76, 20.5, .5), (74, 21, .5), (72, 21.5, .5), (69, 22, 2),
           (68, 24, 1), (71, 25, .5), (74, 25.5, .5), (76, 26, 2),
           (80, 28, 1), (76, 29, .5), (74, 29.5, .5), (71, 30, 1), (68, 31, 1)]
    melB = [(72, 0, 1.5), (77, 1.5, .5), (81, 2, 2),
            (79, 4, 1), (74, 5, .5), (71, 5.5, .5), (74, 6, 2),
            (76, 8, 1.5), (79, 9.5, .5), (84, 10, 2),
            (81, 12, 1), (76, 13, .5), (72, 13.5, .5), (69, 14, 2),
            (74, 16, .5), (77, 16.5, .5), (81, 17, 1), (77, 18, 1), (74, 19, 1),
            (71, 20, .5), (76, 20.5, 1), (80, 21.5, .5), (83, 22, 2),
            (81, 24, 1), (76, 25, .5), (72, 25.5, .5), (69, 26, 2),
            (68, 28, 1), (71, 29, 1), (76, 30, 2)]
    acc = np.zeros(n)
    for base, ml in ((0, mel), (8, mel), (16, melB), (24, mel)):
        for nn, bt, d in ml:
            st = base * bar + bt * beat
            place(acc, st, accordion(mm.midi(nn), d * beat * 0.93, att=0.02), 1.0)
            if base >= 8:
                place(acc, st, accordion(mm.midi(nn - (3 if nn % 12 in (0, 5, 7) else 4)), d * beat * 0.93, att=0.02), 0.5)
    # metales: trompetas y trombón cantan el estribillo y rematan las frases
    for nn, bt, d in melB:
        st = 16 * bar + bt * beat
        m.add(st, lib.tpt.note(nn, d * beat * 0.9, att=0.02, rel=0.25), 0.2, pan=0.25)
        m.add(st, lib.tbn.note(nn - 12, d * beat * 0.9, att=0.03, rel=0.25), 0.22, pan=-0.25)
    for base in (8, 24):
        st = (base + 7) * bar + 2 * beat
        for i, nn in enumerate((68, 71, 76)):
            m.add(st + i * beat / 2, lib.tpt.note(nn, beat * 0.45, att=0.01, rel=0.2), 0.26, pan=0.25)
            m.add(st + i * beat / 2, lib.tbn.note(nn - 12, beat * 0.45, att=0.01, rel=0.2), 0.24, pan=-0.25)
    m.add(0, acc, 0.44, pan=0.08)
    m.add(0, oq._body_eq(gtr), 0.26, pan=-0.4)
    m.add(0, highpass(perc, 2500), 0.5, pan=0.35)
    return m.finish(wet=0.2, rt60=1.5, bass=0.22, room=True, sat=0.95)


def mega_boom(lib, band):
    """Explosión masiva del planeta: golpe grave, timbales, bombos, gong, platillos y retumbo largo que se aleja."""
    k = band.kit
    n = int(5.0 * SR)
    x = np.zeros(n)
    t = np.arange(n) / SR
    # retumbo: ruido grave filtrado con caída lenta
    rum = lowpass(RNG.normal(0, 1, n), 140) * np.exp(-t / 1.6) * 3.0
    sub = np.sin(2 * np.pi * np.cumsum(30 + 50 * np.exp(-t / 0.25)) / SR) * np.exp(-t / 1.1) * 0.9
    x += rum + sub
    for sig, g, d in ((k.get("kick", 1.0), 1.2, 0), (k.get("kick", 1.0), 0.9, 0.03), (lib.timp_lo, 1.0, 0.0), (lib.timp_roll, 0.5, 0.15),
                      (lib.crash, 0.6, 0.02), (k.get("crash", 1.0), 0.5, 0.05), (lib.gong, 0.7, 0.0), (k.get("t3", 1.0), 0.8, 0.06)):
        st = int(d * SR); seg = sig[: n - st]
        x[st:st + len(seg)] += seg * g
    # crepitar de escombros
    for _ in range(40):
        st = int(RNG.uniform(0.1, 3.0) * SR)
        cr = mm.shaker(0.05) * RNG.uniform(0.1, 0.35) * np.exp(-(st / SR) / 1.5)
        x[st:st + len(cr)] += cr[: n - st]
    x *= np.minimum(1, (n - np.arange(n)) / (0.4 * SR))
    return np.tanh(x / np.max(np.abs(x)) * 2.0) * 0.95


def rock_live(lib, band):
    """Tema "Rock" como banda en vivo: hard rock a 138 BPM con batería real, dos guitarras grabadas, bajo y solo."""
    k = band.kit
    bpm = 138
    beat = 60.0 / bpm
    bar = 4 * beat
    m = Mix(32 * bar, tail=3.0)
    n = len(m.L)
    E, C, G, D, A, B = 40, 36, 43, 38, 45, 47
    verse = [(E, MIN), (C, MAJ), (G, MAJ), (D, MAJ)]
    bridge = [(A, MIN), (C, MAJ), (D, MAJ), (B, MAJ)]
    prog = verse * 4 + bridge * 2 + verse * 2
    gl = np.zeros(n); gr = np.zeros(n); bass = np.zeros(n); drums = np.zeros(n); lead = np.zeros(n)
    hum = lambda: RNG.uniform(-0.006, 0.006)
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        r = root if root >= 40 else root + 12
        pc = [r, r + 7, r + 12]
        intro = b < 2
        for x in range(8):
            mute = x in (1, 3, 5, 6) and b < 16
            d = beat * (0.18 if mute else 0.9)
            inst = band.egtr_m if mute else band.egtr
            if not intro or x == 0:
                chord_gtr(inst, gl, t0 + x * beat / 2 + hum(), pc, d, 0.9)
                chord_gtr(inst, gr, t0 + x * beat / 2 + 0.012 + hum(), pc, d, 0.85)
            if not intro:
                place(bass, t0 + x * beat / 2 + hum(), band.ebass.note(root - 12 if root > 40 else root, beat * 0.45, att=0.003, rel=0.06), 0.85)
        if not intro:
            for x in range(8):
                place(drums, t0 + x * beat / 2 + hum(), k.get("hh" if b < 16 else "ride", 0.5 + 0.35 * (x % 2 == 0)), 0.45)
            for bt in (0, 1.5, 2, 2.75):
                place(drums, t0 + bt * beat + hum(), k.get("kick", 0.95), 1.0)
            place(drums, t0 + beat + hum(), k.get("snare", 0.95), 0.9)
            place(drums, t0 + 3 * beat + hum(), k.get("snare", 1.0), 0.95)
            if b % 4 == 3:
                for x, tom in enumerate(["t1", "t1", "t2", "t2", "t3", "t3", "t3", "snare"]):
                    place(drums, t0 + 2 * beat + x * beat / 4, k.get(tom, 0.85, 0.9), 0.8)
            if b % 4 == 0:
                place(drums, t0, k.get("crash", 1.0), 0.75)
        else:
            place(drums, t0, k.get("crash", 0.9), 0.7)
    phrase = [(64, 0, 1), (67, 1, 1), (71, 2, 2), (69, 4, 1), (67, 5, 1), (66, 6, 2), (64, 8, 1), (67, 9, 1), (71, 10, 1),
              (74, 11, 1), (76, 12, 3), (74, 15, 1)]
    for base in (16, 20, 24, 28):
        for nn, bt, d in phrase:
            place(lead, base * bar + bt * beat, band.egtr.note(nn, d * beat + 0.15, att=0.003, rel=0.25), 1.0)
    m.add(0, dip(mm.amp(highpass(gl, 110), drive=8.0, cut=5200), 400, -5), 0.17, pan=-0.85)
    m.add(0, dip(mm.amp(highpass(gr, 110), drive=8.0, cut=5200), 400, -5), 0.17, pan=0.85)
    m.add(0, mm.amp(lead, drive=10.0, cut=5600), 0.24, pan=0.1)
    m.add(0, np.tanh(lowpass(bass, 1800) * 2.0) * 0.6, 0.8)
    m.add(0, drums, 1.15)
    return m.finish(wet=0.3, rt60=2.2, bass=0.15, room=True, sat=0.95)


def brighten(st, amt=0.7):
    """Presencia y aire (realce sobre 3 kHz) tras la mezcla."""
    out = st.copy()
    for c in range(2):
        out[:, c] += highpass(st[:, c], 3000) * amt
    out = out * (0.89 / np.max(np.abs(out)))
    # nivel parejo con los otros temas: limitador suave hasta ~0.15 RMS
    g = 0.15 / max(1e-6, np.sqrt(np.mean(out ** 2)))
    if g > 1:
        out = np.tanh(out * g * 1.1) / np.tanh(1.1)
        out *= 0.89 / np.max(np.abs(out))
    return out


def saw(freq, dur, voices=3, det=12, fc=2600.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for v in range(voices):
        f = freq * 2 ** ((v - (voices - 1) / 2) * det / 1200 / max(1, voices - 1) * 2)
        ph = (t * f + RNG.uniform()) % 1.0
        out += 2 * ph - 1
    out /= voices
    return lowpass(out, fc) if dur > 0.02 else out


def duro(lib, band):
    """French house con empuje punk: bucle de acordes filtrado, guitarras de quinta en corcheas y batería real acelerada."""
    k = band.kit
    bpm = 128
    beat = 60.0 / bpm
    bar = 4 * beat
    m = Mix(32 * bar, tail=2.0)
    n = len(m.L)
    chords = [[50, 53, 57, 60, 64], [43, 53, 57, 59, 64], [48, 52, 55, 59, 62], [45, 52, 55, 60, 64]]  # Dm9 G13 Cmaj9 Am7
    roots = [38, 31, 36, 33]
    loop = np.zeros(int(2 * bar * SR) + SR)
    for ci, ch in enumerate(chords):
        tb = ci * beat * 2
        for st in (0.0, 0.75, 1.5):
            for nn in ch[1:]:
                place(loop, tb + st * beat, lib.hn_st.note(nn, 0.28, att=0.003, rel=0.12), 0.35)
                place(loop, tb + st * beat, lib.vln_sp.note(nn + 12, 0.25, att=0.003, rel=0.1), 0.25)
    loop = np.tanh(lowpass(loop[: int(2 * bar * SR)], 6500) * 1.8) * 0.5
    bed = np.tile(loop, int(np.ceil(n / len(loop))))[:n]
    t = np.arange(n) / SR
    bt_ = t / bar
    openv = np.interp(bt_, [0, 8, 8.01, 16, 18, 20, 24, 24.01, 32], [0.0, 0.95, 1, 1, 0.15, 0.1, 0.95, 1, 1])
    bed = lowpass(bed, 400) * (1 - openv) + bed * openv
    ph = (t / beat) % 1.0
    pump = 0.4 + 0.6 * np.clip(ph / 0.4, 0, 1) ** 0.7
    m.add(0, bed * pump, 0.7)
    drums = np.zeros(n); synth = np.zeros(n); gl = np.zeros(n); gr = np.zeros(n)
    clap = mm.shaker(0.16)
    for b in range(32):
        t0 = b * bar
        brk = 16 <= b < 20
        build = 20 <= b < 24
        drop = 8 <= b < 16 or b >= 24
        ri = (b % 2) * 2
        if not brk:
            for x in range(4):
                place(drums, t0 + x * beat, k.get("kick", 1.0), 0.95)
        if drop:
            # punk: caja en 2 y 4 con fantasmas, platillo abierto en cada corchea
            for x in (1, 3):
                place(drums, t0 + x * beat, k.get("snare", 1.0), 0.75)
                place(drums, t0 + x * beat, clap, 0.25)
            for x in range(8):
                place(drums, t0 + x * beat / 2, k.get("ho" if x % 2 else "hh", 0.55 + 0.3 * (x % 2)), 0.3)
            place(drums, t0 + 1.75 * beat, k.get("snare", 0.3), 0.3)
            if b % 4 == 3:
                for x, tom in enumerate(["t1", "t1", "t2", "t2", "t3", "t3", "snare", "snare"]):
                    place(drums, t0 + 2 * beat + x * beat / 4, k.get(tom, 0.85, 0.8), 0.75)
            # guitarras de quinta en corcheas, dobladas
            for half in range(2):
                r = roots[ri + half] + 12
                r = r if r >= 40 else r + 12
                for x in range(4):
                    st = t0 + half * 2 * beat + x * beat / 2
                    chord_gtr(band.egtr_m if x % 2 else band.egtr, gl, st, [r, r + 7, r + 12], beat * 0.4, 0.8)
                    chord_gtr(band.egtr_m if x % 2 else band.egtr, gr, st + 0.012, [r, r + 7, r + 12], beat * 0.4, 0.8)
        elif not brk:
            for x in range(4):
                place(drums, t0 + x * beat + beat / 2, k.get("ho", 0.5), 0.25)
        if build:
            sub = 8 if b < 22 else 16
            for x in range(sub):
                place(drums, t0 + x * bar / sub, k.get("snare", 0.35 + 0.65 * ((b - 20) * sub + x) / (4 * sub)), 0.5)
        if b in (8, 12, 24, 28):
            place(drums, t0, k.get("crash", 1.0), 0.7)
        # bajo sintetizado en corcheas constantes (octava en el contratiempo)
        if b >= 4 and not brk:
            for half in range(2):
                rr = roots[ri + half]
                for x in range(4):
                    f = mm.midi(rr - 12 + (12 if x % 2 else 0))
                    ln = beat * 0.42
                    sig = saw(f, ln, voices=2, det=6, fc=1100) * np.exp(-np.arange(int(ln * SR)) / SR / 0.15)
                    place(synth, t0 + half * 2 * beat + x * beat / 2, sig, 0.5)
    mel = [(69, 0, 1.5), (72, 1.5, 0.5), (74, 2, 1), (72, 3, 0.5), (69, 3.5, 0.5),
           (67, 4, 1.5), (69, 5.5, 0.5), (71, 6, 1), (67, 7, 1),
           (72, 8, 1.5), (74, 9.5, 0.5), (76, 10, 1), (74, 11, 0.5), (72, 11.5, 0.5),
           (69, 12, 3), (64, 15, 1)]
    lead = np.zeros(n)
    for base in (8, 12, 16, 24, 28):
        for nn, bt, d in mel:
            vow = mm.AH if (bt % 4) < 2 else mm.OH
            sig = mm.voice(mm.midi(nn), d * beat + 0.05, vow, a=0.01, r=0.08, ens=2, depth=0.0, nh=40, tilt=0.6)
            place(lead, base * bar + bt * beat, np.tanh(sig * 2.0), 0.5)
    m.add(0, lead, 0.36, pan=0.1)
    m.add(0, np.tanh(synth * 1.6), 0.38)
    m.add(0, mm.amp(highpass(gl, 120), drive=8.0, cut=5000), 0.13, pan=-0.75)
    m.add(0, mm.amp(highpass(gr, 120), drive=8.0, cut=5000), 0.13, pan=0.75)
    m.add(0, drums, 0.9)
    return m.finish(wet=0.14, rt60=1.1, bass=0.25, room=True, sat=1.0)


if __name__ == "__main__":
    lib = oq.Lib(sys.argv[1])
    band = Band(sys.argv[2])
    out = sys.argv[3]
    what = sys.argv[4] if len(sys.argv) > 4 else "all"
    if what in ("all", "game"):
        mm.save(brighten(game_rock(lib, band), 0.9), out, "game_theme")
    if what in ("all", "neutron"):
        mm.save(brighten(neutron(lib, band), 0.8), out, "theme_neutron")
    if what in ("all", "duro"):
        mm.save(brighten(duro(lib, band), 0.8), out, "theme_duro")
    if what in ("all", "rock"):
        mm.save(brighten(rock_live(lib, band), 0.9), out, "theme_rock")
    if what in ("all", "megaboom"):
        mm.save_wav_mono(mega_boom(lib, band), out, "intro_boom")
    if what in ("all", "regio"):
        mm.save(brighten(regio(lib, band), 0.7), out, "theme_regio")
    if what in ("all", "coro"):
        mm.save(brighten(epica_coro(lib, band), 0.4), out, "theme_coro")
    print("ok")
