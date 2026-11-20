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
    m.add(0, mm.amp(highpass(gl, 110), drive=7.5, cut=5200), 0.16, pan=-0.8)
    m.add(0, mm.amp(highpass(gr, 110), drive=7.5, cut=5200), 0.16, pan=0.8)
    m.add(0, mm.amp(lead, drive=10.0, cut=5600), 0.27, pan=0.1)
    m.add(0, np.tanh(lowpass(bass, 1800) * 2.0) * 0.6, 0.8)
    m.add(0, drums, 1.15)
    return m.finish(wet=0.22, rt60=1.8, bass=0.15)


# ------------------------------------------------------------------ Neutron: folk-pop alegre y nostálgico (todo grabado)
def neutron(lib, band):
    k = band.kit
    bpm = 104
    beat = 60.0 / bpm
    bar = 4 * beat
    sw = 0.64  # corcheas con swing
    eighth = lambda i: (i // 2) * beat + (i % 2) * sw * beat
    m = Mix(32 * bar, tail=3.0)
    G, D, Em, C, Bm, Am, E, A = 43, 38, 40, 36, 35, 45, 40, 45
    A_ = [(G, MAJ), (D, MAJ), (Em, MIN), (C, MAJ), (G, MAJ), (E, MAJ), (A, MAJ), (D, MAJ)]
    B_ = [(C, MAJ), (D, MAJ), (Bm, MIN), (Em, MIN), (Am, MIN), (D, MAJ), (G, MAJ), (D, MAJ)]
    prog = A_ + A_ + B_ + A_
    voic = {(G, MAJ): [43, 47, 50, 55, 59, 67], (D, MAJ): [50, 57, 62, 66], (Em, MIN): [40, 47, 52, 55, 59, 64],
            (C, MAJ): [48, 52, 55, 60, 64], (Bm, MIN): [47, 54, 59, 62, 66], (Am, MIN): [45, 52, 57, 60, 64],
            (E, MAJ): [40, 47, 52, 56, 59, 64], (A, MAJ): [45, 52, 57, 61, 64]}
    gl = np.zeros(len(m.L)); gr = np.zeros(len(m.L)); drums = np.zeros(len(m.L))
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        sec = b // 8
        notes = voic[(root, q)]
        # rasgueo con swing: abajo en tiempos, arriba en contratiempos
        for i in range(8):
            if sec == 0 and i % 2 == 1 and i != 3:
                continue
            up = i % 2 == 1
            ns = notes[-4:] if up else notes
            g = (0.5 if up else 0.8) * (0.75 if sec == 0 else 1.0)
            chord_gtr(band.gretsch_p if up else band.gretsch, gl, t0 + eighth(i), ns, beat * 0.9, g, strum=0.011, up=up)
            chord_gtr(band.gretsch_p if up else band.gretsch, gr, t0 + eighth(i) + 0.014, ns, beat * 0.9, g * 0.9, strum=0.012, up=up)
        # contrabajo pizzicato: tónica y quinta con paso cromático
        nxt = prog[(b + 1) % len(prog)][0]
        rb = root - 12 if root >= 40 else root
        nb = nxt - 12 if nxt >= 40 else nxt
        for bt, n in ((0, rb), (1, rb + 7), (2, rb + 12 if rb + 12 < 52 else rb), (3, nb - 1 if nb > rb else nb + 1)):
            m.add(t0 + bt * beat, band.pizz.note(n, beat * 0.8, att=0.003, rel=0.2), 0.5)
        # piano saltarín (estilo honky-tonk suave)
        tri = triad(root, q)
        for bt in (1, 3):
            for n in tri:
                m.add(t0 + bt * beat, lib.piano.note(n + 24, beat * 0.5, att=0.002, rel=0.3), 0.09, pan=0.25)
        # batería con escobillas
        if sec >= 1:
            for i in range(8):
                place(drums, t0 + eighth(i), k.get("hhb", 0.45 + 0.3 * (i % 2 == 0)), 0.5)
            place(drums, t0, k.get("kick", 0.6), 0.75)
            place(drums, t0 + 2 * beat, k.get("kick", 0.5), 0.6)
            place(drums, t0 + beat, k.get("brush", 0.8), 0.8)
            place(drums, t0 + 3 * beat, k.get("brush", 0.9), 0.85)
        else:
            place(drums, t0 + beat, k.get("side", 0.5), 0.5)
            place(drums, t0 + 3 * beat, k.get("side", 0.5), 0.5)
        if b in (8, 16, 24):
            place(drums, t0, k.get("crash", 0.5), 0.45)
        if sec >= 1:
            for i, n in enumerate(tri):
                m.add(t0, lib.vla.note(n + 24 if n + 24 < 70 else n + 12, bar + 0.3, att=0.4, rel=0.8), 0.08, pan=0.35 - 0.15 * i)
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
    sw_t = lambda bt: bt + (sw - 0.5) if bt % 1 == 0.5 else bt
    lead = np.zeros(len(m.L))
    for n, bt, d in melA:  # guitarra limpia canta la melodía
        place(lead, sw_t(bt) * beat, band.gretsch.note(n, d * beat + 0.1, att=0.002, rel=0.3), 1.0)
    for n, bt, d in melA:  # piano y violines
        st = 8 * bar + sw_t(bt) * beat
        m.add(st, lib.piano.note(n + 12, d * beat, att=0.002, rel=0.9), 0.24, pan=0.05)
        m.add(st, lib.vln.note(n, d * beat + 0.15, att=0.08, rel=0.5), 0.12, pan=-0.2)
    for n, bt, d in melB:
        st = 16 * bar + sw_t(bt) * beat
        m.add(st, lib.hn.note(n - 12, d * beat + 0.15, att=0.08, rel=0.5), 0.3, pan=0.05)
        m.add(st, lib.vln.note(n, d * beat + 0.15, att=0.1, rel=0.5), 0.14, pan=-0.2)
    for n, bt, d in melA:
        st = 24 * bar + sw_t(bt) * beat
        place(lead, st, band.gretsch.note(n, d * beat + 0.1, att=0.002, rel=0.3), 0.9)
        m.add(st, lib.vln.note(n + 12, d * beat + 0.15, att=0.08, rel=0.5), 0.16, pan=-0.15)
    m.add(0, mm.amp(gl, drive=1.4, cut=6500), 0.26, pan=-0.6)
    m.add(0, mm.amp(gr, drive=1.4, cut=6500), 0.26, pan=0.6)
    m.add(0, mm.amp(lead, drive=1.8, cut=6000), 0.32, pan=0.1)
    m.add(0, drums, 0.6)
    return m.finish(wet=0.28, rt60=1.8, bass=0.2)


# ------------------------------------------------------------------ Duro: french house (aquí sí hay sintetizador)
def brighten(st, amt=0.7):
    """Presencia y aire (realce sobre 3 kHz) tras la mezcla."""
    out = st.copy()
    for c in range(2):
        out[:, c] += highpass(st[:, c], 3000) * amt
    return out * (0.89 / np.max(np.abs(out)))


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
    k = band.kit
    bpm = 123
    beat = 60.0 / bpm
    bar = 4 * beat
    m = Mix(32 * bar, tail=2.0)
    n = len(m.L)
    # bucle de acordes tipo muestra de disco: metales cortos + cuerdas + guitarra funk, en un compás
    chords = [[50, 53, 57, 60, 64], [43, 53, 57, 59, 64], [48, 52, 55, 59, 62], [45, 52, 55, 60, 64]]  # Dm9 G13 Cmaj9 Am7
    loop = np.zeros(int(2 * bar * SR) + SR)
    stabs = [0, 1.5, 2.5, 3.5]
    for ci, ch in enumerate(chords):
        tb = ci * beat * 2
        for j, st in enumerate((0.0, 0.75, 1.5)):
            for nn in ch[1:]:
                place(loop, tb + st * beat, lib.hn_st.note(nn, 0.28, att=0.003, rel=0.12), 0.35)
                place(loop, tb + st * beat, lib.vln_sp.note(nn + 12, 0.25, att=0.003, rel=0.1), 0.25)
            for nn in ch[2:]:
                place(loop, tb + st * beat + 0.005, band.gretsch_p.note(nn, 0.18, att=0.002, rel=0.05), 0.35)
    loop = loop[: int(2 * bar * SR)]
    # lo-fi: un poco de saturación y recorte de agudos como un sampler viejo
    loop = np.tanh(lowpass(loop, 6000) * 2.2) * 0.5
    reps = int(np.ceil(n / len(loop)))
    chord_bed = np.tile(loop, reps)[:n]
    dark = lowpass(chord_bed, 380)
    bright = chord_bed
    t = np.arange(n) / SR
    bars_t = t / bar
    # apertura del filtro por secciones: intro abre, drop abierto, breakdown cierra, subida abre
    openv = np.interp(bars_t, [0, 8, 8.01, 16, 18, 20, 24, 24.01, 32], [0.0, 0.95, 1, 1, 0.15, 0.1, 0.95, 1, 1])
    bed = dark * (1 - openv) + bright * openv
    # bombeo de compresión lateral por el bombo
    ph = (t / beat) % 1.0
    pump = 0.35 + 0.65 * np.clip(ph / 0.45, 0, 1) ** 0.7
    m.add(0, bed * pump, 0.95, pan=0.0)
    drums = np.zeros(n); synth = np.zeros(n)
    kick_sub = np.sin(2 * np.pi * np.cumsum(45 + 90 * np.exp(-np.arange(int(0.3 * SR)) / SR / 0.04)) / SR) * \
        np.exp(-np.arange(int(0.3 * SR)) / SR / 0.12)
    clap = mm.shaker(0.18) * 1.2
    bassline = [0, 0, 12, 0, 10, 0, 12, 7]
    roots = [38, 31, 36, 33]
    for b in range(32):
        t0 = b * bar
        breakdown = 16 <= b < 20
        build = 20 <= b < 24
        if not breakdown:
            for bt in range(4):
                place(drums, t0 + bt * beat, k.get("kick", 1.0), 0.8)
                place(drums, t0 + bt * beat, kick_sub, 0.28)
        if b >= 8 and not breakdown:
            for bt in (1, 3):
                place(drums, t0 + bt * beat, clap, 0.35)
                place(drums, t0 + bt * beat, k.get("rim", 0.7), 0.4)
            for bt in range(4):
                place(drums, t0 + bt * beat + beat / 2, k.get("ho", 0.6), 0.32)
                place(drums, t0 + bt * beat, k.get("hh", 0.4), 0.22)
        if build:
            sub = 4 if b < 22 else 8
            for kk in range(sub):
                place(drums, t0 + kk * bar / sub, k.get("snare", 0.4 + 0.6 * (b - 20) / 4), 0.45)
        if b in (8, 24):
            place(drums, t0, k.get("crash", 1.0), 0.6)
        # bajo sintetizado funk (octavas)
        if b >= 4 and not breakdown:
            for half in range(2):
                rr = roots[(b % 2) * 2 + half]
                for kk in range(8):
                    if kk % 4 == 3 and half == 1:
                        continue
                    st = t0 + half * 2 * beat + kk * beat / 4
                    if kk % 2 == 1 and kk not in (3, 7):
                        continue
                    f = mm.midi(rr - 12 + bassline[kk])
                    sig = saw(f, beat * 0.22, voices=2, det=6, fc=900) * np.exp(-np.arange(int(beat * 0.22 * SR)) / SR / 0.12)
                    place(synth, st, sig, 0.5)
    # lead sintetizado con formantes (sonido "robótico" cantado, sin letra)
    mel = [(69, 0, 1.5), (72, 1.5, 0.5), (74, 2, 1), (72, 3, 0.5), (69, 3.5, 0.5),
           (67, 4, 1.5), (69, 5.5, 0.5), (71, 6, 1), (67, 7, 1),
           (72, 8, 1.5), (74, 9.5, 0.5), (76, 10, 1), (74, 11, 0.5), (72, 11.5, 0.5),
           (69, 12, 3), (64, 15, 1)]
    lead = np.zeros(n)
    for base in (8, 12, 16, 24, 28):
        for nn, bt, d in mel:
            vow = mm.AH if (bt % 4) < 2 else mm.OH
            sig = mm.voice(mm.midi(nn), d * beat + 0.05, vow, a=0.01, r=0.08, ens=2, depth=0.0, nh=40, tilt=0.6)
            place(lead, base * bar + bt * beat, np.tanh(sig * 2.5), 0.55)
    m.add(0, lead, 0.42, pan=0.1)
    m.add(0, np.tanh(synth * 1.8), 0.4)
    m.add(0, drums, 0.85)
    return m.finish(wet=0.14, rt60=1.2, bass=0.3)


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
    print("ok")
