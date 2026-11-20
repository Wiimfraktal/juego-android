package com.cristal.abismo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Emblema del piloto estilo Halo Reach: placa de fondo, forma y símbolo, cada uno con su color. */
object Emblem {
    val palette = intArrayOf(
        Color.rgb(20, 22, 30), Color.rgb(235, 238, 245), Color.rgb(150, 155, 165), Color.rgb(200, 40, 40),
        Color.rgb(255, 120, 30), Color.rgb(250, 200, 50), Color.rgb(60, 190, 80), Color.rgb(20, 120, 70),
        Color.rgb(40, 200, 220), Color.rgb(40, 100, 220), Color.rgb(25, 40, 110), Color.rgb(140, 70, 220),
        Color.rgb(230, 80, 170), Color.rgb(120, 70, 40), Color.rgb(210, 180, 130), Color.rgb(90, 110, 60),
        Color.rgb(212, 175, 55), Color.rgb(192, 198, 206), Color.rgb(176, 110, 60), Color.rgb(110, 118, 128),
        Color.rgb(52, 58, 66), Color.rgb(120, 10, 25), Color.rgb(12, 22, 60), Color.rgb(0, 128, 128),
        Color.rgb(85, 90, 40), Color.rgb(230, 210, 160), Color.rgb(250, 180, 200), Color.rgb(170, 200, 250),
        Color.rgb(160, 240, 200), Color.rgb(190, 170, 240), Color.rgb(255, 127, 80), Color.rgb(220, 240, 255)
    )
    val shapeNames = arrayOf("Círculo", "Escudo", "Hexágono", "Diamante", "Estrella", "Triángulo", "Placa", "Octágono", "Alas", "Corona",
        "Engranaje", "Anillo orbital", "Estandarte", "Sello")
    val symbolNames = arrayOf("Estrella", "Rayo", "Planeta", "Alas", "Espadas", "Ojo", "Llama", "Luna",
        "Calavera", "Cometa", "Fénix", "Garra", "Gema", "Corona estelar",
        "Cohete", "Satélite", "Átomo", "Órbita", "Galaxia", "Constelación", "Agujero negro", "Casco",
        "Radar", "Telescopio", "Nave", "Engranaje", "Llave y martillo", "Tuerca", "Circuito", "Reactor")

    private val path = Path()
    private val p2 = Path()
    private val rc = RectF()

