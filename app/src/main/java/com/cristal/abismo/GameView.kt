package com.cristal.abismo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
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

    private class Mote(var x: Float, var y: Float, val z: Float, val speed: Float)

    private companion object {
        const val COLS = 11
        const val H_LOCKED = 0.20f
        const val H_PIECE = 0.34f
        // cristales: cian, ámbar, violeta, esmeralda, rosa, azul, naranja
        val BASE = intArrayOf(
            Color.rgb(70, 220, 255), Color.rgb(255, 205, 70), Color.rgb(170, 110, 255),
            Color.rgb(70, 230, 140), Color.rgb(255, 100, 170), Color.rgb(90, 130, 255),
            Color.rgb(255, 140, 60)
        )
    }

    private var game: Game? = null
    private var s = 0f
    private var ox = 0f
    private var oy = 0f
    private var ccx = 0f
    private var ccy = 0f
    private var floorShader: Shader? = null
    private var gridLines = FloatArray(0)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val sparks = ArrayList<Spark>()
    private val motes = ArrayList<Mote>()
    private var last = 0L
    private var time = 0f
    private var shake = 0f

    // entrada
    private var sx = 0f
    private var sy = 0f
    private var lx = 0f
    private var ly = 0f
    private var t0 = 0L
    private var moved = false

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        s = w / COLS.toFloat()
        var rows = (h / s).toInt()
        if (rows % 2 == 0) rows--
        game = Game(COLS, rows)
        ox = 0f
        oy = (h - rows * s) / 2f
        ccx = ox + (COLS / 2 + 0.5f) * s
        ccy = oy + (rows / 2 + 0.5f) * s
        floorShader = RadialGradient(
            ccx, ccy, max(w, h) * 0.75f,
            intArrayOf(Color.rgb(58, 30, 96), Color.rgb(24, 12, 44), Color.rgb(6, 3, 14)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        val lines = ArrayList<Float>()
        for (i in 0..COLS) { lines.add(ox + i * s); lines.add(oy); lines.add(ox + i * s); lines.add(oy + rows * s) }
        for (j in 0..rows) { lines.add(ox); lines.add(oy + j * s); lines.add(ox + COLS * s); lines.add(oy + j * s) }
        gridLines = lines.toFloatArray()
        motes.clear()
        repeat(45) { motes.add(Mote(Random.nextFloat() * w, Random.nextFloat() * h, 0.2f + Random.nextFloat() * 0.8f, 8f + Random.nextFloat() * 22f)) }
        sparks.clear()
        last = 0L
    }

    // ---------- color ----------
    private fun shade(c: Int, f: Float) = Color.rgb(
        (Color.red(c) * f).toInt().coerceIn(0, 255),
        (Color.green(c) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) * f).toInt().coerceIn(0, 255)
    )

    private fun lighten(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
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
        shake = max(0f, shake - dt * 2.5f)

        canvas.save()
        if (shake > 0f) canvas.translate((Random.nextFloat() - 0.5f) * shake * s * 0.3f, (Random.nextFloat() - 0.5f) * shake * s * 0.3f)
        drawFloor(canvas, g)
        drawMotes(canvas, false)
        drawShadow(canvas, g)
        drawLocked(canvas, g)
        drawPiece(canvas, g)
        drawSparks(canvas)
        drawMotes(canvas, true)
        canvas.restore()
        drawHud(canvas, g)
        postInvalidateOnAnimation()
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
                sparks.add(Spark(px, py, kotlin.math.cos(a) * v, sin(a) * v, 0.8f, 0.8f, lighten(col, 0.3f), s * (0.07f + Random.nextFloat() * 0.1f)))
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
        val w = width.toFloat()
        val h = height.toFloat()
        for (m in motes) {
            m.y -= m.speed * m.z * dt
            m.x += sin(time * 0.6f + m.y * 0.01f) * 6f * m.z * dt
            if (m.y < -10f) { m.y = h + 10f; m.x = Random.nextFloat() * w }
        }
    }

    // ---------- dibujo ----------
    private fun drawFloor(c: Canvas, g: Game) {
        paint.style = Paint.Style.FILL
        paint.shader = floorShader
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        // rejilla del suelo
        paint.color = Color.argb(22, 190, 160, 255)
        paint.strokeWidth = 1.5f
        c.drawLines(gridLines, paint)
        // zona de peligro central
        val near = g.nearestFilled()
        val pulse = 0.5f + 0.5f * sin(time * (if (near <= 3) 9f else 3f))
        val alpha = if (near <= 3) (60 + 80 * pulse).toInt() else (22 + 14 * pulse).toInt()
        paint.color = Color.argb(alpha, 255, 60, 80)
        c.drawRect(ox + (g.cx - 1) * s, oy + (g.cy - 1) * s, ox + (g.cx + 2) * s, oy + (g.cy + 2) * s, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(alpha + 40, 255, 120, 140)
        c.drawRect(ox + (g.cx - 1) * s, oy + (g.cy - 1) * s, ox + (g.cx + 2) * s, oy + (g.cy + 2) * s, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawMotes(c: Canvas, front: Boolean) {
        for (m in motes) {
            if ((m.z > 0.75f) != front) continue
            val a = (40 + 90 * m.z).toInt()
            paint.color = Color.argb(if (front) a / 2 else a, 200, 180, 255)
            c.drawCircle(m.x, m.y, 1.5f + m.z * (if (front) 5f else 3f), paint)
        }
    }

    private fun drawShadow(c: Canvas, g: Game) {
        val p = g.piece ?: return
        paint.color = Color.argb(90, 0, 0, 0)
        val o = s * 0.12f
        for (cell in p.cells) {
            val x = ox + (p.px + cell[0]) * s + o
            val y = oy + (p.py + cell[1]) * s + o
            c.drawRect(x, y, x + s, y + s, paint)
        }
    }

    private fun drawLocked(c: Canvas, g: Game) {
        val n = g.rows * g.cols
        val xs = IntArray(n); val ys = IntArray(n); val cs = IntArray(n)
        var k = 0
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            if (g.grid[y][x] != 0) { xs[k] = x; ys[k] = y; cs[k] = BASE[g.grid[y][x] - 1]; k++ }
        }
        val solid = { x: Int, y: Int -> x in 0 until g.cols && y in 0 until g.rows && g.grid[y][x] != 0 }
        drawLayer(c, g, xs, ys, cs, k, 0f, 0f, H_LOCKED, solid)
    }

    private fun drawPiece(c: Canvas, g: Game) {
        val p = g.piece ?: return
        val xs = IntArray(4) { p.px + p.cells[it][0] }
        val ys = IntArray(4) { p.py + p.cells[it][1] }
        val col = BASE[p.type]
        val cs = IntArray(4) { col }
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
        drawLayer(c, g, xs, ys, cs, 4, g.visX, g.visY, H_PIECE, solid)
    }

    /** Dibuja primero las caras laterales y luego las tapas facetadas (efecto 2.5D en perspectiva). */
    private fun drawLayer(
        c: Canvas, g: Game, xs: IntArray, ys: IntArray, cs: IntArray, n: Int,
        fx: Float, fy: Float, h: Float, solid: (Int, Int) -> Boolean
    ) {
        val k = 1f + h
        paint.style = Paint.Style.FILL
        for (i in 0 until n) {
            val gx = xs[i]; val gy = ys[i]
            val x0 = ox + (gx + fx) * s; val x1 = x0 + s
            val y0 = oy + (gy + fy) * s; val y1 = y0 + s
            val tx0 = ccx + (x0 - ccx) * k; val tx1 = ccx + (x1 - ccx) * k
            val ty0 = ccy + (y0 - ccy) * k; val ty1 = ccy + (y1 - ccy) * k
            val col = cs[i]
            if (gx > g.cx && !solid(gx - 1, gy)) quad(c, x0, y0, x0, y1, tx0, ty1, tx0, ty0, shade(col, 0.50f))
            if (gx < g.cx && !solid(gx + 1, gy)) quad(c, x1, y0, x1, y1, tx1, ty1, tx1, ty0, shade(col, 0.50f))
            if (gy > g.cy && !solid(gx, gy - 1)) quad(c, x0, y0, x1, y0, tx1, ty0, tx0, ty0, shade(col, 0.72f))
            if (gy < g.cy && !solid(gx, gy + 1)) quad(c, x0, y1, x1, y1, tx1, ty1, tx0, ty1, shade(col, 0.72f))
        }
        for (i in 0 until n) {
            val gx = xs[i]; val gy = ys[i]
            val x0 = ox + (gx + fx) * s; val x1 = x0 + s
            val y0 = oy + (gy + fy) * s; val y1 = y0 + s
            val tx0 = ccx + (x0 - ccx) * k; val tx1 = ccx + (x1 - ccx) * k
            val ty0 = ccy + (y0 - ccy) * k; val ty1 = ccy + (y1 - ccy) * k
            val col = cs[i]
            val mx = (tx0 + tx1) / 2f - (tx1 - tx0) * 0.08f
            val my = (ty0 + ty1) / 2f - (ty1 - ty0) * 0.08f
            tri(c, tx0, ty0, tx1, ty0, mx, my, lighten(col, 0.38f))
            tri(c, tx0, ty0, tx0, ty1, mx, my, lighten(col, 0.10f))
            tri(c, tx1, ty0, tx1, ty1, mx, my, shade(col, 0.82f))
            tri(c, tx0, ty1, tx1, ty1, mx, my, shade(col, 0.64f))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(110, 255, 255, 255)
            c.drawRect(tx0, ty0, tx1, ty1, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun quad(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, col: Int) {
        path.reset()
        path.moveTo(ax, ay); path.lineTo(bx, by); path.lineTo(cx, cy); path.lineTo(dx, dy); path.close()
        paint.color = col
        c.drawPath(path, paint)
    }

    private fun tri(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, col: Int) {
        path.reset()
        path.moveTo(ax, ay); path.lineTo(bx, by); path.lineTo(cx, cy); path.close()
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

    private fun drawHud(c: Canvas, g: Game) {
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = s * 0.55f
        paint.color = Color.argb(230, 255, 255, 255)
        c.drawText("PUNTOS ${g.score}", s * 0.4f, s * 1.1f, paint)
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("NIVEL ${g.level}", width - s * 0.4f, s * 1.1f, paint)
        if (time < 7f && !g.over) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = s * 0.42f
            paint.color = Color.argb((255 * min(1f, (7f - time))).toInt(), 255, 255, 255)
            c.drawText("Desliza: mueve y dirige la caída", width / 2f, height - s * 1.8f, paint)
            c.drawText("Toca: gira   ·   Desliza rápido: suelta", width / 2f, height - s * 1.2f, paint)
        }
        if (g.over) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(min(190, (g.overTime * 400).toInt()), 8, 0, 20)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            paint.textSize = s * 0.8f
            c.drawText("El abismo te alcanzó", width / 2f, height / 2f - s, paint)
            paint.textSize = s * 0.6f
            c.drawText("Puntos: ${g.score}", width / 2f, height / 2f, paint)
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
                    if (g.overTime > 0.8f) { onSizeChanged(width, height, width, height) }
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
