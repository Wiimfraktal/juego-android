package com.cristal.abismo

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/**
 * Anunciador con voz grave y contundente. La voz se genera una sola vez con el motor de texto a voz del
 * teléfono (en español), se procesa (tono más bajo, compresión, saturación suave, refuerzo de graves y
 * reverberación de sala) y se guarda en caché; después se reproduce con SoundPool.
 */
class Announcer(private val ctx: Context) {
    companion object {
        val PHRASES = arrayOf(
            "Línea", "Doble línea", "Triple línea", "¡Devastación!",
            "Racha", "Frenesí", "¡Imparable!", "¡Arrasador!", "¡Intocable!", "¡Invencible!",
            "Detonación", "¡Demolición!", "Al límite", "Nuevo récord", "Ascenso"
        )
        const val LINE = 0; const val DOUBLE = 1; const val TRIPLE = 2; const val DEVAST = 3
        const val SPREE = 4; const val FRENZY = 5; const val UNSTOP = 6; const val RAMPAGE = 7
        const val UNTOUCH = 8; const val INVINC = 9; const val DETON = 10; const val DEMOL = 11
        const val LIMIT = 12; const val RECORD = 13; const val PROMO = 14
        private const val VER = "v2"
    }

    var enabled = true
    @Volatile var ready = false
    private val dir = ctx.cacheDir
    private var pool: SoundPool? = null
    private val ids = IntArray(PHRASES.size)
    private val ok = BooleanArray(PHRASES.size)
    private var tts: TextToSpeech? = null

    private fun outFile(i: Int) = File(dir, "vox_${VER}_$i.wav")

