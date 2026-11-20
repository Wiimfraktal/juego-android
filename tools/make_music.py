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

    def finish(self, wet=0.38, rt60=3.4, peak=0.89, bass=0.9):
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
            mix = sig * (1 - wet * 0.5) + wetsig * wet * 2.2
            fm = np.fft.rfft(mix, size)
            fr = np.fft.rfftfreq(size, 1 / SR)
            hp = (fr / 35.0) ** 4 / (1 + (fr / 35.0) ** 4)  # quita el subgrave inaudible
            lows = np.fft.irfft(fm * hp / (1 + (fr / 220.0) ** 4), size)[:n]  # banda 35-220 Hz
            base = np.fft.irfft(fm * hp, size)[:n]
            outs.append(base + bass * lows)
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


def section(freq, dur, a=1.2, r=1.6, fc=1700.0, ens=7, nh=26, depth=0.0035, scoop=0.0):
    """Cuerdas de orquesta: muchas voces con vibrato propio y resonancia de cuerpo."""
    n = int(dur * SR)
    out = np.zeros(n)
    ks = np.arange(1, nh + 1)
    for e in range(ens):
        det = 2 ** ((RNG.uniform(-9, 9)) / 1200.0)
        drift = np.cumsum(RNG.normal(0, 1, n)) / np.sqrt(n) * 0.0012
        drift = np.convolve(drift, np.ones(400) / 400, mode="same")
        f = vibrato(n, freq * det, rate=RNG.uniform(4.6, 5.8), depth=depth * RNG.uniform(0.7, 1.3),
                    delay=RNG.uniform(0.3, 1.0)) * (1 + drift)
        if scoop:
            f = f * 2 ** (-scoop / 12.0 * np.exp(-np.arange(n) / SR / 0.14))
        ph = phase_of(f)
        sig = np.zeros(n)
        for k in ks:
            fk = freq * k
            if fk > 9000:
                break
            amp = k ** -1.15 / (1 + (fk / fc) ** 2)
            amp *= 1 + 1.2 * np.exp(-((fk - 300) / 120) ** 2) + 0.8 * np.exp(-((fk - 1100) / 250) ** 2)
            sig += amp * np.sin(k * ph + RNG.uniform(0, 6.28))
        out += sig
    out /= ens
    bow = np.convolve(RNG.normal(0, 1, n), np.ones(8) / 8, mode="same") * 0.01
    out = out / max(1e-9, np.max(np.abs(out)))
    return (out + bow) * adsr(n, a, r)


def sub(freq, dur, a=2.0, r=2.4):
    n = int(dur * SR)
    ph = phase_of(vibrato(n, freq, rate=0.3, depth=0.0008, delay=0.1))
    return (np.sin(ph) + 0.8 * np.sin(2 * ph) + 0.5 * np.sin(3 * ph)) * adsr(n, a, r)


def horn(freq, dur, a=0.45, r=0.9, ens=3):
    """Trompas: sonido redondo y cálido con ataque lento."""
    n = int(dur * SR)
    out = np.zeros(n)
    ks = np.arange(1, 22)
    for e in range(ens):
        det = 2 ** ((RNG.uniform(-7, 7)) / 1200.0)
        ph = phase_of(vibrato(n, freq * det, rate=4.8, depth=0.0025, delay=0.6))
        sig = np.zeros(n)
        for k in ks:
            fk = freq * k
            if fk > 6500:
                break
            amp = k ** -1.5 * (1 + 2.2 * np.exp(-((fk - 520) / 300) ** 2) + 1.2 * np.exp(-((fk - 1250) / 450) ** 2))
            sig += amp * np.sin(k * ph + RNG.uniform(0, 6.28))
        out += sig
    out /= ens
    out = out / max(1e-9, np.max(np.abs(out)))
    return out * adsr(n, a, r)


def timpani(freq, dur=2.8, decay=2.6):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = freq * (1 + 0.55 * np.exp(-t * 28))
    body = np.sin(phase_of(f)) * np.exp(-t * decay)
    body += 0.45 * np.sin(phase_of(f * 1.5)) * np.exp(-t * decay * 1.6)
    body += 0.25 * np.sin(phase_of(f * 2.0)) * np.exp(-t * decay * 2.2)
    thud = np.convolve(RNG.normal(0, 1, n), np.ones(24) / 24, mode="same") * np.exp(-t * 70) * 0.5
    return (body + thud) * np.minimum(1.0, t * 1500)


