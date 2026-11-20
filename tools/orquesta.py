#!/usr/bin/env python3
"""Música del juego con instrumentos grabados (sin sintetizadores).

Usa la librería orquestal VSCO 2 Community Edition (licencia CC0, Versilian Studios):
https://github.com/sgossner/VSCO-2-CE . Las muestras no se incluyen en el repositorio; se descargan aparte
y se pasa su carpeta como primer argumento. La guitarra eléctrica es un modelo físico de cuerda
(Karplus-Strong + amplificador de válvulas) definido en make_music.py.

Uso: python3 tools/orquesta.py <carpeta_VSCO> <carpeta_salida> [game|menu|solo|boom|all]
"""
import glob
import importlib.util
import os
import re
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("mm", os.path.join(HERE, "make_music.py"))
mm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mm)
SR = mm.SR
Mix = mm.Mix
midi = mm.midi
RNG = np.random.default_rng(11)

NOTE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def load_wav(path):
    w = wave.open(path)
    n, sw, ch, sr = w.getnframes(), w.getsampwidth(), w.getnchannels(), w.getframerate()
    raw = w.readframes(n)
    if sw == 2:
        a = np.frombuffer(raw, "<i2").astype(np.float64) / 32768
    elif sw == 3:
        b = np.frombuffer(raw, np.uint8).reshape(-1, 3).astype(np.int32)
        a = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
        a = np.where(a >= 1 << 23, a - (1 << 24), a) / 8388608.0
    else:
        a = np.frombuffer(raw, "<i4").astype(np.float64) / 2147483648.0
    a = a.reshape(-1, ch).mean(axis=1)
    # al ritmo del motor
    if sr != SR:
        pos = np.arange(0, len(a) - 1, sr / SR)
        a = np.interp(pos, np.arange(len(a)), a)
    # quitar silencio inicial
    pk = np.max(np.abs(a)) + 1e-9
    on = int(np.argmax(np.abs(a) > pk * 0.02))
    a = a[max(0, on - int(0.004 * SR)):]
    # nivel parejo entre notas: RMS de la parte más fuerte
    win = int(0.25 * SR)
    if len(a) > win:
        e = np.convolve(a * a, np.ones(win) / win, mode="valid")
        rms = np.sqrt(np.max(e))
    else:
        rms = np.sqrt(np.mean(a * a))
    return a * (0.2 / max(rms, 1e-6))


def note_of(name, octave_shift=2):
    """VSCO nombra A3 a la nota MIDI 69 (octava desplazada)."""
    m = re.search(r"_([A-G])(#?)(-?\d)_", name)
    return 12 * (int(m.group(3)) + octave_shift) + NOTE[m.group(1)] + (1 if m.group(2) else 0)


