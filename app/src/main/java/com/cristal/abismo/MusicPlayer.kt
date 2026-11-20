package com.cristal.abismo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Reproduce las dos pistas en bucle con fundido suave al cambiar de una a otra. */
class MusicPlayer(private val ctx: Context) {
    private class Voice(val mp: MediaPlayer) {
        var vol = 0f
        var target = 1f
    }

    private val tracks = intArrayOf(R.raw.menu_theme, R.raw.game_theme)
    private var cur: Voice? = null
    private val fading = ArrayList<Voice>()
    private var track = -1
    var enabled = true
    /** Atenuación global (por ejemplo, durante la pausa). */
    var duck = 1f

    private inline fun safe(block: () -> Unit) { try { block() } catch (e: Exception) { } }

    private fun startCurrent() {
        if (track < 0 || !enabled) return
        safe {
            val mp = MediaPlayer.create(ctx, tracks[track]) ?: return
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.start()
            cur = Voice(mp)
        }
    }

    private fun retireCurrent() {
        val c = cur ?: return
        c.target = 0f
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
            safe { it.mp.setVolume(it.vol * level, it.vol * level) }
        }
        val i = fading.iterator()
        while (i.hasNext()) {
            val v = i.next()
            v.vol -= dt / 1.2f
            if (v.vol <= 0f) {
                safe { v.mp.stop() }
                safe { v.mp.release() }
                i.remove()
            } else safe { v.mp.setVolume(v.vol * level, v.vol * level) }
        }
    }

    fun pause() {
        cur?.let { v -> safe { if (v.mp.isPlaying) v.mp.pause() } }
        for (v in fading) safe { if (v.mp.isPlaying) v.mp.pause() }
    }

    fun resume() {
        if (!enabled) return
        cur?.let { v -> safe { v.mp.start() } }
        for (v in fading) safe { v.mp.start() }
    }

    fun release() {
        cur?.let { v -> safe { v.mp.release() } }
        for (v in fading) safe { v.mp.release() }
        cur = null
        fading.clear()
    }

    private companion object {
        const val LEVEL = 0.55f
    }
}

/** Efecto de sonido muy leve al fijar una nave. */
class Sfx(ctx: Context) {
    var enabled = true
    private var loaded = false
    private val pool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
    } catch (e: Exception) { null }
    private var id = 0

    init {
        try {
            pool?.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
            id = pool?.load(ctx, R.raw.place, 1) ?: 0
        } catch (e: Exception) { }
    }

    fun place() {
        if (!enabled || !loaded) return
        try { pool?.play(id, 0.35f, 0.35f, 1, 0, 1f) } catch (e: Exception) { }
    }

    fun release() { try { pool?.release() } catch (e: Exception) { } }
}