def swell(dur=3.2):
    """Platillo suave que crece."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    noise = RNG.normal(0, 1, n)
    hp = noise - np.convolve(noise, np.ones(14) / 14, mode="same")
    env = (t / dur) ** 2.2 * np.exp(-np.maximum(0, t - dur * 0.8) * 5)
    return hp * env


# ------------------------------------------------------------------ menú: orquesta, coro y graves, emotivo
EXT = [14, 11, 14, 14, 14, 11, 10, 10]  # novena/séptima de cada acorde


def menu_theme():
    beat = 1.1
    total = 64 * beat
    m = Mix(total, tail=5.5)
    chords = [(45, 3), (41, 4), (48, 4), (43, 4), (45, 3), (41, 4), (38, 3), (40, 4)]  # 8 tiempos c/u
    for ci, (root, third) in enumerate(chords):
        t0 = ci * 8 * beat
        dur = 8 * beat + 2.8
        m.add(t0, sub(midi(root - 12), dur), 0.14)
        m.add(t0, section(midi(root - 12), dur, a=2.2, r=2.6, fc=520, ens=5), 0.40)
        m.add(t0, section(midi(root), dur, a=2.2, r=2.6, fc=850, ens=5), 0.32, pan=-0.25)
        m.add(t0, section(midi(root + 19), dur, a=2.2, r=2.6, fc=1500, ens=5), 0.19, pan=0.2)
        m.add(t0, section(midi(root + third + 12), dur, a=2.2, r=2.6, fc=1500, ens=5), 0.19, pan=-0.1)
        m.add(t0 + 1.5 * beat, section(midi(root + EXT[ci] + 12), dur - 2 * beat, a=2.6, r=2.6, fc=1800, ens=5), 0.12, pan=0.35)
        if ci >= 1:
            m.add(t0 + 0.4, section(midi(root + 24), dur, a=2.6, r=2.8, fc=2800, ens=8), 0.18, pan=-0.4)
            m.add(t0 + 0.4, section(midi(root + third + 24), dur, a=2.6, r=2.8, fc=2800, ens=8), 0.15, pan=0.4)
        # coro de bajos y voces medias
        m.add(t0, voice(midi(root - 12), dur, OH, a=2.4, r=2.8, ens=5, vowel2=AH), 0.18, pan=0.0)
        for j, nt in enumerate(chord_notes(root, third)[:3]):
            m.add(t0, voice(midi(nt - 12), dur, [OH, AH, OH][j], a=2.4, r=2.8, ens=5, vowel2=AH), 0.12, pan=-0.5 + 0.5 * j)
        if ci >= 2:
            tones = [root + 12, root + third + 12, root + 19, root + 24, root + EXT[ci] + 12, root + 31]
            for k in range(8):
                m.add(t0 + k * beat, pluck(midi(tones[[0, 1, 2, 3, 4, 3, 2, 1][k]]), 3.8, 1.2), 0.055, pan=-0.5 + 0.14 * k)
        m.add(t0, timpani(midi(root - 24), 4.0, 1.5), 0.30)
    mel = [(76, 8, 3), (74, 11, 1), (72, 12, 1), (71, 13, 2), (69, 15, 1),
           (76, 16, 3), (79, 19, 1), (76, 20, 2), (74, 22, 2),
           (74, 24, 2), (71, 26, 1), (74, 27, 1), (79, 28, 4),
           (69, 32, 1), (72, 33, 1), (76, 34, 2), (76, 36, 2), (72, 38, 2),
           (72, 40, 2), (69, 42, 1), (72, 43, 1), (77, 44, 3), (76, 47, 1),
           (74, 48, 2), (77, 50, 2), (81, 52, 2), (79, 54, 2),
           (71, 56, 2), (68, 58, 2), (76, 60, 4)]
    for nt, b, d in mel:
        if b < 32:
            m.add(b * beat, section(midi(nt - 12), d * beat + 1.2, a=0.5, r=1.4, fc=2400, ens=3, depth=0.009, scoop=1.0), 0.28, pan=0.15)
            m.add(b * beat, section(midi(nt), d * beat + 1.2, a=0.6, r=1.4, fc=3200, ens=4, depth=0.008, scoop=0.8), 0.14, pan=0.25)
        else:
            m.add(b * beat, horn(midi(nt - 12), d * beat + 1.0, a=0.55, r=1.1), 0.24, pan=0.1)
            m.add(b * beat, section(midi(nt), d * beat + 1.2, a=0.6, r=1.4, fc=3400, ens=6, depth=0.008, scoop=0.8), 0.18, pan=-0.2)
    m.add(32 * beat, timpani(midi(33), 3.5, 1.8), 0.40)
    m.add(48 * beat, timpani(midi(33), 3.5, 1.8), 0.36)
    for i, tt in enumerate([60, 60.6, 61.2, 61.8, 62.3, 62.7, 63.0, 63.3]):
        m.add(tt * beat, timpani(midi(40), 1.8, 3.2), 0.12 + 0.03 * i)
    m.add(30 * beat, swell(), 0.05)
    return m.finish(wet=0.46, rt60=5.2, bass=0.35)


def crash(dur=3.5):
    """Platillo de impacto con caída larga."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    noise = RNG.normal(0, 1, n)
    hp = noise - np.convolve(noise, np.ones(10) / 10, mode="same")
    return hp * np.exp(-t * 1.6) * np.minimum(1.0, t * 400)


