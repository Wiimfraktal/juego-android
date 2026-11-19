package com.cristal.abismo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView(ctx: Context) : View(ctx) {

    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val max: Float, val color: Int, val size: Float
    )

    private class Star(var ang: Float, var dist: Float, val z: Float)

    /** Trocito de hierro que vuela desde una nave hasta la barra. */
    private class Chip(
        val x0: Float, val y0: Float, val delay: Float, val dur: Float, val amount: Int, val side: Float
    ) { var t = 0f }

    private companion object {
        const val COLS = 23
        const val LIFT_LOCKED = 0.30f
        const val LIFT_PIECE = 0.55f
        const val EXPLOSION_TIME = 2.8f
        // color de facción de cada tipo de nave
        val BASE = intArrayOf(
            Color.rgb(0, 229, 255), Color.rgb(255, 196, 0), Color.rgb(176, 64, 255),
            Color.rgb(0, 255, 140), Color.rgb(255, 40, 150), Color.rgb(50, 110, 255),
            Color.rgb(255, 110, 20)
        )
    }

    private var game: Game? = null
    private var s = 0f      // tamaño de celda
    private var u = 0f      // unidad de interfaz (ancho / 15)
    private var ox = 0f
    private var oy = 0f
    private var ccx = 0f
    private var ccy = 0f
    private var planetR = 0f
    private var planetSc = 1.5f
    private var topInset = 0

    // fases: 0 = menú en paz, 1 = explosión, 2 = partida
    private var phase = 0
    private var phaseT = 0f

    @Volatile private var ships: Array<Bitmap>? = null
    @Volatile private var planetData: PlanetData? = null
    private var loading = false
    private var glows: Array<Bitmap>? = null
    private var glowFire: Bitmap? = null
    private var glowWhite: Bitmap? = null
    private var haze: Bitmap? = null
    private var bgShader: Shader? = null
    private var nebA: Shader? = null
    private var nebB: Shader? = null
    private var atmoShader: Shader? = null
    private val edgeShaders = arrayOfNulls<Shader>(4)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val hazePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val dark1 = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.30f, 0.30f, 0.36f, 1f) })
    }
    private val dark2 = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.55f, 0.55f, 0.62f, 1f) })
    }
    private val path = Path()
    private val rect = RectF()
    private val sparks = ArrayList<Spark>()
    private val stars = ArrayList<Star>()
    private val chips = ArrayList<Chip>()
    private var last = 0L
    private var time = 0f
    private var shake = 0f
    private var starSpeed = 0.12f
    private var alert = 0f
    private var flashWhite = 0f

    private val prefs = ctx.getSharedPreferences("abismo", Context.MODE_PRIVATE)
    private var totalIngots = try { prefs.getInt("ingots", 0) } catch (e: Exception) { 0 }
    private var best = try { prefs.getInt("best", 0) } catch (e: Exception) { 0 }
    private var newRecord = false
    private var overHandled = false
    private var seenIngots = 0
    private var seenBars = 0
    private var dispFrac = 0f
    private var barFlash = 0f
    private var chipTx = 0f
    private var chipTy = 0f

    // geometría del HUD
    private var barX0 = 0f
    private var barX1 = 0f
    private var barY0 = 0f
    private var barY1 = 0f
    private val pauseRect = RectF()
    private val resumeRect = RectF()
    private val escapeRect = RectF()
    private var paused = false

    // entrada
    private var sx = 0f
    private var sy = 0f
    private var lx = 0f
    private var ly = 0f
    private var t0 = 0L
    private var moved = false
    private var downOnPause = false

    fun pause() { if (phase == 2) paused = true }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var t = insets.systemWindowInsetTop
        if (Build.VERSION.SDK_INT >= 28) {
            val cut = insets.displayCutout
            if (cut != null) t = max(t, cut.safeInsetTop)
        }
        if (t != topInset) {
            topInset = t
            if (width > 0) layoutGame()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        layoutGame()
    }

    private fun layoutGame() {
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        s = w / COLS.toFloat()
        u = w / 15f
        val topPad = topInset + u * 2.7f
        val botPad = u * 0.9f
        var rows = ((h - topPad - botPad) / s).toInt()
        if (rows % 2 == 0) rows--
        val g = Game(COLS, rows)
        g.baseSpeed = w * 0.105f / s
        game = g
        ox = 0f
        oy = topPad + (h - topPad - botPad - rows * s) / 2f
        ccx = ox + (COLS / 2 + 0.5f) * s
        ccy = oy + (rows / 2 + 0.5f) * s
        planetR = w * 0.27f
        planetSc = 1.5f
        phase = 0; phaseT = 0f
        seenIngots = 0; seenBars = 0; dispFrac = 0f
        newRecord = false; overHandled = false
        paused = false
        alert = 0f; flashWhite = 0f
        chips.clear()

        val top = topInset + u * 0.95f
        barX0 = u * 0.4f
        barX1 = w - u * 0.4f
        barY0 = top + u * 0.35f
        barY1 = barY0 + u * 0.42f
        pauseRect.set(w / 2f - u * 0.7f, top - u * 0.75f, w / 2f + u * 0.7f, top + u * 0.3f)
        resumeRect.set(w / 2f - u * 3f, h / 2f + u * 0.2f, w / 2f + u * 3f, h / 2f + u * 1.6f)
        escapeRect.set(w / 2f - u * 3.6f, h * 0.80f, w / 2f + u * 3.6f, h * 0.80f + u * 1.6f)

        bgShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(3, 5, 18), Color.rgb(11, 7, 30), Color.rgb(3, 2, 10)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        nebA = RadialGradient(w * 0.2f, h * 0.22f, w * 0.9f,
            intArrayOf(Color.argb(60, 90, 30, 160), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        nebB = RadialGradient(w * 0.85f, h * 0.8f, w * 0.9f,
            intArrayOf(Color.argb(50, 20, 90, 170), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        atmoShader = RadialGradient(ccx, ccy, planetR * 1.5f,
            intArrayOf(Color.argb(0, 90, 160, 255), Color.argb(0, 90, 160, 255), Color.argb(200, 110, 175, 255),
                Color.argb(70, 70, 130, 255), Color.argb(0, 40, 90, 255)),
            floatArrayOf(0f, 0.655f, 0.68f, 0.8f, 1f), Shader.TileMode.CLAMP)
        // bordes de alerta (franjas estrechas rojas)
        val th = u * 1.1f
        val red = Color.argb(255, 255, 30, 40)
        val none = Color.argb(0, 255, 30, 40)
        edgeShaders[0] = LinearGradient(0f, 0f, 0f, th, red, none, Shader.TileMode.CLAMP)
        edgeShaders[1] = LinearGradient(0f, h.toFloat(), 0f, h - th, red, none, Shader.TileMode.CLAMP)
        edgeShaders[2] = LinearGradient(0f, 0f, th, 0f, red, none, Shader.TileMode.CLAMP)
        edgeShaders[3] = LinearGradient(w.toFloat(), 0f, w - th, 0f, red, none, Shader.TileMode.CLAMP)

        if (glows == null) {
            glows = Array(7) { Textures.makeGlow(BASE[it]) }
            glowFire = Textures.makeGlow(Color.rgb(255, 150, 50))
            glowWhite = Textures.makeGlow(Color.rgb(255, 240, 210))
        }
        if (haze == null) {
            val hw = 120
            val hh = (hw * h / w.toFloat()).toInt()
            haze = Textures.makeHaze(hw, hh)
        }
        if (!loading) {
            loading = true
            val pr = (planetR * 0.7f).toInt()
            Thread {
                ships = Textures.makeShipTiles(BASE)
                planetData = Textures.makePlanet(pr)
                postInvalidate()
            }.start()
        }

        stars.clear()
        val maxD = hypot(w.toFloat(), h.toFloat()) / 2f
        repeat(120) {
            stars.add(Star(Random.nextFloat() * 6.2832f, planetR + Random.nextFloat() * (maxD - planetR), 0.2f + Random.nextFloat() * 0.8f))
        }
        sparks.clear()
        last = 0L
    }

    private fun ready() = ships != null && planetData != null

    // ---------- color ----------
    private fun lighten(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
    )

    // ---------- ciclo ----------
    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val now = System.nanoTime()
        val dt = if (last == 0L || paused) 0f else min(0.05f, (now - last) / 1e9f)
        last = now
        time += dt

        if (phase == 1) {
            phaseT += dt
            if (phaseT >= EXPLOSION_TIME) { phase = 2; phaseT = 0f }
        } else phaseT += dt

        val near = g.nearestFilled()
        if (phase == 2 && !paused) {
            g.update(dt)
            consumeBursts(g)
            consumeIron(g)
            emitThrust(g)
            updateBar(g, dt)
            if (g.over && !overHandled) finishGame(g)
        }
        updateParticles(dt, g)

        // cámara del planeta (perspectiva): lejos de la amenaza se achica, cerca crece
        val target = when (phase) {
            0 -> 1.5f + 0.02f * sin(time * 0.6f)
            1 -> 1f + 0.5f * (1f - (phaseT / EXPLOSION_TIME).coerceIn(0f, 1f))
            else -> 1f + 0.22f * (1f - ((near - 1) / 8f)).coerceIn(0f, 1f)
        }
        planetSc += (target - planetSc) * min(1f, dt * 2.5f)

        // estrellas: rápidas y se calman a medida que las naves se acercan al centro
        val starTarget = when (phase) {
            0 -> 0.12f
            1 -> 0.12f + 0.88f * (phaseT / EXPLOSION_TIME).coerceIn(0f, 1f)
            else -> ((near - 1) / 8f).coerceIn(0.10f, 1f)
        }
        starSpeed += (starTarget - starSpeed) * min(1f, dt * 2f)

        // alerta roja en los bordes
        val alertT = if (phase != 2 || g.over) 0f else when {
            near <= 2 -> 1f
            near == 3 -> 0.7f
            near == 4 -> 0.4f
            else -> 0f
        }
        alert += (alertT - alert) * min(1f, dt * 3f)
        shake = max(0f, shake - dt * 2.5f)
        flashWhite = max(0f, flashWhite - dt * 1.6f)

        canvas.save()
        if (shake > 0f) canvas.translate((Random.nextFloat() - 0.5f) * shake * s * 0.35f, (Random.nextFloat() - 0.5f) * shake * s * 0.35f)
        drawSpace(canvas)
        drawPlanet(canvas)
        drawHaze(canvas)
        if (phase == 2) {
            drawDanger(canvas, near)
            drawShadow(canvas, g)
            drawLocked(canvas, g)
            drawPiece(canvas, g)
        }
        drawSparks(canvas)
        canvas.restore()
        if (alert > 0.01f) drawAlert(canvas)
        if (phase == 2) {
            drawChips(canvas)
            drawHud(canvas, g)
        } else drawMenu(canvas)
        if (flashWhite > 0f) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Color.argb((235 * min(1f, flashWhite)).toInt(), 255, 245, 225)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        if (paused) drawPause(canvas)
        postInvalidateOnAnimation()
    }

    private fun startEscape() {
        val pd = planetData ?: return
        if (ships == null) return
        phase = 1; phaseT = 0f
        shake = 1.4f
        flashWhite = 1.1f
        val ex = ccx + pd.cities[0] * planetR * planetSc
        val ey = ccy + pd.cities[1] * planetR * planetSc
        repeat(90) {
            val a = Random.nextFloat() * 6.283f
            val v = s * (3f + Random.nextFloat() * 13f)
            val col = when (Random.nextInt(3)) { 0 -> Color.rgb(255, 230, 160); 1 -> Color.rgb(255, 150, 50); else -> Color.rgb(255, 80, 40) }
            sparks.add(Spark(ex, ey, cos(a) * v, sin(a) * v, 1.5f, 1.5f, col, s * (0.12f + Random.nextFloat() * 0.2f)))
        }
    }

    private fun finishGame(g: Game) {
        overHandled = true
        if (g.score > best) {
            best = g.score
            newRecord = true
            try { prefs.edit().putInt("best", best).apply() } catch (e: Exception) { }
        }
    }

    private fun updateBar(g: Game, dt: Float) {
        if (g.ingots > seenIngots) {
            totalIngots += g.ingots - seenIngots
            seenIngots = g.ingots
            try { prefs.edit().putInt("ingots", totalIngots).apply() } catch (e: Exception) { }
        }
        if (g.barsFilled > seenBars) {
            seenBars = g.barsFilled
            barFlash = 1f
            dispFrac = 0f
        }
        val frac = g.barProgress / g.barNeed().toFloat()
        dispFrac += (frac - dispFrac) * min(1f, dt * 6f)
        barFlash = max(0f, barFlash - dt * 1.5f)
    }

    private fun consumeBursts(g: Game) {
        if (g.bursts.isEmpty()) return
        for (b in g.bursts) {
            val col = BASE[(b[2] - 1).coerceIn(0, 6)]
            val px = ox + (b[0] + 0.5f) * s
            val py = oy + (b[1] + 0.5f) * s
            repeat(6) {
                val a = Random.nextFloat() * 6.283f
                val v = s * (1.5f + Random.nextFloat() * 4f)
                sparks.add(Spark(px, py, cos(a) * v, sin(a) * v, 0.8f, 0.8f, lighten(col, 0.3f), s * (0.07f + Random.nextFloat() * 0.1f)))
            }
        }
        g.bursts.clear()
        shake = 1f
    }

    /** Convierte el hierro soltado por las naves en trocitos que vuelan hacia la barra. */
    private fun consumeIron(g: Game) {
        if (g.ironDrops.isEmpty()) return
        for (d in g.ironDrops) {
            chips.add(Chip(ox + (d[0] + 0.5f) * s, oy + (d[1] + 0.5f) * s, Random.nextFloat() * 0.25f,
                0.7f + Random.nextFloat() * 0.4f, d[2], if (Random.nextBoolean()) 1f else -1f))
        }
        g.ironDrops.clear()
    }

    /** Estela de motor detrás de cada nave (hacia el planeta). */
    private fun emitThrust(g: Game) {
        val p = g.piece ?: return
        for (c in p.cells) {
            var covered = false
            for (o in p.cells) if (o[0] == c[0] - g.gx && o[1] == c[1] - g.gy) covered = true
            if (covered || Random.nextFloat() > 0.5f) continue
            val cxp = ox + (p.fx + c[0] + 0.5f) * s - g.gx * s * 0.5f
            val cyp = oy + (p.fy + c[1] + 0.5f) * s - g.gy * s * 0.5f
            val col = if (Random.nextBoolean()) Color.rgb(255, 170, 60) else Color.rgb(255, 90, 40)
            sparks.add(Spark(cxp + (Random.nextFloat() - 0.5f) * s * 0.4f, cyp + (Random.nextFloat() - 0.5f) * s * 0.4f,
                -g.gx * s * (2.5f + Random.nextFloat() * 2f) + (Random.nextFloat() - 0.5f) * s,
                -g.gy * s * (2.5f + Random.nextFloat() * 2f) + (Random.nextFloat() - 0.5f) * s,
                0.35f, 0.35f, col, s * (0.08f + Random.nextFloat() * 0.08f)))
        }
    }

    private fun updateParticles(dt: Float, g: Game) {
        val it = sparks.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 0.96f; p.vy *= 0.96f
        }
        val maxD = hypot(width.toFloat(), height.toFloat()) / 2f + 20f
        // las estrellas convergen hacia el planeta: nos alejamos de él
        for (st in stars) {
            st.dist -= st.dist * (0.45f + 0.9f * st.z) * starSpeed * dt
            if (st.dist < planetR * 0.9f) { st.dist = maxD * (0.75f + Random.nextFloat() * 0.25f); st.ang = Random.nextFloat() * 6.2832f }
        }
        chipTx = barX0 + (barX1 - barX0) * dispFrac.coerceIn(0.03f, 1f)
        chipTy = (barY0 + barY1) / 2f
        val ci = chips.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            c.t += dt
            if (c.t >= c.delay + c.dur) {
                g.addIron(c.amount)
                ci.remove()
            }
        }
    }

    // ---------- dibujo ----------
    private fun drawSpace(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = bgShader
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = nebA
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = nebB
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.strokeCap = Paint.Cap.ROUND
        for (st in stars) {
            val fade = ((st.dist - planetR) / (s * 3f)).coerceIn(0f, 1f)
            val speedPx = st.dist * (0.45f + 0.9f * st.z) * starSpeed
            val len = 2f + speedPx * 0.055f
            val ca = cos(st.ang); val sa = sin(st.ang)
            paint.strokeWidth = 1f + 2f * st.z
            paint.color = Color.argb((fade * (70f + 185f * st.z)).toInt().coerceIn(0, 255), 200, 215, 255)
            c.drawLine(ccx + ca * st.dist, ccy + sa * st.dist, ccx + ca * (st.dist + len), ccy + sa * (st.dist + len), paint)
        }
    }

    private fun drawPlanet(c: Canvas) {
        val pd = planetData
        c.save()
        c.scale(planetSc, planetSc, ccx, ccy)
        paint.shader = atmoShader
        c.drawRect(ccx - planetR * 1.5f, ccy - planetR * 1.5f, ccx + planetR * 1.5f, ccy + planetR * 1.5f, paint)
        paint.shader = null
        if (pd != null) {
            rect.set(ccx - planetR, ccy - planetR, ccx + planetR, ccy + planetR)
            c.drawBitmap(pd.bmp, null, rect, bmpPaint)
            drawDisturbances(c, pd)
        }
        c.restore()
    }

    /** Ciudades con luces, destellos y fuego; en el menú todo está en calma. */
    private fun drawDisturbances(c: Canvas, pd: PlanetData) {
        val gf = glowFire ?: return
        val gw = glowWhite ?: return
        val n = pd.cities.size / 2
        val ex = ccx + pd.cities[0] * planetR
        val ey = ccy + pd.cities[1] * planetR
        if (phase == 0) return
        val intensity = if (phase == 1) (phaseT / 1.2f).coerceIn(0f, 1f) else 1f
        for (i in 1 until n) {
            val cxp = ccx + pd.cities[i * 2] * planetR
            val cyp = ccy + pd.cities[i * 2 + 1] * planetR
            val v = sin(time * (5f + (i % 5)) + i * 3.1f) * sin(time * 2.3f + i * 1.7f)
            val flash = ((v - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val base = 0.35f + 0.25f * sin(time * 9f + i * 2f)
            val rad = planetR * (0.025f + 0.05f * flash)
            rect.set(cxp - rad, cyp - rad, cxp + rad, cyp + rad)
            addPaint.alpha = ((60f * base + 255f * flash) * intensity).toInt().coerceIn(0, 255)
            c.drawBitmap(if (flash > 0.5f) gw else gf, null, rect, addPaint)
        }
        // epicentro: ciudad ardiendo
        val burn = 0.7f + 0.3f * sin(time * 11f)
        var rad = planetR * 0.13f * (0.6f + 0.4f * intensity)
        rect.set(ex - rad, ey - rad, ex + rad, ey + rad)
        addPaint.alpha = (200 * burn * intensity).toInt().coerceIn(0, 255)
        c.drawBitmap(gf, null, rect, addPaint)
        rad = planetR * 0.045f
        rect.set(ex - rad, ey - rad, ex + rad, ey + rad)
        addPaint.alpha = (255 * burn).toInt().coerceIn(0, 255)
        c.drawBitmap(gw, null, rect, addPaint)
        // explosión inicial: fogonazo, onda expansiva y humo
        if (phase == 1) {
            val t = phaseT
            val fl = (1f - t / 1.6f).coerceIn(0f, 1f)
            if (fl > 0f) {
                val r1 = planetR * (0.08f + 0.5f * (1f - exp2(-t * 5f)))
                rect.set(ex - r1, ey - r1, ex + r1, ey + r1)
                addPaint.alpha = (255 * fl).toInt()
                c.drawBitmap(gw, null, rect, addPaint)
                val r2 = r1 * 1.8f
                rect.set(ex - r2, ey - r2, ex + r2, ey + r2)
                addPaint.alpha = (190 * fl).toInt()
                c.drawBitmap(gf, null, rect, addPaint)
            }
            val ring = (t / 1.8f).coerceIn(0f, 1f)
            if (ring < 1f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = planetR * 0.03f * (1f - ring) + 2f
                paint.color = Color.argb((220 * (1f - ring)).toInt(), 255, 220, 170)
                c.drawCircle(ex, ey, planetR * 1.1f * ring, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun exp2(x: Float) = Math.exp(x.toDouble()).toFloat()

    /** Polvo estelar muy tenue, detrás de las naves para no estorbar. */
    private fun drawHaze(c: Canvas) {
        val hb = haze ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0..1) {
            val sc = if (i == 0) 1.5f else 2.1f
            val dx = sin(time * 0.05f * (i + 1) + i * 2f) * w * 0.12f
            val dy = cos(time * 0.04f * (i + 1)) * h * 0.08f
            val ex = w * (sc - 1f) / 2f
            val ey = h * (sc - 1f) / 2f
            rect.set(-ex + dx, -ey + dy, w + ex + dx, h + ey + dy)
            hazePaint.alpha = if (i == 0) 110 else 70
            c.drawBitmap(hb, null, rect, hazePaint)
        }
    }

    /** Pulso rojo estrecho en el contorno de la pantalla, como alarma de la nave. */
    private fun drawAlert(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val th = u * 1.1f
        val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * (6f + 5f * alert)))
        val a = (230f * alert * pulse).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.FILL
        paint.shader = edgeShaders[0]; paint.alpha = a; c.drawRect(0f, 0f, w, th, paint)
        paint.shader = edgeShaders[1]; paint.alpha = a; c.drawRect(0f, h - th, w, h, paint)
        paint.shader = edgeShaders[2]; paint.alpha = a; c.drawRect(0f, 0f, th, h, paint)
        paint.shader = edgeShaders[3]; paint.alpha = a; c.drawRect(w - th, 0f, w, h, paint)
        paint.shader = null
        paint.alpha = 255
    }

    private fun drawDanger(c: Canvas, near: Int) {
        val g = game ?: return
        val pulse = 0.5f + 0.5f * sin(time * (if (near <= 3) 9f else 3f))
        val alpha = if (near <= 3) (60 + 80 * pulse).toInt() else (14 + 12 * pulse).toInt()
        val x0 = ox + (g.cx - 1) * s
        val y0 = oy + (g.cy - 1) * s
        val x1 = ox + (g.cx + 2) * s
        val y1 = oy + (g.cy + 2) * s
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 255, 50, 60)
        c.drawRoundRect(x0, y0, x1, y1, s * 0.3f, s * 0.3f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(min(255, alpha + 60), 255, 110, 120)
        c.drawRoundRect(x0, y0, x1, y1, s * 0.3f, s * 0.3f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawShadow(c: Canvas, g: Game) {
        val p = g.piece ?: return
        paint.color = Color.argb(100, 0, 0, 0)
        val o = s * 0.14f
        for (cell in p.cells) {
            val x = ox + (p.fx + cell[0]) * s + o
            val y = oy + (p.fy + cell[1]) * s + o
            c.drawRoundRect(x, y, x + s, y + s, s * 0.25f, s * 0.25f, paint)
        }
    }

    private fun drawLocked(c: Canvas, g: Game) {
        val n = g.rows * g.cols
        val xs = IntArray(n); val ys = IntArray(n); val ts = IntArray(n); val ms = IntArray(n)
        var k = 0
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val v = g.grid[y][x]
            if (v == 0) continue
            xs[k] = x; ys[k] = y; ts[k] = v - 1
            var m = 0
            if (y > 0 && g.grid[y - 1][x] == v) m = m or 1
            if (x < g.cols - 1 && g.grid[y][x + 1] == v) m = m or 2
            if (y < g.rows - 1 && g.grid[y + 1][x] == v) m = m or 4
            if (x > 0 && g.grid[y][x - 1] == v) m = m or 8
            ms[k] = m
            k++
        }
        drawShips(c, xs, ys, ts, ms, k, 0f, 0f, LIFT_LOCKED, 70)
    }

    private fun drawPiece(c: Canvas, g: Game) {
        val p = g.piece ?: return
        val bx = floor(p.fx).toInt()
        val by = floor(p.fy).toInt()
        val offX = p.fx - bx
        val offY = p.fy - by
        val xs = IntArray(4) { bx + p.cells[it][0] }
        val ys = IntArray(4) { by + p.cells[it][1] }
        val ts = IntArray(4) { p.type }
        val ms = IntArray(4) { i ->
            var m = 0
            for (j in 0 until 4) {
                if (xs[j] == xs[i] && ys[j] == ys[i] - 1) m = m or 1
                if (xs[j] == xs[i] + 1 && ys[j] == ys[i]) m = m or 2
                if (xs[j] == xs[i] && ys[j] == ys[i] + 1) m = m or 4
                if (xs[j] == xs[i] - 1 && ys[j] == ys[i]) m = m or 8
            }
            m
        }
        // flecha que indica hacia dónde avanza la nave
        var avgx = 0f; var avgy = 0f
        for (cell in p.cells) { avgx += cell[0]; avgy += cell[1] }
        val mx = p.fx + avgx / 4f + 0.5f + g.gx * 2.8f
        val my = p.fy + avgy / 4f + 0.5f + g.gy * 2.8f
        val ax = ox + mx * s
        val ay = oy + my * s
        paint.color = Color.argb(150, 255, 255, 255)
        path.reset()
        val a = s * 0.4f
        path.moveTo(ax + g.gx * a, ay + g.gy * a)
        path.lineTo(ax - g.gx * a + g.gy * a, ay - g.gy * a + g.gx * a)
        path.lineTo(ax - g.gx * a - g.gy * a, ay - g.gy * a - g.gx * a)
        path.close()
        c.drawPath(path, paint)
        drawShips(c, xs, ys, ts, ms, 4, offX, offY, LIFT_PIECE, 190)
    }

    private fun proj(v: Float, center: Float, lift: Float) =
        v + lift * s * ((v - center) / (4f * s)).coerceIn(-1f, 1f)

    /** Tres capas por celda: casco inferior oscuro, capa intermedia y cubierta; el relieve crece hacia afuera. */
    private fun drawShips(
        c: Canvas, xs: IntArray, ys: IntArray, ts: IntArray, ms: IntArray, n: Int,
        offX: Float, offY: Float, lift: Float, glowA: Int
    ) {
        val tl = ships ?: return
        val gl = glows ?: return
        for (layer in 0..2) {
            val f = when (layer) { 0 -> 0f; 1 -> lift * 0.5f; else -> lift }
            val pt = when (layer) { 0 -> dark1; 1 -> dark2; else -> bmpPaint }
            for (i in 0 until n) {
                val x0 = ox + (xs[i] + offX) * s
                val y0 = oy + (ys[i] + offY) * s
                rect.set(proj(x0, ccx, f), proj(y0, ccy, f), proj(x0 + s, ccx, f), proj(y0 + s, ccy, f))
                c.drawBitmap(tl[ts[i] * 16 + ms[i]], null, rect, pt)
            }
        }
        for (i in 0 until n) {
            val x0 = ox + (xs[i] + offX) * s
            val y0 = oy + (ys[i] + offY) * s
            val tx0 = proj(x0, ccx, lift); val tx1 = proj(x0 + s, ccx, lift)
            val ty0 = proj(y0, ccy, lift); val ty1 = proj(y0 + s, ccy, lift)
            val pulse = 0.75f + 0.25f * sin(time * 3f + xs[i] * 1.3f + ys[i] * 0.7f)
            val gcx = (tx0 + tx1) / 2f; val gcy = (ty0 + ty1) / 2f
            val gr = s * 1.3f
            rect.set(gcx - gr, gcy - gr, gcx + gr, gcy + gr)
            addPaint.alpha = (glowA * 0.35f * pulse).toInt()
            c.drawBitmap(gl[ts[i]], null, rect, addPaint)
        }
    }

    private fun drawSparks(c: Canvas) {
        paint.style = Paint.Style.FILL
        for (p in sparks) {
            val f = p.life / p.max
            paint.color = Color.argb((255 * f).toInt(), Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            val r = p.size * (0.5f + f)
            c.drawRect(p.x - r, p.y - r, p.x + r, p.y + r, paint)
        }
    }

    private fun drawChips(c: Canvas) {
        paint.style = Paint.Style.FILL
        for (ch in chips) {
            val p = ch.t - ch.delay
            val sz = u * 0.17f
            if (p < 0f) {
                paint.color = Color.argb(220, 205, 220, 240)
                c.drawRect(ch.x0 - sz, ch.y0 - sz, ch.x0 + sz, ch.y0 + sz, paint)
                continue
            }
            val f0 = (p / ch.dur).coerceIn(0f, 1f)
            val f = f0 * f0 * (3f - 2f * f0)
            val x = ch.x0 + (chipTx - ch.x0) * f + sin(f * PI.toFloat()) * u * 2.2f * ch.side
            val y = ch.y0 + (chipTy - ch.y0) * f
            paint.color = Color.argb(70, 150, 190, 255)
            c.drawCircle(x, y, sz * 2.2f, paint)
            paint.color = Color.rgb(215, 228, 245)
            c.save()
            c.rotate(45f + f * 360f, x, y)
            c.drawRect(x - sz, y - sz, x + sz, y + sz, paint)
            c.restore()
        }
    }

    private fun drawIngot(c: Canvas, cx: Float, cy: Float, w: Float) {
        val h = w * 0.55f
        val topW = w * 0.72f
        path.reset()
        path.moveTo(cx - topW / 2f, cy - h / 2f)
        path.lineTo(cx + topW / 2f, cy - h / 2f)
        path.lineTo(cx + w / 2f, cy + h / 2f)
        path.lineTo(cx - w / 2f, cy + h / 2f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(cx, cy - h / 2f, cx, cy + h / 2f,
            intArrayOf(Color.rgb(225, 232, 245), Color.rgb(130, 145, 170), Color.rgb(70, 82, 105)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, paint)
        paint.shader = null
        paint.color = Color.argb(200, 255, 255, 255)
        c.drawRect(cx - topW * 0.38f, cy - h * 0.36f, cx + topW * 0.2f, cy - h * 0.2f, paint)
    }

    private fun drawMenu(c: Canvas) {
        val a = if (phase == 1) (1f - phaseT / 0.7f).coerceIn(0f, 1f) else 1f
        if (a <= 0f) return
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
        paint.textSize = u * 1.15f
        c.drawText("FUGA ORBITAL", width / 2f, topInset + u * 2.4f, paint)
        paint.textSize = u * 0.42f
        paint.color = Color.argb((200 * a).toInt(), 190, 215, 255)
        c.drawText("Un planeta en calma... por ahora", width / 2f, topInset + u * 3.2f, paint)
        paint.textSize = u * 0.42f
        paint.color = Color.argb((220 * a).toInt(), 255, 255, 255)
        c.drawText("Récord: $best      Lingotes: $totalIngots", width / 2f, escapeRect.top - u * 0.7f, paint)
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        val ok = ready()
        paint.color = if (ok) Color.argb((255 * a).toInt(), 200 + (40 * pulse).toInt(), 40, 40) else Color.argb((200 * a).toInt(), 60, 70, 100)
        c.drawRoundRect(escapeRect, u * 0.5f, u * 0.5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb((255 * a).toInt(), 255, 170, 150)
        c.drawRoundRect(escapeRect, u * 0.5f, u * 0.5f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
        paint.textSize = u * 0.8f
        c.drawText(if (ok) "ESCAPAR" else "Cargando...", width / 2f, escapeRect.centerY() + u * 0.28f, paint)
    }

    private fun drawHud(c: Canvas, g: Game) {
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.style = Paint.Style.FILL
        val top = topInset + u * 0.95f
        paint.textSize = u * 0.5f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.argb(235, 255, 255, 255)
        c.drawText("NIVEL ${g.level}", u * 0.4f, top, paint)
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("$totalIngots", width - u * 0.4f, top, paint)
        val tw = paint.measureText("$totalIngots")
        drawIngot(c, width - u * 0.4f - tw - u * 0.5f, top - u * 0.17f, u * 0.7f)

        // botón de pausa
        paint.color = Color.argb(150, 10, 12, 30)
        c.drawCircle(pauseRect.centerX(), top - u * 0.2f, u * 0.42f, paint)
        paint.color = Color.WHITE
        c.drawRect(pauseRect.centerX() - u * 0.17f, top - u * 0.38f, pauseRect.centerX() - u * 0.06f, top - u * 0.02f, paint)
        c.drawRect(pauseRect.centerX() + u * 0.06f, top - u * 0.38f, pauseRect.centerX() + u * 0.17f, top - u * 0.02f, paint)

        // barra de hierro
        rect.set(barX0, barY0, barX1, barY1)
        paint.color = Color.argb(150, 10, 12, 30)
        c.drawRoundRect(rect, u * 0.21f, u * 0.21f, paint)
        val fw = (barX1 - barX0) * dispFrac.coerceIn(0f, 1f)
        if (fw > u * 0.2f) {
            rect.set(barX0, barY0, barX0 + fw, barY1)
            paint.shader = LinearGradient(barX0, 0f, barX1, 0f,
                intArrayOf(Color.rgb(90, 110, 140), Color.rgb(170, 190, 215), Color.rgb(255, 235, 150)),
                null, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, u * 0.21f, u * 0.21f, paint)
            paint.shader = null
            val sh = ((time * 0.5f) % 1f) * (barX1 - barX0 + u * 2f) - u
            rect.set(max(barX0, sh), barY0, min(barX0 + fw, sh + u), barY1)
            if (rect.width() > 1f) {
                paint.color = Color.argb(110, 255, 255, 255)
                c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
            }
        }
        if (barFlash > 0f) {
            rect.set(barX0, barY0, barX1, barY1)
            paint.color = Color.argb((200 * barFlash).toInt(), 255, 255, 255)
            c.drawRoundRect(rect, u * 0.21f, u * 0.21f, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 0.5f
            paint.color = Color.argb((255 * barFlash).toInt(), 255, 240, 160)
            c.drawText("+3 lingotes", width / 2f, barY1 + u * 0.8f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(140, 170, 200, 255)
        rect.set(barX0, barY0, barX1, barY1)
        c.drawRoundRect(rect, u * 0.21f, u * 0.21f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = u * 0.3f
        paint.color = Color.argb(230, 255, 255, 255)
        c.drawText("hierro ${g.barProgress} / ${g.barNeed()}", width / 2f, barY1 - u * 0.1f, paint)

        if (phaseT < 7f && !g.over && !paused) {
            paint.textSize = u * 0.42f
            paint.color = Color.argb((255 * min(1f, (7f - phaseT))).toInt(), 255, 255, 255)
            c.drawText("Arrastra: mueve la nave en libertad", width / 2f, height - u * 1.8f, paint)
            c.drawText("Toca: gira   ·   Empuja rápido: la lanzas lejos", width / 2f, height - u * 1.2f, paint)
        }
        if (g.over) {
            paint.color = Color.argb(min(200, (g.overTime * 400).toInt()), 8, 0, 20)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 1.0f
            paint.color = Color.rgb(255, 80, 80)
            c.drawText("ESCAPE FALLIDO", width / 2f, height / 2f - u * 1.6f, paint)
            paint.color = Color.WHITE
            paint.textSize = u * 0.6f
            c.drawText("Puntos: ${g.score}", width / 2f, height / 2f - u * 0.4f, paint)
            paint.color = if (newRecord) Color.rgb(255, 225, 100) else Color.argb(220, 255, 255, 255)
            c.drawText(if (newRecord) "¡Nuevo récord! $best" else "Récord: $best", width / 2f, height / 2f + u * 0.5f, paint)
            paint.color = Color.WHITE
            paint.textSize = u * 0.45f
            c.drawText("Toca para volver", width / 2f, height / 2f + u * 1.7f, paint)
        }
    }

    private fun drawPause(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(248, 4, 6, 16)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE
        paint.textSize = u * 1.1f
        c.drawText("PAUSA", width / 2f, height / 2f - u * 1.0f, paint)
        paint.color = Color.argb(255, 40, 60, 110)
        c.drawRoundRect(resumeRect, u * 0.4f, u * 0.4f, paint)
        paint.color = Color.WHITE
        paint.textSize = u * 0.6f
        c.drawText("Continuar", width / 2f, resumeRect.centerY() + u * 0.22f, paint)
    }

    // ---------- entrada ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.x; sy = e.y; lx = e.x; ly = e.y
                t0 = System.currentTimeMillis(); moved = false
                downOnPause = phase == 2 && pauseRect.contains(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (phase != 2 || paused || downOnPause || g.over) { lx = e.x; ly = e.y; return true }
                // arrastre libre: la nave sigue al dedo en coordenadas continuas
                g.drag((e.x - lx) / s, (e.y - ly) / s)
                lx = e.x; ly = e.y
                if (hypot(e.x - sx, e.y - sy) > s * 0.4f) moved = true
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - t0
                val tx = e.x - sx
                val ty = e.y - sy
                val dist = hypot(tx, ty)
                if (phase == 0) {
                    if (escapeRect.contains(sx, sy) && escapeRect.contains(e.x, e.y) && ready()) startEscape()
                } else if (phase == 1) {
                    // la explosión no se interrumpe
                } else if (paused) {
                    if (resumeRect.contains(e.x, e.y) || pauseRect.contains(e.x, e.y)) { paused = false; last = 0L }
                } else if (downOnPause && pauseRect.contains(e.x, e.y)) {
                    paused = true
                } else if (g.over) {
                    if (g.overTime > 0.8f) layoutGame()
                } else if (!moved && dist < s * 0.6f && dt < 300) {
                    g.rotate()
                } else if (dt < 180 && dist > s * 3f && (tx * g.gx + ty * g.gy) > 0.7f * dist) {
                    g.hardDrop()
                }
            }
        }
        return true
    }
}
