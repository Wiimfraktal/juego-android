package com.cristal.abismo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
        val x0: Float, val y0: Float, var delay: Float, val dur: Float, val amount: Int, val side: Float
    ) { var t = 0f }

    private companion object {
        const val COLS = 19
        const val LIFT_LOCKED = 0.30f
        const val LIFT_PIECE = 0.55f
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
    private var planetSc = 1f
    private var topInset = 0

    private var tiles: Array<Bitmap>? = null
    private var glows: Array<Bitmap>? = null
    private var haze: Bitmap? = null
    @Volatile private var planet: Bitmap? = null
    private var planetBuiltFor = 0
    private var bgShader: Shader? = null
    private var nebA: Shader? = null
    private var nebB: Shader? = null
    private var atmoShader: Shader? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val hazePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val path = Path()
    private val rect = RectF()
    private val sparks = ArrayList<Spark>()
    private val stars = ArrayList<Star>()
    private val chips = ArrayList<Chip>()
    private var last = 0L
    private var time = 0f
    private var shake = 0f

    private val prefs = ctx.getSharedPreferences("abismo", Context.MODE_PRIVATE)
    private var totalIngots = try { prefs.getInt("ingots", 0) } catch (e: Exception) { 0 }
    private var best = try { prefs.getInt("best", 0) } catch (e: Exception) { 0 }
    private var newRecord = false
    private var overHandled = false
    private var seenIngots = 0
    private var seenBars = 0
    private var dispFrac = 0f
    private var barFlash = 0f

    // geometría del HUD
    private var barX0 = 0f
    private var barX1 = 0f
    private var barY0 = 0f
    private var barY1 = 0f
    private val pauseRect = RectF()
    private val resumeRect = RectF()
    private var paused = false

    // entrada
    private var sx = 0f
    private var sy = 0f
    private var lx = 0f
    private var ly = 0f
    private var accX = 0f
    private var accY = 0f
    private var t0 = 0L
    private var moved = false
    private var downOnPause = false

    fun pause() { paused = true }

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
        game = Game(COLS, rows)
        ox = 0f
        oy = topPad + (h - topPad - botPad - rows * s) / 2f
        ccx = ox + (COLS / 2 + 0.5f) * s
        ccy = oy + (rows / 2 + 0.5f) * s
        planetR = w * 0.27f
        planetSc = 1f
        seenIngots = 0; seenBars = 0; dispFrac = 0f
        newRecord = false; overHandled = false
        paused = false
        chips.clear()

        val top = topInset + u * 0.95f
        barX0 = u * 0.4f
        barX1 = w - u * 0.4f
        barY0 = top + u * 0.35f
        barY1 = barY0 + u * 0.42f
        pauseRect.set(w / 2f - u * 0.7f, top - u * 0.75f, w / 2f + u * 0.7f, top + u * 0.3f)
        resumeRect.set(w / 2f - u * 3f, h / 2f + u * 0.2f, w / 2f + u * 3f, h / 2f + u * 1.6f)

        bgShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(3, 5, 18), Color.rgb(11, 7, 30), Color.rgb(3, 2, 10)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        nebA = RadialGradient(w * 0.2f, h * 0.22f, w * 0.9f,
            intArrayOf(Color.argb(60, 90, 30, 160), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        nebB = RadialGradient(w * 0.85f, h * 0.8f, w * 0.9f,
            intArrayOf(Color.argb(50, 20, 90, 170), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        // atmósfera: resplandor azul justo en el borde del planeta que se difumina hacia afuera
        atmoShader = RadialGradient(ccx, ccy, planetR * 1.5f,
            intArrayOf(Color.argb(0, 90, 160, 255), Color.argb(0, 90, 160, 255), Color.argb(200, 110, 175, 255),
                Color.argb(70, 70, 130, 255), Color.argb(0, 40, 90, 255)),
            floatArrayOf(0f, 0.655f, 0.68f, 0.8f, 1f), Shader.TileMode.CLAMP)

        if (tiles == null) {
            tiles = Array(7) { Textures.makeTile(BASE[it], it * 17 + 3) }
            glows = Array(7) { Textures.makeGlow(BASE[it]) }
        }
        if (haze == null) {
            val hw = 120
            val hh = (hw * h / w.toFloat()).toInt()
            haze = Textures.makeHaze(hw, hh)
        }
        val pr = (planetR * 0.7f).toInt()
        if (planetBuiltFor != pr) {
            planetBuiltFor = pr
            Thread {
                val b = Textures.makePlanet(pr)
                planet = b
                postInvalidate()
            }.start()
        }

        stars.clear()
        val maxD = hypot(w.toFloat(), h.toFloat()) / 2f
        repeat(110) {
            stars.add(Star(Random.nextFloat() * 6.2832f, planetR + Random.nextFloat() * (maxD - planetR), 0.2f + Random.nextFloat() * 0.8f))
        }
        sparks.clear()
        last = 0L
    }

    // ---------- color ----------
    private fun lighten(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
    )

    private fun shade(c: Int, f: Float) = Color.rgb(
        (Color.red(c) * f).toInt().coerceIn(0, 255),
        (Color.green(c) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) * f).toInt().coerceIn(0, 255)
    )

    // ---------- ciclo ----------
    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val now = System.nanoTime()
        val dt = if (last == 0L || paused) 0f else min(0.05f, (now - last) / 1e9f)
        last = now
        time += dt
        if (!paused) {
            g.update(dt)
            consumeBursts(g)
            consumeIron(g)
            emitThrust(g)
            updateParticles(dt, g)
            updateBar(g, dt)
            if (g.over && !overHandled) finishGame(g)
        }
        val near = g.nearestFilled()
        val target = 1f + 0.22f * (1f - ((near - 1) / 8f)).coerceIn(0f, 1f)
        planetSc += (target - planetSc) * min(1f, dt * 2f)
        shake = max(0f, shake - dt * 2.5f)

        canvas.save()
        if (shake > 0f) canvas.translate((Random.nextFloat() - 0.5f) * shake * s * 0.3f, (Random.nextFloat() - 0.5f) * shake * s * 0.3f)
        drawSpace(canvas)
        drawPlanet(canvas)
        drawHaze(canvas)
        drawDanger(canvas, near)
        drawShadow(canvas, g)
        drawLocked(canvas, g)
        drawPiece(canvas, g)
        drawSparks(canvas)
        canvas.restore()
        drawChips(canvas)
        drawHud(canvas, g)
        if (paused) drawPause(canvas)
        postInvalidateOnAnimation()
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
            val x = p.px + c[0]; val y = p.py + c[1]
            val bx = x - g.gx; val by = y - g.gy
            var covered = false
            for (o in p.cells) if (p.px + o[0] == bx && p.py + o[1] == by) covered = true
            if (covered || Random.nextFloat() > 0.55f) continue
            val cxp = ox + (x + g.visX + 0.5f) * s - g.gx * s * 0.5f
            val cyp = oy + (y + g.visY + 0.5f) * s - g.gy * s * 0.5f
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
        // las estrellas se acercan al planeta: nos alejamos de él
        for (st in stars) {
            st.dist -= st.dist * (0.10f + 0.28f * st.z) * dt
            if (st.dist < planetR * 0.9f) { st.dist = maxD * (0.75f + Random.nextFloat() * 0.25f); st.ang = Random.nextFloat() * 6.2832f }
        }
        // hierro en vuelo
        val tx = barX0 + (barX1 - barX0) * dispFrac.coerceIn(0.03f, 1f)
        val ty = (barY0 + barY1) / 2f
        val ci = chips.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            c.t += dt
            if (c.t >= c.delay + c.dur) {
                g.addIron(c.amount)
                ci.remove()
            }
        }
        chipTx = tx; chipTy = ty
    }

    private var chipTx = 0f
    private var chipTy = 0f

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
        // estrellas que convergen hacia el planeta; la estela queda del lado de afuera
        for (st in stars) {
            val fade = ((st.dist - planetR) / (s * 3f)).coerceIn(0f, 1f)
            val len = st.dist * (0.03f + 0.06f * st.z)
            val ca = cos(st.ang); val sa = sin(st.ang)
            paint.strokeWidth = 1f + 2f * st.z
            paint.color = Color.argb((fade * (60f + 190f * st.z)).toInt().coerceIn(0, 255), 200, 215, 255)
            c.drawLine(ccx + ca * st.dist, ccy + sa * st.dist, ccx + ca * (st.dist + len), ccy + sa * (st.dist + len), paint)
        }
    }

    private fun drawPlanet(c: Canvas) {
        c.save()
        c.scale(planetSc, planetSc, ccx, ccy)
        paint.shader = atmoShader
        c.drawRect(ccx - planetR * 1.5f, ccy - planetR * 1.5f, ccx + planetR * 1.5f, ccy + planetR * 1.5f, paint)
        paint.shader = null
        val pb = planet
        if (pb != null) {
            rect.set(ccx - planetR, ccy - planetR, ccx + planetR, ccy + planetR)
            c.drawBitmap(pb, null, rect, bmpPaint)
        }
        c.restore()
    }

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
        paint.color = Color.argb(110, 0, 0, 0)
        val o = s * 0.14f
        for (cell in p.cells) {
            val x = ox + (p.px + cell[0] + g.visX) * s + o
            val y = oy + (p.py + cell[1] + g.visY) * s + o
            c.drawRect(x, y, x + s, y + s, paint)
        }
    }

    private fun drawLocked(c: Canvas, g: Game) {
        val n = g.rows * g.cols
        val xs = IntArray(n); val ys = IntArray(n); val ts = IntArray(n)
        var k = 0
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            if (g.grid[y][x] != 0) { xs[k] = x; ys[k] = y; ts[k] = g.grid[y][x] - 1; k++ }
        }
        val solid = { x: Int, y: Int -> x in 0 until g.cols && y in 0 until g.rows && g.grid[y][x] != 0 }
        drawLayer(c, g, xs, ys, ts, k, 0f, 0f, LIFT_LOCKED, 70, solid)
    }

    private fun drawPiece(c: Canvas, g: Game) {
        val p = g.piece ?: return
        val xs = IntArray(4) { p.px + p.cells[it][0] }
        val ys = IntArray(4) { p.py + p.cells[it][1] }
        val ts = IntArray(4) { p.type }
        val solid = { x: Int, y: Int -> (0 until 4).any { xs[it] == x && ys[it] == y } }
        // flecha que indica hacia dónde avanza la nave
        val mx = xs.average().toFloat() + 0.5f + g.gx * 2.6f
        val my = ys.average().toFloat() + 0.5f + g.gy * 2.6f
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
        drawLayer(c, g, xs, ys, ts, 4, g.visX, g.visY, LIFT_PIECE, 190, solid)
    }

    private fun proj(v: Float, center: Float, lift: Float) =
        v + lift * s * ((v - center) / (4f * s)).coerceIn(-1f, 1f)

    /** Dibuja las paredes laterales y luego las tapas texturizadas; el relieve crece hacia afuera. */
    private fun drawLayer(
        c: Canvas, g: Game, xs: IntArray, ys: IntArray, ts: IntArray, n: Int,
        fx: Float, fy: Float, lift: Float, glowA: Int, solid: (Int, Int) -> Boolean
    ) {
        val tl = tiles ?: return
        val gl = glows ?: return
        paint.style = Paint.Style.FILL
        for (i in 0 until n) {
            val gx = xs[i]; val gy = ys[i]
            val x0 = ox + (gx + fx) * s; val x1 = x0 + s
            val y0 = oy + (gy + fy) * s; val y1 = y0 + s
            val tx0 = proj(x0, ccx, lift); val tx1 = proj(x1, ccx, lift)
            val ty0 = proj(y0, ccy, lift); val ty1 = proj(y1, ccy, lift)
            val col = BASE[ts[i]]
            if (gx > g.cx && !solid(gx - 1, gy)) quad(c, x0, y0, x0, y1, tx0, ty1, tx0, ty0, shade(col, 0.40f))
            if (gx < g.cx && !solid(gx + 1, gy)) quad(c, x1, y0, x1, y1, tx1, ty1, tx1, ty0, shade(col, 0.40f))
            if (gy > g.cy && !solid(gx, gy - 1)) quad(c, x0, y0, x1, y0, tx1, ty0, tx0, ty0, shade(col, 0.58f))
            if (gy < g.cy && !solid(gx, gy + 1)) quad(c, x0, y1, x1, y1, tx1, ty1, tx0, ty1, shade(col, 0.58f))
        }
        for (i in 0 until n) {
            val gx = xs[i]; val gy = ys[i]
            val x0 = ox + (gx + fx) * s; val x1 = x0 + s
            val y0 = oy + (gy + fy) * s; val y1 = y0 + s
            val tx0 = proj(x0, ccx, lift); val tx1 = proj(x1, ccx, lift)
            val ty0 = proj(y0, ccy, lift); val ty1 = proj(y1, ccy, lift)
            rect.set(tx0, ty0, tx1, ty1)
            c.drawBitmap(tl[ts[i]], null, rect, bmpPaint)
            // destello que recorre el casco
            val sweep = 0.5f + 0.5f * sin(time * 2.2f - (gx + gy) * 0.55f)
            addPaint.alpha = (glowA * 0.25f * sweep).toInt()
            c.drawBitmap(gl[ts[i]], null, rect, addPaint)
            // halo de la franja de facción
            val pulse = 0.75f + 0.25f * sin(time * 3f + gx * 1.3f + gy * 0.7f)
            val gcx = (tx0 + tx1) / 2f; val gcy = (ty0 + ty1) / 2f
            val gr = s * 1.3f
            rect.set(gcx - gr, gcy - gr, gcx + gr, gcy + gr)
            addPaint.alpha = (glowA * 0.45f * pulse).toInt()
            c.drawBitmap(gl[ts[i]], null, rect, addPaint)
        }
    }

    private fun quad(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, col: Int) {
        path.reset()
        path.moveTo(ax, ay); path.lineTo(bx, by); path.lineTo(cx, cy); path.lineTo(dx, dy); path.close()
        paint.color = col
        c.drawPath(path, paint)
    }

    private fun drawSparks(c: Canvas) {
        for (p in sparks) {
            val f = p.life / p.max
            paint.color = Color.argb((255 * f).toInt(), Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            val r = p.size * (0.5f + f)
            c.drawRect(p.x - r, p.y - r, p.x + r, p.y + r, paint)
        }
    }

    private fun drawChips(c: Canvas) {
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

    private fun drawHud(c: Canvas, g: Game) {
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.style = Paint.Style.FILL
        val top = topInset + u * 0.95f
        paint.textSize = u * 0.5f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.argb(235, 255, 255, 255)
        c.drawText("NIVEL ${g.level}", u * 0.4f, top, paint)
        // lingotes
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

        if (time < 7f && !g.over && !paused) {
            paint.textSize = u * 0.42f
            paint.color = Color.argb((255 * min(1f, (7f - time))).toInt(), 255, 255, 255)
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
            c.drawText("Toca para reiniciar", width / 2f, height / 2f + u * 1.7f, paint)
        }
    }

    private fun drawPause(c: Canvas) {
        paint.style = Paint.Style.FILL
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
                accX = 0f; accY = 0f
                t0 = System.currentTimeMillis(); moved = false
                downOnPause = pauseRect.contains(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (paused || downOnPause || g.over) { lx = e.x; ly = e.y; return true }
                accX += e.x - lx; accY += e.y - ly
                lx = e.x; ly = e.y
                val step = s * 0.55f
                var guard = 0
                while ((abs(accX) >= step || abs(accY) >= step) && guard++ < 8) {
                    if (abs(accX) >= step) {
                        val d = if (accX > 0f) 1 else -1
                        g.steer(d, 0); accX -= d * step; moved = true
                    }
                    if (abs(accY) >= step) {
                        val d = if (accY > 0f) 1 else -1
                        g.steer(0, d); accY -= d * step; moved = true
                    }
                }
                accX = accX.coerceIn(-step, step)
                accY = accY.coerceIn(-step, step)
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - t0
                val tx = e.x - sx
                val ty = e.y - sy
                val dist = hypot(tx, ty)
                if (paused) {
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
