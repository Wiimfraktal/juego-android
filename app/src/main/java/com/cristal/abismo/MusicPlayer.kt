package com.cristal.abismo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Música en bucle sin cortes: cada pista usa dos reproductores encadenados (setNextMediaPlayer), así al terminar
 * la canción la siguiente vuelta empieza sin pausa ni tirón. Todo lo pesado (crear y preparar) va en un hilo
 * aparte; el hilo del juego solo ajusta volúmenes, y únicamente cuando cambian.
 */
class MusicPlayer(private val ctx: Context) {
    private inner class Voice(val res: Int) {
        @Volatile var mp: MediaPlayer? = null
        @Volatile var next: MediaPlayer? = null
        var vol = 0f
        var applied = -1f
        @Volatile var dead = false

        fun start() = worker.post {
            val m = create() ?: return@post
            if (dead) { safe { m.release() }; return@post }
            safe { m.setVolume(0f, 0f); m.start() }
            mp = m
            chain(m)
        }

        private fun create(): MediaPlayer? = try { MediaPlayer.create(ctx, res) } catch (e: Exception) { null }

        /** Prepara la siguiente vuelta y la encadena para que empiece justo al terminar la actual. */
        private fun chain(m: MediaPlayer) {
            val n = create() ?: run { safe { m.isLooping = true }; return }
            if (dead) { safe { n.release() }; return }
            safe { n.setVolume(applied.coerceAtLeast(0f), applied.coerceAtLeast(0f)) }
            next = n
            safe { m.setNextMediaPlayer(n) }
            m.setOnCompletionListener { old ->
                worker.post {
                    mp = n; next = null
                    safe { old.release() }
                    if (!dead) chain(n) else safe { n.release() }
                }
            }
        }

        fun applyVol(v: Float) {
            if (kotlin.math.abs(v - applied) < 0.004f) return
            applied = v
            safe { mp?.setVolume(v, v) }
            safe { next?.setVolume(v, v) }
        }

        fun kill() {
            dead = true
            worker.post { safe { mp?.stop() }; safe { mp?.release() }; safe { next?.release() }; mp = null; next = null }
        }
    }

    private val thread = android.os.HandlerThread("musica").apply { start() }
    private val worker = android.os.Handler(thread.looper)
    private val tracks = intArrayOf(R.raw.menu_theme, R.raw.game_theme, R.raw.theme_tension, R.raw.theme_rock, R.raw.theme_heroic,
        R.raw.theme_neutron, R.raw.theme_duro, R.raw.theme_coro, R.raw.theme_regio)
    private var cur: Voice? = null
    private val fading = ArrayList<Voice>()
    private var track = -1
    var enabled = true
    /** Atenuación global (por ejemplo, durante la pausa). */
    var duck = 1f

    private inline fun safe(block: () -> Unit) { try { block() } catch (e: Exception) { } }

    private fun startCurrent() {
        if (track < 0 || !enabled) return
        cur = Voice(tracks[track]).also { it.start() }
    }

    private fun retireCurrent() {
        val c = cur ?: return
        fading.add(c)
        cur = null
    }

    fun play(t: Int) {
        if (t == track) return
        track = t
        retireCurrent()
        startCurrent()
    }

    fun turn(on: Boolean) {
        enabled = on
        if (!on) retireCurrent() else if (cur == null) startCurrent()
    }

    fun update(dt: Float) {
        val level = LEVEL * duck
        cur?.let {
            it.vol = (it.vol + dt / 1.8f).coerceAtMost(1f)
            it.applyVol(it.vol * level)
        }
        val i = fading.iterator()
        while (i.hasNext()) {
            val v = i.next()
            v.vol -= dt / 1.2f
            if (v.vol <= 0f) { v.kill(); i.remove() } else v.applyVol(v.vol * level)
        }
    }

    fun pause() {
        val all = listOfNotNull(cur) + fading
        worker.post { for (v in all) safe { v.mp?.let { if (it.isPlaying) it.pause() } } }
    }

    fun resume() {
        if (!enabled) return
        val all = listOfNotNull(cur) + fading
        worker.post { for (v in all) safe { v.mp?.start() } }
    }

    fun release() {
        cur?.kill()
        for (v in fading) v.kill()
        cur = null
        fading.clear()
        worker.post { thread.quitSafely() }
    }

    private companion object {
        const val LEVEL = 0.55f
    }
}

/** Efectos de sonido muy leves al fijar una nave; cada facción usa el suyo. */
class Sfx(ctx: Context) {
    var enabled = true
    private val pool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
    } catch (e: Exception) { null }
    private val ids = IntArray(10)
    private val ok = BooleanArray(10)

    init {
        try {
            pool?.setOnLoadCompleteListener { _, sampleId, status ->
                for (i in ids.indices) if (ids[i] == sampleId) ok[i] = status == 0
            }
            val raws = intArrayOf(R.raw.place_0, R.raw.place_1, R.raw.place_2, R.raw.place_3, R.raw.place_4, R.raw.place_5, R.raw.place_6, R.raw.bar_solo, R.raw.boom, R.raw.intro_boom)
            for (i in raws.indices) ids[i] = pool?.load(ctx, raws[i], 1) ?: 0
        } catch (e: Exception) { }
    }

    fun place(kind: Int) {
        val k = kind.coerceIn(0, 6)
        if (!enabled || !ok[k]) return
        try { pool?.play(ids[k], 0.35f, 0.35f, 1, 0, 1f) } catch (e: Exception) { }
    }

    /** Solo de guitarra al completar una barra (depende del interruptor de música). */
    fun solo() {
        if (!ok[7]) return
        try { pool?.play(ids[7], 0.85f, 0.85f, 2, 0, 1f) } catch (e: Exception) { }
    }

    /** Estruendo al completar una línea. */
    fun boom() {
        if (!enabled || !ok[8]) return
        try { pool?.play(ids[8], 0.7f, 0.7f, 2, 0, 1f) } catch (e: Exception) { }
    }

    /** Explosión masiva del planeta al iniciar la partida (más fuerte). */
    fun bigBoom() {
        if (!enabled || !ok[9]) return
        try { pool?.play(ids[9], 1f, 1f, 3, 0, 1f) } catch (e: Exception) { }
    }

    fun release() { try { pool?.release() } catch (e: Exception) { } }
}