    /** Llamar desde el hilo principal: carga la voz guardada o la genera en segundo plano. */
    fun start() {
        try { dir.listFiles()?.forEach { if (it.name.startsWith("vox_") && !it.name.startsWith("vox_${VER}_")) it.delete() } } catch (e: Exception) { }
        if (PHRASES.indices.all { outFile(it).exists() }) { Thread { loadPool() }.start(); return }
        try {
            tts = TextToSpeech(ctx.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) Thread { generate() }.start()
            }
        } catch (e: Exception) { }
    }

    private fun generate() {
        val t = tts ?: return
        try {
            val locales = arrayOf(Locale("es", "MX"), Locale("es", "US"), Locale("es", "ES"), Locale("es"))
            var chosen: Locale? = null
            for (l in locales) if (t.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE) { chosen = l; break }
            if (chosen == null) { t.shutdown(); return }
            t.language = chosen
            pickMaleVoice(t, chosen)
            t.setPitch(0.74f)
            t.setSpeechRate(0.97f)
            for (i in PHRASES.indices) {
                val raw = File(dir, "vox_raw_$i.wav")
                val latch = CountDownLatch(1)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { latch.countDown() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { latch.countDown() }
                })
                t.synthesizeToFile(PHRASES[i], Bundle(), raw, "u$i")
                latch.await(8, TimeUnit.SECONDS)
                if (raw.exists() && raw.length() > 1000) process(raw, outFile(i))
                raw.delete()
            }
        } catch (e: Exception) { }
        try { t.shutdown() } catch (e: Exception) { }
        tts = null
        loadPool()
    }

    /** Elige una voz masculina del idioma si el motor la ofrece (Google: es-xx-x-...-local con "male"). */
    private fun pickMaleVoice(t: TextToSpeech, loc: Locale) {
        try {
            val vs = t.voices ?: return
            val same = vs.filter { it.locale.language == "es" && !it.isNetworkConnectionRequired }
                .sortedByDescending { (if (it.locale.country == loc.country) 2 else 0) + if (it.quality >= 400) 1 else 0 }
            val male = same.firstOrNull { v ->
                val n = v.name.lowercase()
                n.contains("male") && !n.contains("female") || v.features.any { it.lowercase().contains("gender=male") }
            } ?: same.firstOrNull { v ->
                // en voces de Google, las variantes "-x-eed", "-x-esd", "-x-esf" suelen ser masculinas
                val n = v.name.lowercase(); n.contains("-x-eed") || n.contains("-x-esd") || n.contains("-x-ese") || n.contains("-x-esf")
            }
            if (male != null) t.voice = male
        } catch (e: Exception) { }
    }

    /** Lee un WAV PCM de 16 bits (mono o estéreo) y devuelve muestras y frecuencia. */
    private fun readWav(f: File): Pair<FloatArray, Int>? {
        val bytes = f.readBytes()
        if (bytes.size < 44) return null
        fun i32(o: Int) = (bytes[o].toInt() and 255) or ((bytes[o + 1].toInt() and 255) shl 8) or
            ((bytes[o + 2].toInt() and 255) shl 16) or ((bytes[o + 3].toInt() and 255) shl 24)
        fun i16(o: Int) = (bytes[o].toInt() and 255) or ((bytes[o + 1].toInt() and 255) shl 8)
        var p = 12
        var sr = 22050; var ch = 1; var bits = 16
        var dataOff = -1; var dataLen = 0
        while (p + 8 <= bytes.size) {
            val id = String(bytes, p, 4, Charsets.US_ASCII)
            val len = i32(p + 4)
            if (id == "fmt ") { ch = i16(p + 10); sr = i32(p + 12); bits = i16(p + 22) }
            if (id == "data") { dataOff = p + 8; dataLen = min(len, bytes.size - dataOff); break }
            p += 8 + len + (len and 1)
        }
        if (dataOff < 0 || bits != 16) return null
        val frames = dataLen / (2 * ch)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var s = 0f
            for (c in 0 until ch) {
                val o = dataOff + (i * ch + c) * 2
                s += (i16(o).toShort()).toFloat() / 32768f
            }
            out[i] = s / ch
        }
        return Pair(out, sr)
    }

    private fun process(src: File, dst: File) {
        val (x0, sr) = readWav(src) ?: return
        // recorte de silencios
        var a = 0; var b = x0.size - 1
        while (a < b && abs(x0[a]) < 0.01f) a++
        while (b > a && abs(x0[b]) < 0.01f) b--
        if (b - a < 100) return
        // tono un poco más grave por re-muestreo (apenas más lento)
        val ratio = 0.94f
        val n = ((b - a) / ratio).toInt()
        val y = FloatArray(n + (0.5f * sr).toInt())
        for (i in 0 until n) {
            val pos = a + i * ratio
            val k = pos.toInt()
            val fr = pos - k
            y[i] = x0[k] * (1f - fr) + x0[min(k + 1, x0.size - 1)] * fr
        }
        // paso alto a 80 Hz (quita retumbe), cuerpo grave moderado y presencia en 2-4 kHz (claridad)
        val twoPi = 2f * Math.PI.toFloat()
        var hp = 0f; var lp = 0f; var b1 = 0f; var b2 = 0f
        val ah = 1f - exp(-twoPi * 80f / sr)
        val al = 1f - exp(-twoPi * 200f / sr)
        val a1 = 1f - exp(-twoPi * 1900f / sr)
        val a2 = 1f - exp(-twoPi * 4200f / sr)
        for (i in y.indices) {
            hp += ah * (y[i] - hp)
            val x = y[i] - hp
            lp += al * (x - lp)
            b1 += a1 * (x - b1); b2 += a2 * (x - b2)
            val presence = b2 - b1
            y[i] = x + 0.35f * lp + 0.55f * presence
        }
        // normalizar y comprimir
        var pk = 1e-6f
        for (v in y) pk = max(pk, abs(v))
        var env = 0f
        val att = exp(-1f / (0.004f * sr)); val rel = exp(-1f / (0.12f * sr))
        for (i in y.indices) {
            val v = y[i] / pk
            val av = abs(v)
            env = if (av > env) att * env + (1 - att) * av else rel * env + (1 - rel) * av
            val thr = 0.35f
            val gain = if (env > thr) (thr + (env - thr) / 2.5f) / env else 1f
            y[i] = tanh(v * gain * 1.5f) / tanh(1.5f)
        }
        // reverberación de sala corta (Schroeder: 4 peines + 2 pasa-todo)
        val wet = FloatArray(y.size)
        val combs = intArrayOf(1116, 1188, 1277, 1356).map { it * sr / 44100 }
        for (d in combs) {
            val buf = FloatArray(d); var idx = 0
            for (i in y.indices) {
                val o = buf[idx]
                buf[idx] = y[i] + o * 0.6f
                idx = (idx + 1) % d
                wet[i] += o * 0.25f
            }
        }
        for (d in intArrayOf(556, 341).map { it * sr / 44100 }) {
            val buf = FloatArray(d); var idx = 0
            for (i in wet.indices) {
                val bo = buf[idx]
                val v = wet[i] + bo * 0.5f
                buf[idx] = v
                wet[i] = bo - v * 0.5f
                idx = (idx + 1) % d
            }
        }
        var pk2 = 1e-6f
        for (i in y.indices) { y[i] = y[i] * 0.92f + wet[i] * 0.12f; pk2 = max(pk2, abs(y[i])) }
        writeWav(dst, y, sr, 0.95f / pk2)
    }

    private fun writeWav(f: File, y: FloatArray, sr: Int, g: Float) {
        val tmp = File(f.path + ".tmp")
        DataOutputStream(FileOutputStream(tmp).buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 255); o.write((v shr 8) and 255); o.write((v shr 16) and 255); o.write((v shr 24) and 255) }
            fun le16(v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }
            o.writeBytes("RIFF"); le32(36 + y.size * 2); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sr); le32(sr * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(y.size * 2)
            for (v in y) le16((v * g * 32767f).toInt().coerceIn(-32768, 32767))
        }
        tmp.renameTo(f)
    }

    private fun loadPool() {
        try {
            val p = SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build()
            p.setOnLoadCompleteListener { _, sid, st -> for (i in ids.indices) if (ids[i] == sid) ok[i] = st == 0 }
            for (i in PHRASES.indices) if (outFile(i).exists()) ids[i] = p.load(outFile(i).path, 1)
            pool = p
            ready = true
        } catch (e: Exception) { }
    }

    fun say(i: Int) {
        if (!enabled || !ready || i !in ids.indices || !ok[i]) return
        try { pool?.play(ids[i], 1f, 1f, 3, 0, 1f) } catch (e: Exception) { }
    }

    fun release() {
        try { pool?.release() } catch (e: Exception) { }
        try { tts?.shutdown() } catch (e: Exception) { }
    }
}
