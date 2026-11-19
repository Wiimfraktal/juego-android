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

    private companion object {
        const val COLS = 15
        const val LIFT_LOCKED = 0.30f
        const val LIFT_PIECE = 0.55f
        // colores neón: cian, ámbar, violeta, verde, magenta, azul, naranja
        val BASE = intArrayOf(
            Color.rgb(0, 229, 255), Color.rgb(255, 196, 0), Color.rgb(176, 64, 255),
            Color.rgb(0, 255, 140), Color.rgb(255, 40, 150), Color.rgb(50, 110, 255),
            Color.rgb(255, 110, 20)
        )
    }

    private var game: Game? = null
    private var s = 0f
    private var ox = 0f
    private var oy = 0f
    private var ccx = 0f
    private var ccy = 0f
    private var planetR = 0f
    private var topInset = 0

    private var tiles: Array<Bitmap>? = null
    private var glows: Array<Bitmap>? = null
    @Volatile private var planet: Bitmap? = null
    private var planetBuiltFor = 0
    private var bgShader: Shader? = null
    private var nebA: Shader? = null
    private var nebB: Shader? = null
    private var atmoShader: Shader? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val path = Path()
    private val rect = RectF()
    private val sparks = ArrayList<Spark>()
    private val stars = ArrayList<Star>()
    private var last = 0L
    private var time = 0f
    private var shake = 0f

    private val prefs = ctx.getSharedPreferences("abismo", Context.MODE_PRIVATE)
    private var totalIngots = try { prefs.getInt("ingots", 0) } catch (e: Exception) { 0 }
    private var seenIngots = 0
    private var seenBars = 0
    private var dispFrac = 0f
    private var barFlash = 0f

    // entrada
    private var sx = 0f
    private var sy = 0f
    private var lx = 0f
    private var ly = 0f
    private var t0 = 0L
    private var moved = false

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
        val topPad = topInset + s * 2.6f
        val botPad = s * 0.9f
        var rows = ((h - topPad - botPad) / s).toInt()
        if (rows % 2 == 0) rows--
        game = Game(COLS, rows)
        ox = 0f
        oy = topPad + (h - topPad - botPad - rows * s) / 2f
        ccx = ox + (COLS / 2 + 0.5f) * s
        ccy = oy + (rows / 2 + 0.5f) * s
        planetR = s * 4.2f
        seenIngots = 0; seenBars = 0; dispFrac = 0f

        bgShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(3, 5, 18), Color.rgb(13, 6, 30), Color.rgb(3, 2, 10)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        nebA = RadialGradient(w * 0.2f, h * 0.22f, w * 0.9f,
            intArrayOf(Color.argb(70, 90, 30, 160), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        nebB = RadialGradient(w * 0.85f, h * 0.8f, w * 0.9f,
            intArrayOf(Color.argb(60, 20, 90, 170), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        atmoShader = RadialGradient(ccx, ccy, planetR * 1.5f,
            intArrayOf(Color.argb(0, 255, 120, 40), Color.argb(0, 255, 120, 40), Color.argb(150, 255, 110, 40), Color.argb(0, 255, 90, 30)),
            floatArrayOf(0f, 0.62f, 0.69f, 1f), Shader.TileMode.CLAMP)

        if (tiles == null) {
            tiles = Array(7) { Textures.makeTile(BASE[it], it * 17 + 3) }
            glows = Array(7) { Textures.makeGlow(BASE[it]) }
        }
        val pr = (planetR / 2f).toInt()
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
            stars.add(Star(Random.nextFloat() * 6.2832f, planetR + Random.nextFloat() * maxD, 0.2f + Random.nextFloat() * 0.8f))
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
        val dt = if (last == 0L) 0f else min(0.05f, (now - last) / 1e9f)
        last = now
        time += dt
        g.update(dt)
        consumeBursts(g)
        updateParticles(dt)
        updateBar(g, dt)
        shake = max(0f, shake - dt * 2.5f)

        canvas.save()
        if (shake > 0f) canvas.translate((Random.nextFloat() - 0.5f) * shake * s * 0.3f, (Random.nextFloat() - 0.5f) * shake * s * 0.3f)
        drawSpace(canvas)
        drawPlanet(canvas)
        drawDanger(canvas, g)
        drawShadow(canvas, g)
        drawLocked(canvas, g)
        drawPiece(canvas, g)
        drawSparks(canvas)
        canvas.restore()
        drawHud(canvas, g)
        postInvalidateOnAnimation()
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

    private fun updateParticles(dt: Float) {
        val it = sparks.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 0.96f; p.vy *= 0.96f
        }
        val maxD = hypot(width.toFloat(), height.toFloat()) / 2f + 20f
        for (st in stars) {
            st.dist += (s * 0.6f + s * 3.5f * st.z) * dt * (0.4f + st.dist / maxD * 1.6f)
            if (st.dist > maxD) { st.dist = planetR * 0.85f + Random.nextFloat() * s; st.ang = Random.nextFloat() * 6.2832f }
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
        // estrellas que se alejan del planeta (estamos huyendo)
        for (st in stars) {
            val fade = ((st.dist - planetR) / (s * 3f)).coerceIn(0f, 1f)
            val len = (s * 0.3f + s * 2.2f * st.z) * (st.dist / (planetR * 4f)).coerceIn(0.2f, 1.5f)
            val ca = cos(st.ang); val sa = sin(st.ang)
            paint.strokeWidth = 1f + 2.2f * st.z
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.argb((fade * (60f + 190f * st.z)).toInt().coerceIn(0, 255), 200, 215, 255)
            c.drawLine(ccx + ca * (st.dist - len), ccy + sa * (st.dist - len), ccx + ca * st.dist, ccy + sa * st.dist, paint)
        }
    }

    private fun drawPlanet(c: Canvas) {
        paint.shader = atmoShader
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val pb = planet ?: return
        rect.set(ccx - planetR, ccy - planetR, ccx + planetR, ccy + planetR)
        c.drawBitmap(pb, null, rect, bmpPaint)
    }

    private fun drawDanger(c: Canvas, g: Game) {
        val near = g.nearestFilled()
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
        // flecha que indica hacia dónde cae
        val mx = xs.average().toFloat() + 0.5f + g.gx * 2.2f
        val my = ys.average().toFloat() + 0.5f + g.gy * 2.2f
        val ax = ox + mx * s
        val ay = oy + my * s
        paint.color = Color.argb(150, 255, 255, 255)
        path.reset()
        val a = s * 0.35f
        path.moveTo(ax + g.gx * a, ay + g.gy * a)
        path.lineTo(ax - g.gx * a + g.gy * a, ay - g.gy * a + g.gx * a)
        path.lineTo(ax - g.gx * a - g.gy * a, ay - g.gy * a - g.gx * a)
        path.close()
        c.drawPath(path, paint)
        drawLayer(c, g, xs, ys, ts, 4, g.visX, g.visY, LIFT_PIECE, 190, solid)
    }

    private fun proj(v: Float, center: Float, lift: Float) =
        v + lift * s * ((v - center) / (4f * s)).coerceIn(-1f, 1f)

    /** Dibuja las paredes laterales y luego las tapas texturizadas; el relieve crece hacia afuera del planeta. */
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
            if (gx > g.cx && !solid(gx - 1, gy)) quad(c, x0, y0, x0, y1, tx0, ty1, tx0, ty0, shade(col, 0.42f))
            if (gx < g.cx && !solid(gx + 1, gy)) quad(c, x1, y0, x1, y1, tx1, ty1, tx1, ty0, shade(col, 0.42f))
            if (gy > g.cy && !solid(gx, gy - 1)) quad(c, x0, y0, x1, y0, tx1, ty0, tx0, ty0, shade(col, 0.62f))
            if (gy < g.cy && !solid(gx, gy + 1)) quad(c, x0, y1, x1, y1, tx1, ty1, tx0, ty1, shade(col, 0.62f))
        }
        for (i in 0 until n) {
            val gx = xs[i]; val gy = ys[i]
            val x0 = ox + (gx + fx) * s; val x1 = x0 + s
            val y0 = oy + (gy + fy) * s; val y1 = y0 + s
            val tx0 = proj(x0, ccx, lift); val tx1 = proj(x1, ccx, lift)
            val ty0 = proj(y0, ccy, lift); val ty1 = proj(y1, ccy, lift)
            rect.set(tx0, ty0, tx1, ty1)
            c.drawBitmap(tl[ts[i]], null, rect, bmpPaint)
            // brillo animado tipo shader: barrido diagonal sobre la placa
            val sweep = 0.5f + 0.5f * sin(time * 2.2f - (gx + gy) * 0.55f)
            addPaint.alpha = (glowA * 0.32f * sweep).toInt()
            c.drawBitmap(gl[ts[i]], null, rect, addPaint)
            // halo de energía alrededor del bloque
            val pulse = 0.75f + 0.25f * sin(time * 3f + gx * 1.3f + gy * 0.7f)
            val gcx = (tx0 + tx1) / 2f; val gcy = (ty0 + ty1) / 2f
            val gr = s * 1.35f
            rect.set(gcx - gr, gcy - gr, gcx + gr, gcy + gr)
            addPaint.alpha = (glowA * 0.55f * pulse).toInt()
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
        val top = topInset + s * 0.95f
        paint.textSize = s * 0.5f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.argb(235, 255, 255, 255)
        c.drawText("NIVEL ${g.level}", s * 0.4f, top, paint)
        // lingotes
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("$totalIngots", width - s * 0.4f, top, paint)
        val tw = paint.measureText("$totalIngots")
        drawIngot(c, width - s * 0.4f - tw - s * 0.5f, top - s * 0.17f, s * 0.7f)

        // barra de progreso
        val bx0 = s * 0.4f
        val bx1 = width - s * 0.4f
        val by0 = top + s * 0.35f
        val by1 = by0 + s * 0.42f
        rect.set(bx0, by0, bx1, by1)
        paint.color = Color.argb(150, 10, 12, 30)
        c.drawRoundRect(rect, s * 0.21f, s * 0.21f, paint)
        val fw = (bx1 - bx0) * dispFrac.coerceIn(0f, 1f)
        if (fw > s * 0.2f) {
            rect.set(bx0, by0, bx0 + fw, by1)
            paint.shader = LinearGradient(bx0, 0f, bx1, 0f,
                intArrayOf(Color.rgb(60, 140, 255), Color.rgb(0, 229, 255), Color.rgb(255, 230, 120)),
                null, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, s * 0.21f, s * 0.21f, paint)
            paint.shader = null
            // brillo en movimiento sobre la barra
            val sh = ((time * 0.5f) % 1f) * (bx1 - bx0 + s * 2f) - s
            rect.set(max(bx0, sh), by0, min(bx0 + fw, sh + s), by1)
            if (rect.width() > 1f) {
                paint.color = Color.argb(110, 255, 255, 255)
                c.drawRoundRect(rect, s * 0.2f, s * 0.2f, paint)
            }
        }
        if (barFlash > 0f) {
            rect.set(bx0, by0, bx1, by1)
            paint.color = Color.argb((200 * barFlash).toInt(), 255, 255, 255)
            c.drawRoundRect(rect, s * 0.21f, s * 0.21f, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = s * 0.5f
            paint.color = Color.argb((255 * barFlash).toInt(), 255, 240, 160)
            c.drawText("+3 lingotes", width / 2f, by1 + s * 0.8f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(140, 170, 200, 255)
        rect.set(bx0, by0, bx1, by1)
        c.drawRoundRect(rect, s * 0.21f, s * 0.21f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = s * 0.3f
        paint.color = Color.argb(230, 255, 255, 255)
        c.drawText("${g.barProgress} / ${g.barNeed()}", width / 2f, by1 - s * 0.1f, paint)

        if (time < 7f && !g.over) {
            paint.textSize = s * 0.42f
            paint.color = Color.argb((255 * min(1f, (7f - time))).toInt(), 255, 255, 255)
            c.drawText("Desliza: mueve y dirige la caída", width / 2f, height - s * 1.8f, paint)
            c.drawText("Toca: gira   ·   Desliza rápido: suelta", width / 2f, height - s * 1.2f, paint)
        }
        if (g.over) {
            paint.color = Color.argb(min(190, (g.overTime * 400).toInt()), 8, 0, 20)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            paint.textSize = s * 0.8f
            c.drawText("El planeta te alcanzó", width / 2f, height / 2f - s, paint)
            paint.textSize = s * 0.6f
            c.drawText("Puntos: ${g.score}   Lingotes: ${g.ingots}", width / 2f, height / 2f, paint)
            paint.textSize = s * 0.45f
            c.drawText("Toca para reiniciar", width / 2f, height / 2f + s * 1.2f, paint)
        }
    }

    // ---------- entrada ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.x; sy = e.y; lx = e.x; ly = e.y
                t0 = System.currentTimeMillis(); moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lx
                val dy = e.y - ly
                if (max(abs(dx), abs(dy)) >= s * 0.9f) {
                    if (abs(dx) > abs(dy)) g.steer(if (dx > 0) 1 else -1, 0)
                    else g.steer(0, if (dy > 0) 1 else -1)
                    lx = e.x; ly = e.y; moved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - t0
                val tx = e.x - sx
                val ty = e.y - sy
                val dist = hypot(tx, ty)
                if (g.over) {
                    if (g.overTime > 0.8f) layoutGame()
                } else if (!moved && dist < s * 0.4f && dt < 300) {
                    g.rotate()
                } else if (dt < 220 && dist > s * 1.2f) {
                    if (abs(tx) > abs(ty)) g.hardDrop(if (tx > 0) 1 else -1, 0)
                    else g.hardDrop(0, if (ty > 0) 1 else -1)
                }
            }
        }
        return true
    }
}