class Inst:
    """Instrumento muestreado: elige la grabación más cercana y la afina por re-muestreo."""

    def __init__(self, items):
        self.items = sorted(items, key=lambda t: t[0])  # (midi, señal)
        self.loops = {}

    @staticmethod
    def from_glob(pattern, mapper=note_of):
        items = []
        for p in sorted(glob.glob(pattern)):
            try:
                items.append((mapper(os.path.basename(p)), load_wav(p)))
            except Exception:
                pass
        if not items:
            raise SystemExit("sin muestras: " + pattern)
        return Inst(items)

    def _long(self, idx, need):
        """Extiende una nota sostenida con bucles cruzados de su parte estable."""
        m, a = self.items[idx]
        if len(a) >= need:
            return a
        key = (idx, need // SR)
        if key in self.loops:
            return self.loops[key]
        s0, s1 = int(len(a) * 0.35), int(len(a) * 0.85)
        seg = a[s0:s1]
        xf = int(0.2 * SR)
        out = a[:s1].copy()
        while len(out) < need:
            fade = np.linspace(0, 1, xf)
            tail = out[-xf:] * np.sqrt(1 - fade) + seg[:xf] * np.sqrt(fade)
            out = np.concatenate([out[:-xf], tail, seg[xf:]])
        self.loops[key] = out
        return out

    def note(self, m, dur, att=0.01, rel=0.35, start=0.0):
        idx = int(np.argmin([abs(x[0] - m) for x in self.items]))
        sm, _ = self.items[idx]
        ratio = 2 ** ((m - sm) / 12.0)
        n = int((dur + rel) * SR)
        base = self._long(idx, int(n * ratio) + int(start * SR) + 4)
        pos = np.arange(n) * ratio + start * SR
        y = np.interp(pos, np.arange(len(base)), base, right=0.0)
        env = np.ones(n)
        na = max(1, int(att * SR))
        env[:na] = np.linspace(0, 1, na)
        nd = int(dur * SR)
        if nd < n:
            env[nd:] *= np.linspace(1, 0, n - nd) ** 1.5
        return y * env


class Lib:
    def __init__(self, root):
        R = lambda p: os.path.join(root, p)
        self.vln = Inst.from_glob(R("Strings/Violin Section/susVib/*_v2.wav"))
        self.vln_sp = Inst.from_glob(R("Strings/Violin Section/Spic/*_v2_rr1.wav"))
        self.vla = Inst.from_glob(R("Strings/Viola Section/susvib/*_v2_1.wav"))
        self.vc = Inst.from_glob(R("Strings/Cello Section/susvib/*_v3_1.wav"))
        self.vc_sp = Inst.from_glob(R("Strings/Cello Section/spic/*_v2_RR1.wav"))
        self.cb = Inst.from_glob(R("Strings/Solo Contrabass/SusVib/*_v3_rr1.wav"))
        self.cb_sp = Inst.from_glob(R("Strings/Solo Contrabass/Spic/*_v3_rr1.wav"))
        self.hn = Inst.from_glob(R("Brass/F Horn/sus/*_v3_1.wav"))
        self.hn_st = Inst.from_glob(R("Brass/F Horn/stac/*_v3_rr1.wav"))
        self.tbn = Inst.from_glob(R("Brass/Tenor Trombone/sus/*_v2_1.wav"))
        self.tuba = Inst.from_glob(R("Brass/Tuba/sus/*_v3_rr1_Mid.wav"))
        self.tpt = Inst.from_glob(R("Brass/Trumpet/sus/*_v3_rr1.wav"))
        chart = {}
        with open(R("Keys/Upright Piano/MappingChart.txt")) as fh:
            for line in fh:
                mt = re.match(r"(\d{3})=(\d+)", line.strip())
                if mt:
                    chart[mt.group(1)] = int(mt.group(2))
        self.piano = Inst.from_glob(R("Keys/Upright Piano/Player_dyn2_rr1_*.wav"),
                                    lambda n: chart[re.search(r"_(\d{3})\.wav", n).group(1)])
        one = lambda p: load_wav(R(p))
        self.timp_lo = one("Percussion/Timpani/Timpani4_Hit_v3_rr1_Sum.wav")
        self.timp_mid = one("Percussion/Timpani/Timpani2_Hit_v3_rr1_Sum.wav")
        self.timp_hi = one("Percussion/Timpani/Timpani3_Hit_v3_rr1_Sum.wav")
        self.timp_roll = one("Percussion/Timpani/Rolls/Timpani4_Roll_v5_rr1_Sum.wav")
        self.bd = one("VSCO 1 Percussion/drums/bass/bdrum_fff_1.wav")
        self.bd2 = one("VSCO 1 Percussion/drums/bass/bdrum_ff_1.wav")
        self.sn = one("VSCO 1 Percussion/drums/snare/drum1/snare1_ff_1.wav")
        self.sn2 = one("VSCO 1 Percussion/drums/snare/drum1/snare1_f_1.wav")
        self.sn_g = one("VSCO 1 Percussion/drums/snare/drum1/snare1_mp_1.wav")
        self.crash = one("VSCO 1 Percussion/varMetal/Cymbals/clash/crash_hit_fff_loose.wav")
        self.crash2 = one("VSCO 1 Percussion/varMetal/Cymbals/clash/crash_hit_ff_loose.wav")
        self.susp = one("VSCO 1 Percussion/varMetal/Cymbals/susp/susp_hit_softmall_ff.wav")
        g = sorted(glob.glob(R("VSCO 1 Percussion/varMetal/Gong/gong/*.wav")))
        self.gong = load_wav(g[-1]) if g else self.crash


def hit(sig, dur=None):
    if dur is None:
        return sig
    n = min(len(sig), int(dur * SR))
    out = sig[:n].copy()
    f = min(n, int(0.05 * SR))
    out[n - f:] *= np.linspace(1, 0, f)
    return out


def riser(lib, dur):
    """Platillo grabado al revés: subida natural antes de un impacto."""
    c = lib.crash[: int(dur * SR)][::-1].copy()
    return c * np.linspace(0.2, 1, len(c))


MIN, MAJ = 3, 4


def triad(root, q):
    return [root, root + q, root + 7]


# ------------------------------------------------------------------ tema de partida (épico, estilo Halo 2/3)
def game_theme(lib):
    bpm = 112
    beat = 60.0 / bpm
    bar = 4 * beat
    total = 32 * bar
    m = Mix(total, tail=4.0)
    D, Bb, F, C, G, A = 38, 34, 41, 36, 31, 33
    prog = [(D, MIN), (D, MIN), (Bb, MAJ), (A, MAJ),                                      # intro
            (D, MIN), (Bb, MAJ), (F, MAJ), (C, MAJ), (D, MIN), (G, MIN), (Bb, MAJ), (A, MAJ),  # A
            (Bb, MAJ), (C, MAJ), (D, MIN), (A, MAJ),                                      # subida
            (D, MIN), (Bb, MAJ), (F, MAJ), (C, MAJ), (G, MIN), (Bb, MAJ), (A, MAJ), (A, MAJ),  # B (clímax)
            (Bb, MAJ), (F, MAJ), (G, MIN), (A, MAJ),                                      # puente emotivo
            (D, MIN), (Bb, MAJ), (C, MAJ), (A, MAJ)]                                      # regreso
    sec = lambda b: "intro" if b < 4 else "A" if b < 12 else "up" if b < 16 else "B" if b < 24 else "bridge" if b < 28 else "ret"
    guit_l = np.zeros(len(m.L))
    guit_r = np.zeros(len(m.L))
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        s = sec(b)
        full = s in ("A", "B", "ret", "up")
        tri = triad(root, q)
        # graves: contrabajo y tuba (pedal), chelos
        m.add(t0, lib.cb.note(root, bar + 0.1, att=0.03, rel=0.5), 0.55, pan=-0.1)
        if s in ("B", "up", "ret"):
            m.add(t0, lib.tuba.note(root, bar + 0.1, att=0.04, rel=0.5), 0.35, pan=0.15)
        if s in ("intro", "bridge"):
            m.add(t0, lib.vc.note(root + 12, bar + 0.2, att=0.25, rel=0.8), 0.45, pan=-0.3)
        # ostinato de chelos y contrabajos en corcheas (spiccato grabado)
        if full:
            pat = [0, 0, 12, 0, 0, 0, 7, 5]
            for k in range(8):
                acc = 1.0 if k % 2 == 0 else 0.7
                m.add(t0 + k * beat / 2, lib.vc_sp.note(root + 12 + pat[k], beat / 2, att=0.003, rel=0.12), 0.55 * acc, pan=-0.35)
                m.add(t0 + k * beat / 2, lib.cb_sp.note(root + pat[k] % 12, beat / 2, att=0.003, rel=0.12), 0.40 * acc, pan=0.1)
        # colchón de cuerdas
        if s != "intro":
            for i, n in enumerate(tri):
                m.add(t0, lib.vla.note(n + 24 if n + 24 < 70 else n + 12, bar + 0.2, att=0.18, rel=0.6), 0.22, pan=0.3 - 0.1 * i)
            m.add(t0, lib.vln.note(tri[1] + 36, bar + 0.2, att=0.2, rel=0.6), 0.22, pan=-0.45)
            m.add(t0, lib.vln.note(tri[2] + 36, bar + 0.2, att=0.2, rel=0.6), 0.20, pan=0.45)
        else:
            # graves con saltos agudos: violines en lo alto sobre el pedal
            m.add(t0 + 2 * beat, lib.vln.note(tri[2] + 48, 2 * beat + 0.3, att=0.3, rel=0.8), 0.18, pan=0.5)
        # percusión
        if full:
            m.add(t0, hit(lib.bd), 0.85)
            m.add(t0 + 1.5 * beat, hit(lib.bd2), 0.55)
            m.add(t0 + 2 * beat, hit(lib.bd), 0.75)
            m.add(t0 + beat, hit(lib.sn), 0.55, pan=0.1)
            m.add(t0 + 3 * beat, hit(lib.sn), 0.6, pan=0.1)
            m.add(t0 + 3.75 * beat, hit(lib.sn_g), 0.3, pan=0.1)
            m.add(t0, hit(lib.timp_lo, 3.0), 0.55, pan=-0.2)
            m.add(t0 + 2.5 * beat, hit(lib.timp_mid, 2.0), 0.4, pan=-0.2)
            if s == "B":
                m.add(t0 + 3.5 * beat, hit(lib.timp_hi, 1.5), 0.35, pan=0.2)
        elif s == "intro" and b == 0:
            m.add(t0, hit(lib.timp_roll, 4 * bar), 0.35, pan=-0.2)
        elif s == "bridge" and b == 27:
            m.add(t0, hit(lib.timp_roll, bar), 0.4, pan=-0.2)
        # metales en la subida: golpes rítmicos
        if s == "up":
            for bt in (0, 1.5, 3):
                for n in (root + 12, root + 19, root + 24):
                    m.add(t0 + bt * beat, lib.hn_st.note(n, 0.5, att=0.003, rel=0.25), 0.33, pan=-0.2)
                m.add(t0 + bt * beat, lib.tbn.note(root + 12, 0.45, att=0.005, rel=0.2), 0.35, pan=0.2)
        # guitarras rock (modelo físico): A y regreso con palm mute; B con acordes abiertos
        if s in ("A", "ret", "up"):
            for k in range(8):
                mute = k not in (0, 4)
                for buf in (guit_l, guit_r):
                    mm.power_chord(buf, t0 + k * beat / 2 + RNG.uniform(0, 0.008), root + 12, beat * (0.45 if mute else 0.95), mute)
        elif s == "B":
            for k in range(8):
                mute = k % 2 == 1
                for buf in (guit_l, guit_r):
                    mm.power_chord(buf, t0 + k * beat / 2 + RNG.uniform(0, 0.008), root + 12, beat * (0.45 if mute else 1.8 if k == 0 else 0.9), mute)
    m.add(0, mm.amp(guit_l), 0.18, pan=-0.7)
    m.add(0, mm.amp(guit_r), 0.18, pan=0.7)

    # melodías
    hn_mel = [(62, 0, 2), (69, 2, 2), (65, 4, 1), (67, 5, 1), (69, 6, 1.5), (70, 7.5, 0.5), (72, 8, 3), (69, 11, 1),
              (67, 12, 2), (64, 14, 1), (67, 15, 1), (74, 16, 2), (69, 18, 1), (65, 19, 1), (67, 20, 1.5), (69, 21.5, 0.5),
              (70, 22, 2), (74, 24, 2), (77, 26, 2), (76, 28, 3), (73, 31, 1)]
    for n, bt, d in hn_mel:  # sección A (compases 5-12)
        m.add(4 * bar + bt * beat, lib.hn.note(n, d * beat + 0.1, att=0.04, rel=0.4), 0.55, pan=0.05)
        m.add(4 * bar + bt * beat, lib.hn.note(n - 12, d * beat + 0.1, att=0.04, rel=0.4), 0.3, pan=-0.05)
    vl_mel = [(74, 0, 1), (81, 1, 1), (86, 2, 2), (84, 4, 1.5), (82, 5.5, 0.5), (81, 6, 2), (81, 8, 1), (84, 9, 1),
              (89, 10, 2), (88, 12, 2), (79, 14, 2), (79, 16, 1), (82, 17, 1), (86, 18, 2), (89, 20, 2), (86, 22, 2),
              (88, 24, 3), (85, 27, 1), (81, 28, 4)]
    for n, bt, d in vl_mel:  # clímax B (compases 17-24): violines con saltos de octava, trompas una octava abajo
        st = 16 * bar + bt * beat
        m.add(st, lib.vln.note(n, d * beat + 0.15, att=0.05, rel=0.5), 0.40, pan=-0.2)
        m.add(st, lib.vln.note(n - 12, d * beat + 0.15, att=0.05, rel=0.5), 0.30, pan=0.2)
        m.add(st, lib.hn.note(n - 24, d * beat + 0.1, att=0.04, rel=0.4), 0.45, pan=0.0)
        m.add(st, lib.tbn.note(n - 24 - 3 if n % 12 in (2, 9) else n - 24 - 4, d * beat + 0.1, att=0.05, rel=0.4), 0.22, pan=0.25)
    for n, bt, d in [(62, 0, 2), (65, 2, 2), (60, 4, 4), (58, 8, 2), (62, 10, 2), (61, 12, 2), (64, 14, 2)]:
        m.add(24 * bar + bt * beat, lib.vc.note(n - 12, d * beat + 0.3, att=0.12, rel=0.6), 0.55, pan=-0.15)
    # piano emotivo: notas altas en la intro y arpegios en el puente
    for n, bt in [(81, 0), (86, 2), (77, 4), (81, 6), (82, 8), (86, 10), (85, 12)]:
        m.add(bt * beat, lib.piano.note(n, 2 * beat, att=0.002, rel=1.2), 0.30, pan=0.3)
    for b in range(24, 28):
        root, q = prog[b]
        arp = [root + 24, root + 24 + q, root + 31, root + 36, root + 36 + q, root + 31, root + 36, root + 24 + q]
        for k, n in enumerate(arp):
            m.add(b * bar + k * beat / 2, lib.piano.note(n, beat, att=0.002, rel=0.8), 0.25, pan=0.25)
    # impactos y subidas
    m.add(16 * bar - 2 * beat, riser(lib, 2 * beat), 0.35)
    m.add(15 * bar, hit(lib.timp_roll, bar), 0.45, pan=-0.2)
    for b in (4, 16, 20, 28):
        m.add(b * bar, hit(lib.crash), 0.45, pan=0.3)
    m.add(16 * bar, hit(lib.gong), 0.35)
    m.add(16 * bar, lib.tuba.note(38 - 12, 3.0, att=0.01, rel=1.0), 0.45)
    m.add(16 * bar, lib.tbn.note(38, 3.0, att=0.01, rel=1.0), 0.35)
    m.add(32 * bar - 2 * beat, riser(lib, 2 * beat), 0.25)
    return m.finish(wet=0.32, rt60=2.6, bass=0.25)


# ------------------------------------------------------------------ tema "Tensión": oscuro y pulsante
def tension_theme(lib):
    bpm = 92
    beat = 60.0 / bpm
    bar = 4 * beat
    total = 24 * bar
    m = Mix(total, tail=4.0)
    C, Ab, Fm, G = 36, 32, 41, 31
    prog = [(C, MIN), (C, MIN), (Ab, MAJ), (Ab, MAJ), (Fm, MIN), (Fm, MIN), (G, MAJ), (G, MAJ)] * 3
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        lvl = 0.6 + 0.4 * (b // 8) / 2
        m.add(t0, lib.cb.note(root, bar + 0.1, att=0.2, rel=0.6), 0.55)
        for k in range(16):
            n = root + 12 + (0 if k % 4 != 3 else 7)
            m.add(t0 + k * beat / 4, lib.vc_sp.note(n, beat / 4, att=0.002, rel=0.08), 0.42 * lvl * (1.0 if k % 4 == 0 else 0.6), pan=-0.3)
        m.add(t0, lib.hn.note(root + 12, bar + 0.2, att=0.6, rel=0.8), 0.30 * lvl, pan=0.1)
        m.add(t0, lib.vln.note(root + 43, bar + 0.3, att=0.8, rel=1.0), 0.14, pan=0.45)
        m.add(t0, lib.vla.note(root + 24 + q, bar + 0.3, att=0.5, rel=0.8), 0.18, pan=0.25)
        for k in range(4):
            m.add(t0 + k * beat, hit(lib.timp_lo if k % 2 == 0 else lib.timp_mid, 1.5), 0.35 * lvl, pan=-0.2)
        if b >= 8:
            m.add(t0, hit(lib.bd), 0.6)
            for k in range(16):
                if k % 4 == 2:
                    m.add(t0 + k * beat / 4, hit(lib.sn_g), 0.22, pan=0.1)
        if b >= 16:
            m.add(t0 + 2 * beat, hit(lib.bd2), 0.5)
            m.add(t0 + beat, hit(lib.sn2), 0.35, pan=0.1)
            m.add(t0 + 3 * beat, hit(lib.sn), 0.4, pan=0.1)
            m.add(t0, lib.tbn.note(root + 12, bar * 0.5, att=0.05, rel=0.4), 0.25)
        if b % 8 == 7:
            m.add(t0 + 2 * beat, riser(lib, 2 * beat), 0.25)
            m.add(t0, hit(lib.timp_roll, bar), 0.3)
    for b in (8, 16):
        m.add(b * bar, hit(lib.crash), 0.4, pan=0.3)
        m.add(b * bar, hit(lib.gong), 0.25)
    return m.finish(wet=0.36, rt60=3.0, bass=0.25)


# ------------------------------------------------------------------ tema "Rock": guitarras al frente con batería
def rock_theme(lib):
    bpm = 132
    beat = 60.0 / bpm
    bar = 4 * beat
    total = 32 * bar
    m = Mix(total, tail=3.0)
    E, Cc, G, D, A, B = 40, 36, 43, 38, 45, 47
    verse = [(E, MIN), (Cc, MAJ), (G, MAJ), (D, MAJ)]
    bridge = [(A, MIN), (Cc, MAJ), (D, MAJ), (B, MAJ)]
    prog = verse * 4 + bridge * 2 + verse * 2
    gl = np.zeros(len(m.L))
    gr = np.zeros(len(m.L))
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        r = root - 12 if root > 43 else root
        for k in range(8):
            mute = k in (1, 3, 5, 6)
            for buf in (gl, gr):
                mm.power_chord(buf, t0 + k * beat / 2 + RNG.uniform(0, 0.007), r + 12, beat * (0.4 if mute else 0.9), mute)
            m.add(t0 + k * beat / 2, lib.cb_sp.note(r, beat / 2, att=0.003, rel=0.1), 0.45, pan=0.0)
        m.add(t0, hit(lib.bd), 0.85)
        m.add(t0 + 1.5 * beat, hit(lib.bd2), 0.55)
        m.add(t0 + 2 * beat, hit(lib.bd), 0.75)
        m.add(t0 + 3.5 * beat, hit(lib.bd2), 0.45)
        m.add(t0 + beat, hit(lib.sn), 0.6, pan=0.1)
        m.add(t0 + 3 * beat, hit(lib.sn), 0.65, pan=0.1)
        if b % 4 == 0:
            m.add(t0, hit(lib.crash), 0.4, pan=0.3)
        else:
            m.add(t0, hit(lib.susp), 0.12, pan=-0.3)
        m.add(t0, lib.vla.note(r + 24 + q, bar + 0.1, att=0.3, rel=0.5), 0.14, pan=0.3)
        m.add(t0, lib.vln.note(r + 31, bar + 0.1, att=0.3, rel=0.5), 0.12, pan=-0.3)
        if 16 <= b < 24:
            for bt in (0, 1.5, 3):
                m.add(t0 + bt * beat, lib.hn_st.note(r + 12, 0.4, att=0.003, rel=0.2), 0.3)
                m.add(t0 + bt * beat, lib.tbn.note(r + 12, 0.35, att=0.004, rel=0.2), 0.25)
    m.add(0, mm.amp(gl, drive=8.0), 0.22, pan=-0.75)
    m.add(0, mm.amp(gr, drive=8.0), 0.22, pan=0.75)
    # guitarra solista en la segunda mitad
    lead = np.zeros(len(m.L))
    phrase = [(64, 0, 1), (67, 1, 1), (71, 2, 2), (69, 4, 1), (67, 5, 1), (66, 6, 2), (64, 8, 1), (67, 9, 1), (71, 10, 1),
              (74, 11, 1), (76, 12, 3), (74, 15, 1)]
    for base in (24, 28):
        for i, (n, bt, d) in enumerate(phrase):
            last = i == len(phrase) - 2
            sig = mm.gtr_note(midi(n + 12), d * beat + 0.1, bend=2.0 if last else 0.0, vib=0.01 if d >= 2 else 0.0)
            st = int((base * bar + bt * beat) * SR)
            seg = sig[: max(0, len(lead) - st)]
            lead[st:st + len(seg)] += seg
    m.add(0, mm.amp(lead, drive=9.0, cut=5200), 0.26, pan=0.05)
    return m.finish(wet=0.26, rt60=2.0, bass=0.25)


# ------------------------------------------------------------------ tema "Heroica": fanfarria de metales
def heroic_theme(lib):
    bpm = 104
    beat = 60.0 / bpm
    bar = 4 * beat
    total = 32 * bar
    m = Mix(total, tail=4.0)
    D, G, Bm, A, Bb, F, Gm = 38, 43, 35, 33, 34, 41, 31
    prog = [(D, MAJ), (G, MAJ), (Bm, MIN), (A, MAJ), (D, MAJ), (G, MAJ), (A, MAJ), (D, MAJ)] * 2 + \
           [(Bb, MAJ), (F, MAJ), (Gm, MIN), (A, MAJ)] * 2 + [(D, MAJ), (G, MAJ), (A, MAJ), (D, MAJ)] * 2
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        tri = triad(root, q)
        m.add(t0, lib.cb.note(root, bar + 0.1, att=0.05, rel=0.5), 0.5)
        m.add(t0, lib.tuba.note(root, bar + 0.1, att=0.05, rel=0.5), 0.3, pan=0.15)
        pat = [0, 7, 12, 7, 0, 7, 12, 16 if q == MAJ else 15]
        for k in range(8):
            m.add(t0 + k * beat / 2, lib.vc_sp.note(root + 12 + pat[k], beat / 2, att=0.003, rel=0.1), 0.42, pan=-0.35)
        for i, n in enumerate(tri):
            m.add(t0, lib.vla.note(n + 24 if n + 24 < 70 else n + 12, bar + 0.2, att=0.15, rel=0.5), 0.2, pan=0.3 - 0.1 * i)
        # marcha: bombo, caja con redobles y timbales
        m.add(t0, hit(lib.bd), 0.75)
        m.add(t0 + 2 * beat, hit(lib.bd), 0.65)
        for k in range(8):
            if k in (2, 6) or (k == 7 and b % 2 == 1):
                m.add(t0 + k * beat / 2, hit(lib.sn), 0.5, pan=0.1)
            elif k % 2 == 1:
                m.add(t0 + k * beat / 2, hit(lib.sn_g), 0.22, pan=0.1)
        m.add(t0, hit(lib.timp_lo, 2.0), 0.45, pan=-0.2)
        if b % 8 == 0:
            m.add(t0, hit(lib.crash), 0.45, pan=0.3)
    fan = [(62, 0, 1.5), (66, 1.5, 0.5), (69, 2, 2), (74, 4, 3), (71, 7, 1), (69, 8, 1.5), (71, 9.5, 0.5), (73, 10, 2),
           (69, 12, 4), (74, 16, 1.5), (73, 17.5, 0.5), (71, 18, 2), (67, 20, 2), (69, 22, 2), (73, 24, 2), (76, 26, 2), (74, 28, 4)]
    for base in (0, 8, 24):
        for n, bt, d in fan:
            st = base * bar + bt * beat
            m.add(st, lib.tpt.note(n, d * beat + 0.05, att=0.02, rel=0.3), 0.4, pan=0.15)
            m.add(st, lib.hn.note(n - 12, d * beat + 0.05, att=0.04, rel=0.3), 0.4, pan=-0.1)
            m.add(st, lib.tbn.note(n - 12 - 4, d * beat + 0.05, att=0.04, rel=0.3), 0.2, pan=0.25)
    for b in range(16, 24):
        root, q = prog[b]
        m.add(b * bar, lib.vln.note(root + 36 + q, bar + 0.2, att=0.1, rel=0.5), 0.3, pan=-0.3)
        m.add(b * bar, lib.vln.note(root + 43, bar + 0.2, att=0.1, rel=0.5), 0.25, pan=0.3)
    m.add(16 * bar - 2 * beat, riser(lib, 2 * beat), 0.3)
    m.add(24 * bar - 2 * beat, riser(lib, 2 * beat), 0.3)
    m.add(24 * bar, hit(lib.gong), 0.3)
    return m.finish(wet=0.32, rt60=2.8, bass=0.25)


# ------------------------------------------------------------------ menú (lento y emotivo)
def menu_theme(lib):
    bpm = 62
    beat = 60.0 / bpm
    bar = 4 * beat
    total = 16 * bar
    m = Mix(total, tail=5.0)
    D, Bb, F, C, G, A = 38, 34, 41, 36, 31, 33
    prog = [(D, MIN), (Bb, MAJ), (F, MAJ), (C, MAJ), (D, MIN), (G, MIN), (Bb, MAJ), (A, MAJ)] * 2
    for b, (root, q) in enumerate(prog):
        t0 = b * bar
        tri = triad(root, q)
        m.add(t0, lib.cb.note(root, bar + 0.3, att=0.3, rel=1.0), 0.45)
        m.add(t0, lib.vc.note(root + 12, bar + 0.3, att=0.4, rel=1.0), 0.40, pan=-0.3)
        for i, n in enumerate(tri):
            m.add(t0, lib.vla.note(n + 24 if n + 24 < 70 else n + 12, bar + 0.3, att=0.5, rel=1.0), 0.18, pan=0.3 - 0.15 * i)
        if b >= 8:
            m.add(t0, lib.vln.note(tri[1] + 36, bar + 0.3, att=0.6, rel=1.0), 0.18, pan=-0.4)
            m.add(t0, lib.vln.note(tri[2] + 36, bar + 0.3, att=0.6, rel=1.0), 0.16, pan=0.4)
        # arpegio de piano
        arp = [root + 24, root + 24 + q, root + 31, root + 36]
        for k in range(8):
            m.add(t0 + k * beat / 2, lib.piano.note(arp[[0, 1, 2, 3, 2, 1, 2, 3][k]], beat, att=0.002, rel=1.0), 0.16, pan=0.2)
        if b in (0, 8):
            m.add(t0, hit(lib.timp_lo, 4.0), 0.30, pan=-0.2)
    mel = [(74, 0, 2), (72, 2, 1), (69, 3, 1), (70, 4, 3), (69, 7, 1), (72, 8, 2), (69, 10, 2), (67, 12, 3), (64, 15, 1),
           (65, 16, 2), (69, 18, 2), (74, 20, 2), (70, 22, 2), (74, 24, 2), (77, 26, 2), (76, 28, 3), (73, 31, 1)]
    for n, bt, d in mel:
        m.add(bt * beat, lib.piano.note(n + 12, d * beat, att=0.002, rel=1.4), 0.32, pan=0.1)
        m.add(8 * bar + bt * beat, lib.hn.note(n - 12, d * beat + 0.2, att=0.12, rel=0.8), 0.40, pan=0.05)
        m.add(8 * bar + bt * beat, lib.vln.note(n + 12, d * beat + 0.2, att=0.15, rel=0.8), 0.22, pan=-0.1)
    m.add(15 * bar, hit(lib.timp_roll, bar), 0.25)
    m.add(8 * bar, hit(lib.susp), 0.25, pan=0.3)
    return m.finish(wet=0.45, rt60=4.0, bass=0.25)


# ------------------------------------------------------------------ solo de barra (guitarra + percusión real)
def bar_solo(lib):
    bpm = 150
    beat = 60.0 / bpm
    m = Mix(3.4, tail=1.6)
    lead = np.zeros(len(m.L))
    notes = [(69, 0, 0.5), (72, 0.5, 0.5), (74, 1, 0.5), (76, 1.5, 0.5), (79, 2, 0.5), (81, 2.5, 0.5),
             (79, 3, 0.25), (81, 3.25, 0.25), (84, 3.5, 0.5), (81, 4, 2.6)]
    for i, (nt, b, d) in enumerate(notes):
        last = i == len(notes) - 1
        sig = mm.gtr_note(midi(nt), d * beat + (1.2 if last else 0.08), bend=2.0 if last else 0.0, vib=0.012 if last else 0.0)
        st = int(b * beat * SR)
        seg = sig[: max(0, len(lead) - st)]
        lead[st:st + len(seg)] += seg
    m.add(0, mm.amp(lead, drive=9.0, cut=5200), 0.42, pan=0.05)
    rhythm = np.zeros(len(m.L))
    mm.power_chord(rhythm, 0, 45, 1.6, False)
    mm.power_chord(rhythm, 4 * beat, 41, 1.8, False)
    m.add(0, mm.amp(rhythm), 0.20, pan=-0.4)
    m.add(0, hit(lib.crash), 0.5, pan=0.3)
    m.add(0, hit(lib.bd), 0.8)
    m.add(0, hit(lib.timp_lo, 2.5), 0.6)
    for n in (45, 52, 57):
        m.add(0, lib.hn_st.note(n, 0.6, att=0.003, rel=0.4), 0.35)
    for k in range(6):
        m.add(k * beat / 2, hit(lib.sn if k % 2 else lib.sn2), 0.35 + 0.05 * k, pan=0.1)
    m.add(4 * beat, hit(lib.crash2), 0.35, pan=-0.3)
    m.add(4 * beat, hit(lib.bd), 0.7)
    return m.finish(wet=0.3, rt60=2.0, bass=0.25, loop=False)


# ------------------------------------------------------------------ explosión de línea (bombo, timbal, platillo y gong)
def boom(lib):
    n = int(2.2 * SR)
    x = np.zeros(n)
    for sig, g, d in ((lib.bd, 1.0, 0), (lib.timp_lo, 0.8, 0.0), (lib.crash, 0.5, 0.02), (lib.gong, 0.35, 0.0)):
        s = sig[: n - int(d * SR)]
        x[int(d * SR):int(d * SR) + len(s)] += s * g
    x *= np.linspace(1, 0, n) ** 0.6
    return np.tanh(x / np.max(np.abs(x)) * 1.3) * 0.85


def save_stereo_wav(st, path):
    pcm = (np.clip(st, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == "__main__":
    lib = Lib(sys.argv[1])
    out = sys.argv[2]
    what = sys.argv[3] if len(sys.argv) > 3 else "all"
    if what in ("all", "game"):
        mm.save(game_theme(lib), out, "game_theme")
    if what in ("all", "themes"):
        mm.save(tension_theme(lib), out, "theme_tension")
        mm.save(rock_theme(lib), out, "theme_rock")
        mm.save(heroic_theme(lib), out, "theme_heroic")
    if what in ("all", "menu"):
        mm.save(menu_theme(lib), out, "menu_theme")
    if what in ("all", "solo"):
        st = bar_solo(lib)
        f = int(0.06 * SR)
        st[-f:] *= np.linspace(1, 0, f)[:, None]
        save_stereo_wav(st, os.path.join(out, "bar_solo.wav"))
    if what in ("all", "boom"):
        mm.save_wav_mono(boom(lib), out, "boom")
    print("ok")