    private fun poly(cx: Float, cy: Float, r: Float, n: Int, rot: Float, inner: Float = 0f) {
        path.reset()
        val pts = if (inner > 0f) n * 2 else n
        for (i in 0 until pts) {
            val a = rot + i * 2f * PI.toFloat() / pts
            val rr = if (inner > 0f && i % 2 == 1) r * inner else r
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun shapePath(shape: Int, cx: Float, cy: Float, r: Float) {
        val up = -PI.toFloat() / 2f
        when (shape) {
            0 -> { path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW) }
            1 -> {
                path.reset()
                path.moveTo(cx - r * 0.85f, cy - r * 0.8f)
                path.lineTo(cx + r * 0.85f, cy - r * 0.8f)
                path.lineTo(cx + r * 0.85f, cy + r * 0.05f)
                path.quadTo(cx + r * 0.75f, cy + r * 0.7f, cx, cy + r)
                path.quadTo(cx - r * 0.75f, cy + r * 0.7f, cx - r * 0.85f, cy + r * 0.05f)
                path.close()
            }
            2 -> poly(cx, cy, r, 6, up)
            3 -> poly(cx, cy, r, 4, up)
            4 -> poly(cx, cy, r, 5, up, 0.5f)
            5 -> poly(cx, cy + r * 0.15f, r, 3, up)
            6 -> { path.reset(); rc.set(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f); path.addRoundRect(rc, r * 0.25f, r * 0.25f, Path.Direction.CW) }
            7 -> poly(cx, cy, r, 8, up + PI.toFloat() / 8f)
            8 -> {
                path.reset()
                path.moveTo(cx, cy - r * 0.2f)
                path.lineTo(cx + r, cy - r * 0.7f); path.lineTo(cx + r * 0.75f, cy + r * 0.1f); path.lineTo(cx + r * 0.3f, cy + r * 0.25f)
                path.lineTo(cx, cy + r * 0.8f)
                path.lineTo(cx - r * 0.3f, cy + r * 0.25f); path.lineTo(cx - r * 0.75f, cy + r * 0.1f); path.lineTo(cx - r, cy - r * 0.7f)
                path.close()
            }
            10 -> gear(cx, cy, r, 10, 0.8f)
            11 -> {
                path.reset(); path.addCircle(cx, cy, r * 0.62f, Path.Direction.CW)
                p2.reset(); rc.set(cx - r, cy - r * 0.42f, cx + r, cy + r * 0.42f); p2.addOval(rc, Path.Direction.CW)
                val m = android.graphics.Matrix(); m.setRotate(-20f, cx, cy); p2.transform(m)
                path.op(p2, Path.Op.UNION)
            }
            12 -> {
                path.reset()
                path.moveTo(cx - r * 0.78f, cy - r * 0.92f); path.lineTo(cx + r * 0.78f, cy - r * 0.92f)
                path.lineTo(cx + r * 0.78f, cy + r * 0.95f); path.lineTo(cx, cy + r * 0.5f); path.lineTo(cx - r * 0.78f, cy + r * 0.95f)
                path.close()
            }
            13 -> poly(cx, cy, r, 16, up, 0.84f)
            else -> {
                path.reset()
                path.moveTo(cx - r * 0.9f, cy + r * 0.7f)
                path.lineTo(cx - r * 0.9f, cy - r * 0.5f); path.lineTo(cx - r * 0.45f, cy - r * 0.05f)
                path.lineTo(cx, cy - r * 0.8f); path.lineTo(cx + r * 0.45f, cy - r * 0.05f)
                path.lineTo(cx + r * 0.9f, cy - r * 0.5f); path.lineTo(cx + r * 0.9f, cy + r * 0.7f)
                path.close()
            }
        }
    }

    /** Engranaje: `n` dientes trapezoidales, raíz a `inner`·r. */
    private fun gear(cx: Float, cy: Float, r: Float, n: Int, inner: Float) {
        path.reset()
        val step = 2f * PI.toFloat() / n
        for (i in 0 until n) {
            val a = i * step - PI.toFloat() / 2f
            val pts = floatArrayOf(-0.5f, inner, -0.3f, 1f, 0.3f, 1f, 0.5f, inner)
            for (k in 0 until 4) {
                val aa = a + pts[k * 2] * step * 0.9f
                val rr = r * pts[k * 2 + 1]
                val x = cx + cos(aa) * rr; val y = cy + sin(aa) * rr
                if (i == 0 && k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
    }

    private inline fun stroked(p: Paint, w: Float, block: () -> Unit) {
        val st = p.style; val sw = p.strokeWidth; val cap = p.strokeCap
        p.style = Paint.Style.STROKE; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND
        block()
        p.style = st; p.strokeWidth = sw; p.strokeCap = cap
    }

    private fun symbol(c: Canvas, p: Paint, sym: Int, cx: Float, cy: Float, r: Float) {
        val up = -PI.toFloat() / 2f
        when (sym) {
            0 -> { poly(cx, cy, r, 5, up, 0.42f); c.drawPath(path, p) }
            1 -> {
                path.reset()
                path.moveTo(cx + r * 0.25f, cy - r); path.lineTo(cx - r * 0.45f, cy + r * 0.1f); path.lineTo(cx - r * 0.02f, cy + r * 0.1f)
                path.lineTo(cx - r * 0.25f, cy + r); path.lineTo(cx + r * 0.45f, cy - r * 0.15f); path.lineTo(cx + r * 0.02f, cy - r * 0.15f)
                path.close(); c.drawPath(path, p)
            }
            2 -> {
                c.drawCircle(cx, cy, r * 0.55f, p)
                val st = p.style; val sw = p.strokeWidth
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.12f
                rc.set(cx - r, cy - r * 0.32f, cx + r, cy + r * 0.32f)
                c.save(); c.rotate(-18f, cx, cy); c.drawOval(rc, p); c.restore()
                p.style = st; p.strokeWidth = sw
            }
            3 -> {
                for (sg in intArrayOf(-1, 1)) {
                    path.reset()
                    path.moveTo(cx + sg * r * 0.1f, cy)
                    path.lineTo(cx + sg * r, cy - r * 0.55f); path.lineTo(cx + sg * r * 0.85f, cy - r * 0.15f)
                    path.lineTo(cx + sg * r * 0.95f, cy - r * 0.05f); path.lineTo(cx + sg * r * 0.7f, cy + r * 0.2f)
                    path.lineTo(cx + sg * r * 0.75f, cy + r * 0.3f); path.lineTo(cx + sg * r * 0.15f, cy + r * 0.35f)
                    path.close(); c.drawPath(path, p)
                }
                c.drawCircle(cx, cy + r * 0.1f, r * 0.14f, p)
            }
            4 -> for (ang in floatArrayOf(45f, -45f)) {
                c.save(); c.rotate(ang, cx, cy)
                rc.set(cx - r * 0.08f, cy - r, cx + r * 0.08f, cy + r * 0.55f); c.drawRect(rc, p)
                rc.set(cx - r * 0.3f, cy + r * 0.45f, cx + r * 0.3f, cy + r * 0.58f); c.drawRect(rc, p)
                rc.set(cx - r * 0.07f, cy + r * 0.58f, cx + r * 0.07f, cy + r * 0.95f); c.drawRect(rc, p)
                c.restore()
            }
            5 -> {
                path.reset()
                path.moveTo(cx - r, cy); path.quadTo(cx, cy - r * 0.85f, cx + r, cy); path.quadTo(cx, cy + r * 0.85f, cx - r, cy)
                path.close(); c.drawPath(path, p)
                val col = p.color
                p.color = Color.argb(200, 0, 0, 0); c.drawCircle(cx, cy, r * 0.32f, p)
                p.color = col; c.drawCircle(cx, cy, r * 0.14f, p)
            }
            6 -> {
                path.reset()
                path.moveTo(cx, cy - r)
                path.cubicTo(cx + r * 0.6f, cy - r * 0.3f, cx + r * 0.75f, cy + r * 0.35f, cx, cy + r)
                path.cubicTo(cx - r * 0.75f, cy + r * 0.35f, cx - r * 0.45f, cy - r * 0.2f, cx - r * 0.15f, cy - r * 0.35f)
                path.cubicTo(cx - r * 0.1f, cy - r * 0.6f, cx, cy - r * 0.75f, cx, cy - r)
                path.close(); c.drawPath(path, p)
            }
            7 -> {
                path.reset(); path.addCircle(cx, cy, r * 0.85f, Path.Direction.CW)
                p2.reset(); p2.addCircle(cx + r * 0.38f, cy - r * 0.18f, r * 0.72f, Path.Direction.CW)
                path.op(p2, Path.Op.DIFFERENCE); c.drawPath(path, p)
            }
            8 -> {
                path.reset(); path.addCircle(cx, cy - r * 0.15f, r * 0.7f, Path.Direction.CW)
                rc.set(cx - r * 0.42f, cy + r * 0.3f, cx + r * 0.42f, cy + r * 0.85f); path.addRect(rc, Path.Direction.CW)
                p2.reset()
                p2.addCircle(cx - r * 0.28f, cy - r * 0.15f, r * 0.18f, Path.Direction.CW)
                p2.addCircle(cx + r * 0.28f, cy - r * 0.15f, r * 0.18f, Path.Direction.CW)
                for (k in -1..1) { rc.set(cx + k * r * 0.22f - r * 0.05f, cy + r * 0.5f, cx + k * r * 0.22f + r * 0.05f, cy + r * 0.85f); p2.addRect(rc, Path.Direction.CW) }
                path.op(p2, Path.Op.DIFFERENCE); c.drawPath(path, p)
            }
            9 -> {
                c.drawCircle(cx + r * 0.45f, cy - r * 0.45f, r * 0.32f, p)
                path.reset()
                path.moveTo(cx + r * 0.2f, cy - r * 0.7f); path.lineTo(cx - r, cy + r)
                path.lineTo(cx + r * 0.7f, cy - r * 0.2f); path.close(); c.drawPath(path, p)
            }
            10 -> {
                path.reset()
                path.moveTo(cx, cy - r * 0.9f)
                path.lineTo(cx + r * 0.2f, cy - r * 0.3f); path.lineTo(cx + r, cy - r * 0.6f); path.lineTo(cx + r * 0.55f, cy + r * 0.05f)
                path.lineTo(cx + r * 0.75f, cy + r * 0.2f); path.lineTo(cx + r * 0.2f, cy + r * 0.3f); path.lineTo(cx + r * 0.3f, cy + r)
                path.lineTo(cx, cy + r * 0.6f)
                path.lineTo(cx - r * 0.3f, cy + r); path.lineTo(cx - r * 0.2f, cy + r * 0.3f); path.lineTo(cx - r * 0.75f, cy + r * 0.2f)
                path.lineTo(cx - r * 0.55f, cy + r * 0.05f); path.lineTo(cx - r, cy - r * 0.6f); path.lineTo(cx - r * 0.2f, cy - r * 0.3f)
                path.close(); c.drawPath(path, p)
            }
            11 -> for (k in -1..1) {
                c.save(); c.rotate(20f, cx, cy)
                path.reset()
                val x = cx + k * r * 0.42f
                path.moveTo(x - r * 0.1f, cy - r); path.lineTo(x + r * 0.1f, cy - r); path.lineTo(x + r * 0.03f, cy + r); path.close()
                c.drawPath(path, p); c.restore()
            }
            12 -> {
                path.reset()
                path.moveTo(cx - r * 0.55f, cy - r * 0.55f); path.lineTo(cx + r * 0.55f, cy - r * 0.55f)
                path.lineTo(cx + r * 0.95f, cy - r * 0.1f); path.lineTo(cx, cy + r * 0.95f); path.lineTo(cx - r * 0.95f, cy - r * 0.1f)
                path.close(); c.drawPath(path, p)
                val col = p.color
                p.color = Color.argb(90, 255, 255, 255)
                path.reset(); path.moveTo(cx - r * 0.55f, cy - r * 0.55f); path.lineTo(cx, cy - r * 0.1f); path.lineTo(cx - r * 0.95f, cy - r * 0.1f); path.close()
                c.drawPath(path, p); p.color = col
            }
            14 -> { // cohete
                path.reset()
                path.moveTo(cx, cy - r)
                path.cubicTo(cx + r * 0.38f, cy - r * 0.6f, cx + r * 0.3f, cy + r * 0.2f, cx + r * 0.24f, cy + r * 0.45f)
                path.lineTo(cx - r * 0.24f, cy + r * 0.45f)
                path.cubicTo(cx - r * 0.3f, cy + r * 0.2f, cx - r * 0.38f, cy - r * 0.6f, cx, cy - r)
                path.close(); c.drawPath(path, p)
                for (sg in intArrayOf(-1, 1)) {
                    path.reset(); path.moveTo(cx + sg * r * 0.22f, cy + r * 0.05f); path.lineTo(cx + sg * r * 0.55f, cy + r * 0.55f)
                    path.lineTo(cx + sg * r * 0.22f, cy + r * 0.45f); path.close(); c.drawPath(path, p)
                }
                path.reset(); path.moveTo(cx - r * 0.15f, cy + r * 0.5f); path.lineTo(cx, cy + r); path.lineTo(cx + r * 0.15f, cy + r * 0.5f)
                path.close(); c.drawPath(path, p)
                val col = p.color; p.color = Color.argb(170, 0, 0, 0); c.drawCircle(cx, cy - r * 0.35f, r * 0.12f, p); p.color = col
            }
            15 -> { // satélite
                c.save(); c.rotate(-30f, cx, cy)
                rc.set(cx - r * 0.2f, cy - r * 0.28f, cx + r * 0.2f, cy + r * 0.28f); c.drawRect(rc, p)
                for (sg in intArrayOf(-1, 1)) {
                    rc.set(if (sg < 0) cx - r else cx + r * 0.3f, cy - r * 0.2f, if (sg < 0) cx - r * 0.3f else cx + r, cy + r * 0.2f)
                    c.drawRect(rc, p)
                    stroked(p, r * 0.06f) { c.drawLine(cx + sg * r * 0.2f, cy, cx + sg * r * 0.3f, cy, p) }
                }
                stroked(p, r * 0.07f) { c.drawLine(cx, cy - r * 0.28f, cx, cy - r * 0.55f, p) }
                rc.set(cx - r * 0.25f, cy - r * 0.8f, cx + r * 0.25f, cy - r * 0.5f); c.drawArc(rc, 0f, 180f, false, p)
                c.restore()
            }
            16 -> { // átomo
                stroked(p, r * 0.1f) {
                    for (k in 0 until 3) {
                        c.save(); c.rotate(k * 60f, cx, cy)
                        rc.set(cx - r, cy - r * 0.36f, cx + r, cy + r * 0.36f); c.drawOval(rc, p)
                        c.restore()
                    }
                }
                c.drawCircle(cx, cy, r * 0.17f, p)
            }
            17 -> { // órbita
                c.drawCircle(cx, cy, r * 0.42f, p)
                stroked(p, r * 0.07f) { c.drawCircle(cx, cy, r * 0.82f, p) }
                c.drawCircle(cx + r * 0.58f, cy - r * 0.58f, r * 0.15f, p)
            }
            18 -> { // galaxia espiral
                stroked(p, r * 0.16f) {
                    for (arm in 0 until 2) {
                        path.reset()
                        for (k in 0..24) {
                            val t = k / 24f
                            val a = arm * PI.toFloat() + t * 3.4f
                            val rr = r * (0.12f + 0.85f * t)
                            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr * 0.8f
                            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        c.drawPath(path, p)
                    }
                }
                c.drawCircle(cx, cy, r * 0.22f, p)
            }
            19 -> { // constelación
                val pts = floatArrayOf(-0.8f, 0.5f, -0.35f, -0.1f, 0.1f, 0.2f, 0.45f, -0.55f, 0.85f, -0.2f, 0.3f, 0.75f)
                stroked(p, r * 0.06f) {
                    for (k in 0 until 4) c.drawLine(cx + pts[k * 2] * r, cy + pts[k * 2 + 1] * r, cx + pts[k * 2 + 2] * r, cy + pts[k * 2 + 3] * r, p)
                    c.drawLine(cx + pts[4] * r, cy + pts[5] * r, cx + pts[10] * r, cy + pts[11] * r, p)
                }
                for (k in 0 until 6) { poly(cx + pts[k * 2] * r, cy + pts[k * 2 + 1] * r, r * (if (k == 3) 0.24f else 0.16f), 4, -PI.toFloat() / 2f, 0.4f); c.drawPath(path, p) }
            }
            20 -> { // agujero negro
                stroked(p, r * 0.2f) {
                    c.save(); c.rotate(-15f, cx, cy)
                    rc.set(cx - r, cy - r * 0.3f, cx + r, cy + r * 0.3f); c.drawOval(rc, p)
                    c.restore()
                }
                val col = p.color
                p.color = Color.argb(235, 0, 0, 0); c.drawCircle(cx, cy, r * 0.45f, p)
                p.color = col
                stroked(p, r * 0.06f) { c.drawCircle(cx, cy, r * 0.5f, p) }
            }
            21 -> { // casco de astronauta
                path.reset(); path.addCircle(cx, cy - r * 0.1f, r * 0.8f, Path.Direction.CW)
                rc.set(cx - r * 0.6f, cy + r * 0.45f, cx + r * 0.6f, cy + r * 0.95f); path.addRoundRect(rc, r * 0.15f, r * 0.15f, Path.Direction.CW)
                p2.reset(); rc.set(cx - r * 0.55f, cy - r * 0.45f, cx + r * 0.55f, cy + r * 0.2f); p2.addRoundRect(rc, r * 0.3f, r * 0.3f, Path.Direction.CW)
                path.op(p2, Path.Op.DIFFERENCE); c.drawPath(path, p)
                val col = p.color; p.color = Color.argb(110, 255, 255, 255)
                rc.set(cx - r * 0.42f, cy - r * 0.38f, cx - r * 0.1f, cy - r * 0.18f); c.drawOval(rc, p); p.color = col
            }
            22 -> { // radar
                rc.set(cx - r * 0.85f, cy - r * 0.9f, cx + r * 0.55f, cy + r * 0.5f)
                c.save(); c.rotate(-35f, cx - r * 0.15f, cy - r * 0.2f); c.drawArc(rc, 0f, 180f, true, p); c.restore()
                stroked(p, r * 0.12f) {
                    c.drawLine(cx - r * 0.15f, cy - r * 0.1f, cx - r * 0.15f, cy + r * 0.75f, p)
                    c.drawLine(cx - r * 0.55f, cy + r * 0.85f, cx + r * 0.25f, cy + r * 0.85f, p)
                }
                stroked(p, r * 0.07f) {
                    for (k in 1..2) { rc.set(cx + r * 0.1f - k * r * 0.25f, cy - r * 0.95f - k * r * 0.0f, cx + r * 0.6f + k * r * 0.2f, cy - r * 0.45f + k * r * 0.25f); c.drawArc(rc, -80f, 70f, false, p) }
                }
            }
            23 -> { // telescopio
                c.save(); c.rotate(-30f, cx, cy)
                rc.set(cx - r * 0.85f, cy - r * 0.2f, cx + r * 0.55f, cy + r * 0.12f); c.drawRect(rc, p)
                rc.set(cx + r * 0.5f, cy - r * 0.28f, cx + r * 0.85f, cy + r * 0.2f); c.drawRect(rc, p)
                c.restore()
                stroked(p, r * 0.09f) {
                    c.drawLine(cx, cy + r * 0.05f, cx - r * 0.5f, cy + r * 0.95f, p)
                    c.drawLine(cx, cy + r * 0.05f, cx + r * 0.5f, cy + r * 0.95f, p)
                    c.drawLine(cx, cy + r * 0.05f, cx, cy + r * 0.95f, p)
                }
            }
            24 -> { // nave
                path.reset()
                path.moveTo(cx, cy - r); path.lineTo(cx + r * 0.25f, cy - r * 0.2f); path.lineTo(cx + r * 0.95f, cy + r * 0.45f)
                path.lineTo(cx + r * 0.9f, cy + r * 0.7f); path.lineTo(cx + r * 0.25f, cy + r * 0.5f); path.lineTo(cx + r * 0.18f, cy + r * 0.85f)
                path.lineTo(cx - r * 0.18f, cy + r * 0.85f); path.lineTo(cx - r * 0.25f, cy + r * 0.5f); path.lineTo(cx - r * 0.9f, cy + r * 0.7f)
                path.lineTo(cx - r * 0.95f, cy + r * 0.45f); path.lineTo(cx - r * 0.25f, cy - r * 0.2f); path.close()
                c.drawPath(path, p)
                val col = p.color; p.color = Color.argb(170, 0, 0, 0)
                path.reset(); path.moveTo(cx, cy - r * 0.6f); path.lineTo(cx + r * 0.1f, cy - r * 0.25f); path.lineTo(cx - r * 0.1f, cy - r * 0.25f); path.close()
                c.drawPath(path, p); p.color = col
            }
            25 -> { // engranaje
                gear(cx, cy, r, 8, 0.74f)
                p2.reset(); p2.addCircle(cx, cy, r * 0.3f, Path.Direction.CW)
                path.op(p2, Path.Op.DIFFERENCE); c.drawPath(path, p)
            }
            26 -> { // llave y martillo cruzados
                c.save(); c.rotate(45f, cx, cy)
                rc.set(cx - r * 0.08f, cy - r * 0.45f, cx + r * 0.08f, cy + r * 0.95f); c.drawRect(rc, p)
                path.reset(); path.addCircle(cx, cy - r * 0.62f, r * 0.3f, Path.Direction.CW)
                p2.reset(); rc.set(cx - r * 0.1f, cy - r * 1f, cx + r * 0.1f, cy - r * 0.6f); p2.addRect(rc, Path.Direction.CW)
                path.op(p2, Path.Op.DIFFERENCE); c.drawPath(path, p)
                c.restore()
                c.save(); c.rotate(-45f, cx, cy)
                rc.set(cx - r * 0.07f, cy - r * 0.55f, cx + r * 0.07f, cy + r * 0.95f); c.drawRect(rc, p)
                rc.set(cx - r * 0.4f, cy - r * 0.85f, cx + r * 0.4f, cy - r * 0.5f); c.drawRoundRect(rc, r * 0.06f, r * 0.06f, p)
                c.restore()
            }
            27 -> { // tuerca
                poly(cx, cy, r * 0.9f, 6, 0f)
                p2.reset(); p2.addCircle(cx, cy, r * 0.4f, Path.Direction.CW)
                path.op(p2, Path.Op.DIFFERENCE); c.drawPath(path, p)
            }
            28 -> { // circuito
                stroked(p, r * 0.09f) {
                    path.reset()
                    path.moveTo(cx - r * 0.9f, cy - r * 0.5f); path.lineTo(cx - r * 0.3f, cy - r * 0.5f); path.lineTo(cx, cy - r * 0.2f)
                    path.moveTo(cx - r * 0.9f, cy + r * 0.3f); path.lineTo(cx - r * 0.25f, cy + r * 0.3f)
                    path.moveTo(cx + r * 0.25f, cy - r * 0.25f); path.lineTo(cx + r * 0.5f, cy - r * 0.7f); path.lineTo(cx + r * 0.9f, cy - r * 0.7f)
                    path.moveTo(cx + r * 0.25f, cy + r * 0.2f); path.lineTo(cx + r * 0.6f, cy + r * 0.55f); path.lineTo(cx + r * 0.6f, cy + r * 0.9f)
                    c.drawPath(path, p)
                }
                rc.set(cx - r * 0.28f, cy - r * 0.28f, cx + r * 0.28f, cy + r * 0.28f); c.drawRoundRect(rc, r * 0.06f, r * 0.06f, p)
                for (q in floatArrayOf(-0.9f, -0.5f, -0.9f, 0.3f, 0.9f, -0.7f, 0.6f, 0.9f).toList().chunked(2)) c.drawCircle(cx + q[0] * r, cy + q[1] * r, r * 0.12f, p)
            }
            29 -> { // reactor
                stroked(p, r * 0.1f) { c.drawCircle(cx, cy, r * 0.88f, p) }
                for (k in 0 until 3) {
                    rc.set(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
                    c.drawArc(rc, -90f + k * 120f - 30f, 60f, true, p)
                }
                val col = p.color; p.color = Color.argb(200, 0, 0, 0); c.drawCircle(cx, cy, r * 0.24f, p); p.color = col
                c.drawCircle(cx, cy, r * 0.15f, p)
            }
            else -> {
                path.reset()
                path.moveTo(cx - r * 0.85f, cy + r * 0.5f); path.lineTo(cx - r * 0.85f, cy - r * 0.2f); path.lineTo(cx - r * 0.4f, cy + r * 0.1f)
                path.lineTo(cx, cy - r * 0.45f); path.lineTo(cx + r * 0.4f, cy + r * 0.1f); path.lineTo(cx + r * 0.85f, cy - r * 0.2f)
                path.lineTo(cx + r * 0.85f, cy + r * 0.5f); path.close(); c.drawPath(path, p)
                poly(cx, cy - r * 0.75f, r * 0.28f, 5, up, 0.42f); c.drawPath(path, p)
            }
        }
    }

    /** Dibuja el emblema completo centrado en (cx, cy) con diámetro `size`. */
    fun draw(c: Canvas, p: Paint, cx: Float, cy: Float, size: Float, shape: Int, sym: Int, cBg: Int, cShape: Int, cSym: Int, alpha: Float = 1f) {
        val r = size / 2f
        val a = (255 * alpha).toInt().coerceIn(0, 255)
        val st = p.style
        p.style = Paint.Style.FILL
        p.color = Color.argb(a, Color.red(cBg), Color.green(cBg), Color.blue(cBg))
        rc.set(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(rc, r * 0.18f, r * 0.18f, p)
        shapePath(shape.coerceIn(0, shapeNames.size - 1), cx, cy, r * 0.82f)
        p.color = Color.argb(a, Color.red(cShape), Color.green(cShape), Color.blue(cShape))
        c.drawPath(path, p)
        p.color = Color.argb(a, Color.red(cSym), Color.green(cSym), Color.blue(cSym))
        symbol(c, p, sym.coerceIn(0, symbolNames.size - 1), cx, cy, r * 0.46f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.04f
        p.color = Color.argb(a * 160 / 255, 255, 255, 255)
        c.drawRoundRect(rc, r * 0.18f, r * 0.18f, p)
        p.style = st
    }
}
