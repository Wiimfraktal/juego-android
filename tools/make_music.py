#!/usr/bin/env python3
"""Genera la música original del juego (sin letra) con síntesis aditiva.

Estilo: coro masculino "oh/ah" estilo canto gregoriano, violonchelos, campanas y tambores épicos
sobre armonías modales. Todo es composición propia, sin muestras ni material con derechos.

Uso: python3 tools/make_music.py <carpeta_salida>
Requiere numpy y ffmpeg (libopus).
"""
import subprocess
import sys
import wave

import numpy as np

SR = 32000
RNG = np.random.default_rng(7)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12.0)


def phase_of(freq_t):
    return 2 * np.pi * np.cumsum(freq_t) / SR


def vibrato(n, base, rate=5.2, depth=0.004, delay=0.4):
    t = np.arange(n) / SR
    ramp = np.clip((t - delay) / 0.8, 0, 1)
    return base * (1 + depth * ramp * np.sin(2 * np.pi * rate * t + RNG.uniform(0, 6.28)))


def adsr(n, a, r, hold_level=1.0):
    env = np.ones(n) * hold_level
    na = max(1, int(a * SR))
    nr = max(1, int(r * SR))
    env[:min(na, n)] *= np.linspace(0, 1, min(na, n)) ** 1.5
    if nr < n:
        env[n - nr:] *= np.linspace(1, 0, nr) ** 1.5
    return env


def formant_weights(freqs, formants):
    w = np.full(len(freqs), 0.04)
    for (fc, bw, g) in formants:
        w += g * np.exp(-((freqs - fc) / bw) ** 2)
    return w