# ------------------------------------------------------------------ partida: Epic Core orquestal
def game_theme():
    bpm = 128
    beat = 60.0 / bpm
    total = 128 * beat
    m = Mix(total, tail=3.5)
    cyc = [(45, 3), (41, 4), (48, 4), (43, 4), (45, 3), (41, 4), (43, 4), (40, 4)]  # 8 tiempos c/u
    for cy in range(2):
        full = cy == 1
        for ci, (root, third) in enumerate(cyc):
            t0 = (cy * 8 + ci) * 8 * beat
            dur = 8 * beat + 1.8
            # base de cuerdas graves, violas y violines
            m.add(t0, sub(midi(root - 12), dur, a=0.6, r=1.6), 0.15)
            m.add(t0, section(midi(root - 12), dur, a=0.5, r=1.6, fc=600, ens=5), 0.40)
            m.add(t0, section(midi(root), dur, a=0.5, r=1.6, fc=950, ens=5), 0.28, pan=-0.25)
            m.add(t0, section(midi(root + 19), dur, a=0.7, r=1.6, fc=1700, ens=5), 0.18, pan=0.2)
            m.add(t0, section(midi(root + third + 12), dur, a=0.7, r=1.6, fc=1700, ens=5), 0.18, pan=-0.1)
            m.add(t0, section(midi(root + 24), dur, a=0.9, r=1.6, fc=3000, ens=8), 0.17, pan=-0.4)
            m.add(t0, section(midi(root + third + 24), dur, a=0.9, r=1.6, fc=3000, ens=8), 0.14, pan=0.4)
            # ostinato orquestal de cuerdas en semicorcheas (spiccato)
            pat = [0, 0, 7, 0, 0, 0, 7, 12]
            for k in range(32):
                nt = root + 12 + pat[k % 8]
                acc = 1.0 if k % 4 == 0 else 0.65
                m.add(t0 + k * beat / 4, section(midi(nt), 0.2, a=0.004, r=0.09, fc=2300, ens=4),
                      (0.20 if full else 0.13) * acc, pan=-0.35 if k % 2 == 0 else 0.35)
            # trompas: acordes de fanfarria
            if full or ci >= 2:
                for k, (bt, d) in enumerate([(0, 1.5), (1.5, 1.0), (2.5, 1.5), (4, 3.5)]):
                    for iv in (0, 7, 12):
                        m.add(t0 + bt * beat, horn(midi(root + 12 + iv), d * beat + 0.4, a=0.05, r=0.5), 0.15 if full else 0.10,
                              pan=-0.3 + 0.3 * (iv // 7))
            # coro potente
            if full:
                for j, nt in enumerate(chord_notes(root, third)[:4]):
                    m.add(t0, voice(midi(nt - 12), dur, [OH, AH, OH, EH][j], a=0.5, r=1.6, ens=6, vowel2=AH), 0.13, pan=-0.5 + 0.33 * j)
            # taiko y tambores épicos
            m.add(t0, timpani(midi(root - 24), 3.5, 1.6), 0.60)
            m.add(t0, drum(f0=52, sweep=70, decay=3.5), 0.45)
            m.add(t0 + 2 * beat, timpani(midi(root - 12), 2.0, 2.8), 0.40)
            m.add(t0 + 4 * beat, timpani(midi(root - 24), 2.5, 2.2), 0.45)
            m.add(t0 + 6 * beat, timpani(midi(root - 12), 2.0, 2.8), 0.30)
            for bt in ((3, 5.5, 7, 7.5) if full else (3, 7)):
                m.add(t0 + bt * beat, tom(95 if bt % 2 else 120, 0.45, 8.0), 0.30, pan=-0.2 if bt % 2 else 0.2)
            # braam grave en cada cambio de acorde
            if full or ci % 2 == 0:
                m.add(t0, horn(midi(root - 12), 3.0, a=0.06, r=1.4), 0.28)
                m.add(t0, horn(midi(root - 24 + 12), 3.0, a=0.06, r=1.4), 0.20)
            if ci == 7:
                m.add(t0 + 4.5 * beat, swell(3.4), 0.11 if full else 0.08)
    # subida e impacto al empezar el segundo ciclo
    t_imp = 64 * beat
    m.add(t_imp - 4 * beat, swell(4 * beat), 0.20)
    m.add(t_imp, crash(), 0.20)
    m.add(t_imp, timpani(midi(33), 4.0, 1.4), 0.75)
    # melodía heroica
    motif = [(69, 0, 2), (72, 2, 1), (76, 3, 2), (74, 5, 1), (72, 6, 2),
             (72, 8, 1), (77, 9, 1), (81, 10, 2), (79, 12, 2), (76, 14, 2),
             (74, 16, 1), (79, 17, 1), (83, 18, 2), (81, 20, 2), (79, 22, 2),
             (76, 24, 2), (74, 26, 1), (71, 27, 1), (68, 28, 4)]
    for base in (32, 64, 96):
        for nt, b, d in motif:
            vol = 0.22 if base >= 64 else 0.14
            m.add((base + b) * beat, section(midi(nt), d * beat + 0.6, a=0.08, r=0.6, fc=3400, ens=6, depth=0.007, scoop=0.7), vol, pan=-0.1)
            m.add((base + b) * beat, horn(midi(nt - 12), d * beat + 0.6, a=0.08, r=0.6), vol * 0.8, pan=0.1)
    return m.finish(wet=0.34, rt60=3.2, bass=0.3)


def place_sfx(kind=0):
    """Sonido muy leve al fijar una nave; cada facción tiene el suyo."""
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    if kind == 0:  # golpe sordo
        x = np.sin(phase_of(70 + 90 * np.exp(-t * 40))) * np.exp(-t * 16)
        x += np.convolve(RNG.normal(0, 1, n), np.ones(5) / 5, mode="same") * np.exp(-t * 130) * 0.25
        x += (np.sin(2 * np.pi * 820 * t) * 0.5 + np.sin(2 * np.pi * 1330 * t) * 0.3) * np.exp(-t * 24) * 0.12
    elif kind == 1:  # choque metálico
        x = sum(g * np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, g, d in
                [(310, 1.0, 9), (487, 0.8, 11), (913, 0.6, 14), (1371, 0.4, 18), (2210, 0.25, 25)])
        x = x * 0.45 + np.sin(phase_of(120 + 80 * np.exp(-t * 50))) * np.exp(-t * 20) * 0.5
    elif kind == 2:  # zumbido láser
        x = np.sin(phase_of(1900 * np.exp(-t * 9) + 260)) * np.exp(-t * 12) * 0.7
        x += np.sin(phase_of(70 + 60 * np.exp(-t * 40))) * np.exp(-t * 18) * 0.5
    elif kind == 3:  # campanilla de cristal
        x = sum(g * np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, g, d in
                [(1568, 1.0, 7), (2349, 0.5, 9), (3136, 0.3, 12), (4186, 0.15, 16)]) * 0.5
        x += np.sin(phase_of(80 + 60 * np.exp(-t * 40))) * np.exp(-t * 22) * 0.35
    elif kind == 4:  # gong grave
        x = sum(g * np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, g, d in
                [(95, 1.0, 5), (143, 0.7, 6), (211, 0.5, 8), (317, 0.3, 11)]) * 0.7
    elif kind == 6:  # arpegio brillante ascendente
        x = np.zeros(n)
        for i, f in enumerate([1046, 1318, 1568, 2093]):
            tt = np.maximum(0, t - i * 0.045)
            x += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 14) * (tt > 0) * 0.35
        x += np.sin(phase_of(75 + 60 * np.exp(-t * 40))) * np.exp(-t * 20) * 0.35
    else:  # descarga eléctrica
        noise = RNG.normal(0, 1, n)
        buzz = np.sin(2 * np.pi * 140 * t) * np.sign(np.sin(2 * np.pi * 37 * t))
        x = (noise - np.convolve(noise, np.ones(6) / 6, mode="same")) * np.exp(-t * 28) * 0.5 + buzz * np.exp(-t * 16) * 0.35
        x += np.sin(phase_of(60 + 90 * np.exp(-t * 45))) * np.exp(-t * 20) * 0.4
    x = x * np.minimum(1.0, t * 900)
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
    only = sys.argv[2] if len(sys.argv) > 2 else "all"
    if only == "sfx":
        for k in range(7):
            save_wav_mono(place_sfx(k), out, f"place_{k}")
        print("ok")
        raise SystemExit
    if only in ("all", "menu"):
        save(menu_theme(), out, "menu_theme")
    if only in ("all", "game"):
        save(game_theme(), out, "game_theme")
    for k in range(7):
        save_wav_mono(place_sfx(k), out, f"place_{k}")
    print("ok")