def voice(freq, dur, formants, a=1.2, r=1.8, ens=6, depth=0.004, nh=34, tilt=1.0, vowel2=None):
    """Coro masculino: varias gargantas distintas (formantes y afinación propias), aliento y paso de una vocal a otra."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    morph = None
    if vowel2 is not None:
        morph = np.clip((t - dur * 0.25) / (dur * 0.5), 0, 1)
    ks = np.arange(1, nh + 1)
    for e in range(ens):
        det = 2 ** ((RNG.uniform(-12, 12)) / 1200.0)
        # la afinación flota despacio como en un coro real
        drift = np.cumsum(RNG.normal(0, 1, n)) / np.sqrt(n) * 0.0016
        drift = np.convolve(drift, np.ones(400) / 400, mode="same")
        f = vibrato(n, freq * det, rate=RNG.uniform(4.6, 5.7), depth=depth * RNG.uniform(0.7, 1.3),
                    delay=RNG.uniform(0.2, 0.9)) * (1 + drift)
        ph = phase_of(f)
        scale = RNG.uniform(0.94, 1.07)  # largo del tracto vocal de cada cantante
        fk = freq * ks
        valid = fk < 7500
        w1 = formant_weights(fk / scale, formants)
        w2 = formant_weights(fk / scale, vowel2) if vowel2 is not None else None
        sig = np.zeros(n)
        tr = 1.0 + 0.04 * np.sin(2 * np.pi * RNG.uniform(3.8, 4.6) * t + RNG.uniform(0, 6.28))
        for k in ks:
            if not valid[k - 1]:
                break
            amp = 1.0 / k ** tilt
            if morph is None:
                w = w1[k - 1]
            else:
                w = w1[k - 1] * (1 - morph) + w2[k - 1] * morph
            sig += amp * w * np.sin(k * ph + 0.37 * k)
        out += sig * tr
    out /= ens
    # aliento: ruido moldeado por los formantes
    noise = RNG.normal(0, 1, n)
    spec = np.fft.rfft(noise)
    fr = np.fft.rfftfreq(n, 1 / SR)
    spec *= formant_weights(fr, formants)
    breath = np.fft.irfft(spec, n)
    breath /= max(1e-9, np.max(np.abs(breath)))
    out = out / max(1e-9, np.max(np.abs(out))) + 0.035 * breath
    return out * adsr(n, a, r)


OH = [(450, 140, 1.0), (850, 160, 0.55), (2400, 300, 0.12)]
AH = [(700, 160, 1.0), (1150, 200, 0.6), (2600, 300, 0.12)]
EH = [(520, 140, 1.0), (1750, 200, 0.5), (2500, 300, 0.12)]


def strings(freq, dur, a=0.5, r=0.9, fc=1500.0, nh=24, ens=3, depth=0.003):
    n = int(dur * SR)
    out = np.zeros(n)
    for e in range(ens):
        det = 2 ** ((RNG.uniform(-6, 6)) / 1200.0)
        ph = phase_of(vibrato(n, freq * det, rate=5.0, depth=depth, delay=0.3))
        sig = np.zeros(n)
        for k in range(1, nh + 1):
            if freq * k > 8000:
                break
            sig += (1.0 / k) / (1 + (freq * k / fc) ** 2) * np.sin(k * ph + 0.7 * k)
        out += sig
    out /= ens
    bow = RNG.normal(0, 1, n)
    bow = np.convolve(bow, np.ones(6) / 6, mode="same") * 0.015
    return (out + bow) * adsr(n, a, r)


def pluck(freq, dur, decay=3.2):
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for k, (g, d) in enumerate([(1.0, 1.0), (0.45, 1.6), (0.25, 2.4), (0.12, 3.4)], start=1):
        out += g * np.sin(2 * np.pi * freq * k * t) * np.exp(-t * decay * d)
    return out * np.minimum(1.0, t * 800)


def bell(freq, dur):
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for ratio, g, d in [(1.0, 1.0, 1.1), (2.01, 0.5, 1.7), (2.76, 0.35, 2.3), (5.4, 0.15, 3.5)]:
        out += g * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-t * d)
    return out * np.minimum(1.0, t * 600)


def drum(dur=0.7, f0=48, sweep=95, decay=6.0, click=0.35):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f0 + sweep * np.exp(-t * 22)
    body = np.sin(phase_of(f)) * np.exp(-t * decay)
    noise = RNG.normal(0, 1, n)
    noise = np.convolve(noise, np.ones(8) / 8, mode="same") * np.exp(-t * 60) * click
    return body + noise


def tom(freq, dur=0.5, decay=9.0):
    return drum(dur, freq, freq * 0.8, decay, 0.15)


def shaker(dur=0.09):
    n = int(dur * SR)
    t = np.arange(n) / SR
    noise = RNG.normal(0, 1, n)
    hp = noise - np.convolve(noise, np.ones(10) / 10, mode="same")
    return hp * np.exp(-t * 45)


class Mix:
    def __init__(self, seconds, tail=4.0):
        self.n = int(seconds * SR)
        self.tail = int(tail * SR)
        self.L = np.zeros(self.n + self.tail)
        self.R = np.zeros(self.n + self.tail)

    def add(self, start, sig, gain=1.0, pan=0.0):
        i = int(start * SR)
        if i >= len(self.L):
            return
        sig = sig[: len(self.L) - i]
        gl = gain * np.cos((pan + 1) * np.pi / 4)
        gr = gain * np.sin((pan + 1) * np.pi / 4)
        self.L[i:i + len(sig)] += sig * gl
        self.R[i:i + len(sig)] += sig * gr

    def finish(self, wet=0.38, rt60=3.4, peak=0.89):
        n = len(self.L)
        ir_len = int(rt60 * SR)
        t = np.arange(ir_len) / SR
        env = np.exp(-6.9 * t / rt60)
        irs = []
        for _ in range(2):
            ir = RNG.normal(0, 1, ir_len) * env
            ir = np.cumsum(ir) * 0.02  # suaviza: reverb oscura
            ir -= np.convolve(ir, np.ones(4000) / 4000, mode="same") * 0.9
            ir /= np.sqrt(np.sum(ir ** 2))
            irs.append(ir)
        size = 1 << int(np.ceil(np.log2(n + ir_len)))
        outs = []
        for sig, ir in ((self.L, irs[0]), (self.R, irs[1])):
            fs = np.fft.rfft(sig, size)
            fi = np.fft.rfft(ir, size)
            wetsig = np.fft.irfft(fs * fi, size)[:n]
            outs.append(sig * (1 - wet * 0.5) + wetsig * wet * 2.2)
        L, R = outs
        # bucle sin costura: la cola de reverb se pliega al inicio
        main = self.n
        for ch in (L, R):
            tail = ch[main:]
            ch[:len(tail)] += tail
        L, R = L[:main], R[:main]
        m = max(np.max(np.abs(L)), np.max(np.abs(R)))
        L, R = np.tanh(L / m * 1.25) / np.tanh(1.25), np.tanh(R / m * 1.25) / np.tanh(1.25)
        L, R = L * peak, R * peak
        return np.stack([L, R], axis=1)


def save(stereo, out_dir, name):
    pcm = (np.clip(stereo, -1, 1) * 32767).astype("<i2")
    wav = f"{out_dir}/{name}.wav"
    with wave.open(wav, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libopus", "-b:a", "112k",
                    "-ar", "48000", f"{out_dir}/{name}.ogg"], check=True)


def chord_notes(root, third):
    return [root + 12, root + 19, root + 24, root + third + 24]


# ------------------------------------------------------------------ menú: calmo y solemne
def menu_theme():
    bpm = 64
    beat = 60.0 / bpm
    bars = 16
    total = bars * 4 * beat
    m = Mix(total)
    chords = [(45, 3), (41, 4), (48, 4), (43, 4), (45, 3), (41, 4), (38, 3), (40, 4)]  # 2 compases c/u
    for ci, (root, third) in enumerate(chords):
        t0 = ci * 8 * beat
        dur = 8 * beat + 2.5
        for j, nt in enumerate(chord_notes(root, third)):
            vow = [OH, AH, OH, EH][j % 4]
            m.add(t0, voice(midi(nt - 12), dur, vow, a=1.8, r=2.4, vowel2=AH if j % 2 == 0 else OH), 0.22, pan=-0.6 + 0.4 * j)
            if j in (0, 1):
                m.add(t0 + 0.05, voice(midi(nt), dur, vow, a=1.8, r=2.4, vowel2=EH), 0.12, pan=0.3 - 0.4 * j)
        # violonchelo largo
        m.add(t0, strings(midi(root + 12), 4 * beat + 0.8, a=0.9, r=1.2, fc=1200), 0.33, pan=-0.2)
        m.add(t0 + 4 * beat, strings(midi(root + 19), 4 * beat + 0.8, a=0.9, r=1.2, fc=1200), 0.30, pan=0.2)
    # bordón grave continuo
    m.add(0, voice(midi(33), total + 1.0, OH, a=3.0, r=3.0, ens=2, depth=0.0015, nh=14), 0.30, pan=0.0)
    # melodía solista "ah" (en beats absolutos)
    mel = [(76, 8, 2), (74, 10, 1), (72, 11, 1), (71, 12, 2), (69, 14, 2),
           (76, 16, 3), (79, 19, 1), (76, 20, 2), (74, 22, 2),
           (74, 24, 2), (71, 26, 1), (74, 27, 1), (79, 28, 4),
           (69, 32, 1), (72, 33, 1), (76, 34, 2), (76, 36, 2), (72, 38, 2),
           (72, 40, 2), (69, 42, 1), (72, 43, 1), (77, 44, 3), (76, 47, 1),
           (74, 48, 2), (77, 50, 2), (81, 52, 2), (79, 54, 2),
           (71, 56, 2), (68, 58, 2), (76, 60, 4)]
    for nt, b, d in mel:
        m.add(b * beat, voice(midi(nt - 12), d * beat + 1.1, AH, a=0.5, r=1.1, ens=4, depth=0.006, vowel2=OH), 0.22, pan=0.1)
    return m.finish(wet=0.50, rt60=5.6)


# ------------------------------------------------------------------ partida: tenso y épico
def game_theme():
    bpm = 100
    beat = 60.0 / bpm
    bars = 24
    total = bars * 4 * beat
    m = Mix(total, tail=3.0)
    cycle = [(45, 3), (45, 3), (41, 4), (41, 4), (43, 4), (43, 4), (40, 4), (40, 4)]  # 1 compás c/u, 8 por ciclo
    for cy in range(3):
        for ci, (root, third) in enumerate(cycle):
            bar = cy * 8 + ci
            t0 = bar * 4 * beat
            # coro
            for j, nt in enumerate(chord_notes(root, third)[: (3 if cy == 0 else 4)]):
                vow = [OH, AH, OH, EH][j % 4]
                m.add(t0, voice(midi(nt), 4 * beat + 1.4, vow, a=0.7, r=1.4), 0.17, pan=-0.5 + 0.35 * j)
            if cy == 2:
                m.add(t0, voice(midi(root + 36), 4 * beat + 1.2, AH, a=0.7, r=1.2, ens=2), 0.10, pan=0.4)
            # ostinato de cuerdas (corcheas)
            for k in range(8):
                nt = root + 12 + (7 if k % 4 == 2 else 0) + (12 if k % 8 == 7 else 0)
                m.add(t0 + k * beat / 2, strings(midi(nt), beat * 0.55, a=0.01, r=0.12, fc=2200, ens=1),
                      0.20 if k % 2 == 0 else 0.13, pan=-0.3 if k % 2 == 0 else 0.3)
            # percusión
            m.add(t0, drum(), 0.75, pan=0.0)
            m.add(t0 + 2 * beat, drum(f0=44, sweep=80), 0.62, pan=0.0)
            if cy >= 1:
                m.add(t0 + 3.5 * beat, tom(120), 0.30, pan=-0.3)
                m.add(t0 + 3.75 * beat, tom(95), 0.30, pan=0.3)
            if cy == 2:
                m.add(t0 + 1 * beat, tom(100), 0.30, pan=0.3)
            for k in range(16):
                m.add(t0 + k * beat / 4, shaker(), 0.07 if k % 2 else 0.04, pan=0.2 if k % 2 else -0.2)
        # melodía de metal/coro en los ciclos 2 y 3
        if cy >= 1:
            base = cy * 8 * 4 * beat
            motif = [(69, 0, 1), (72, 1, 1), (76, 2, 2), (74, 4, 1), (72, 5, 1), (69, 6, 2),
                     (72, 8, 1), (77, 9, 1), (81, 10, 2), (79, 12, 2), (76, 14, 2),
                     (74, 16, 1), (79, 17, 1), (83, 18, 2), (81, 20, 2), (79, 22, 2),
                     (76, 24, 2), (74, 26, 1), (71, 27, 1), (68, 28, 4)]
            for nt, b, d in motif:
                m.add(base + b * beat, strings(midi(nt), d * beat + 0.6, a=0.08, r=0.5, fc=3200, ens=3, depth=0.005),
                      0.22, pan=0.1)
    # bordón
    m.add(0, voice(midi(33), total + 1.0, OH, a=2.0, r=2.0, ens=2, depth=0.0015, nh=14), 0.26)
    return m.finish(wet=0.30, rt60=2.6)


def place_sfx():
    """Golpe sordo y metálico muy leve cuando una nave se acopla."""
    n = int(0.32 * SR)
    t = np.arange(n) / SR
    thump = np.sin(phase_of(70 + 90 * np.exp(-t * 40))) * np.exp(-t * 16)
    click = RNG.normal(0, 1, n)
    click = np.convolve(click, np.ones(5) / 5, mode="same") * np.exp(-t * 130)
    ring = (np.sin(2 * np.pi * 820 * t) * 0.5 + np.sin(2 * np.pi * 1330 * t) * 0.3) * np.exp(-t * 24)
    x = thump * 0.9 + click * 0.25 + ring * 0.12
    x *= np.minimum(1.0, t * 900)
    return np.tanh(x * 1.2) * 0.8


def save_wav_mono(sig, out_dir, name):
    pcm = (np.clip(sig, -1, 1) * 32767).astype("<i2")
    with wave.open(f"{out_dir}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    save(menu_theme(), out, "menu_theme")
    save(game_theme(), out, "game_theme")
    save_wav_mono(place_sfx(), out, "place")
    print("ok")
