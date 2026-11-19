package com.cristal.abismo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Ruido de valor 3D y fBm para texturas procedurales. */
object Noise {
    private fun h(x: Int, y: Int, z: Int): Float {
        var n = x * 374761393 + y * 668265263 + z * 1274126177
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFFFF) / 16777215f
    }

    private fun fade(t: Float) = t * t * (3f - 2f * t)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun n3(x: Float, y: Float, z: Float): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val zi = floor(z).toInt()
        val u = fade(x - xi); val v = fade(y - yi); val w = fade(z - zi)
        val a = lerp(h(xi, yi, zi), h(xi + 1, yi, zi), u)
        val b = lerp(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), u)
        val c = lerp(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), u)
        val d = lerp(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), u)
        return lerp(lerp(a, b, v), lerp(c, d, v), w)
    }

    fun fbm(x: Float, y: Float, z: Float, oct: Int): Float {
        var amp = 0.5f; var f = 1f; var sum = 0f; var tot = 0f
        for (i in 0 until oct) {
            sum += amp * n3(x * f, y * f, z * f)
            tot += amp
            amp *= 0.5f; f *= 2.03f
        }
        return sum / tot
    }
}

object Textures {
    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun cl(v: Float) = v.toInt().coerceIn(0, 255)

    /** Placa de casco metálica con remaches, biselado y núcleo de energía incandescente. */
    fun makeTile(col: Int, seed: Int): Bitmap {
        val t = 96
        val px = IntArray(t * t)
        val r0 = Color.red(col) * 0.72f + 170f * 0.28f
        val g0 = Color.green(col) * 0.72f + 170f * 0.28f
        val b0 = Color.blue(col) * 0.72f + 170f * 0.28f
        val sd = seed.toFloat()
        val rivets = arrayOf(floatArrayOf(14f, 14f), floatArrayOf(81f, 14f), floatArrayOf(14f, 81f), floatArrayOf(81f, 81f))
        for (y in 0 until t) for (x in 0 until t) {
            val u = x / (t - 1f)
            val v = y / (t - 1f)
            val n = Noise.fbm(x * 0.03f, y * 0.35f, sd, 3)
            val grain = Noise.fbm(x * 0.5f, y * 0.5f, sd + 9f, 2)
            var lum = (1.15f - 0.6f * ((u + v) / 2f)) * (0.78f + 0.35f * n) * (0.92f + 0.16f * grain)
            val d = min(min(x, y), min(t - 1 - x, t - 1 - y))
            if (d < 5) lum *= if (d == x || d == y) 1.4f else 0.55f
            else if (d in 8..9) lum *= 0.55f
            for (rv in rivets) {
                val dx = x - rv[0]; val dy = y - rv[1]
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 4.5f) lum *= if (dx + dy < 0f) 1.6f else 0.6f
                else if (dist < 5.6f) lum *= 0.45f
            }
            var r = r0 * lum; var g = g0 * lum; var b = b0 * lum
            val cdx = x - 47.5f; val cdy = y - 47.5f
            val cd = sqrt(cdx * cdx + cdy * cdy)
            if (cd < 21f) {
                val k = (1f - cd / 19f).coerceIn(0f, 1f)
                val kk = k.pow(1.3f) * 0.96f
                val er = Color.red(col) + (255 - Color.red(col)) * 0.65f
                val eg = Color.green(col) + (255 - Color.green(col)) * 0.65f
                val eb = Color.blue(col) + (255 - Color.blue(col)) * 0.65f
                val white = smooth(9f, 0f, cd)
                r = r + (er - r) * kk + 255f * white * 0.5f
                g = g + (eg - g) * kk + 255f * white * 0.5f
                b = b + (eb - b) * kk + 255f * white * 0.5f
                if (cd > 18f) { r *= 0.5f; g *= 0.5f; b *= 0.5f }
            }
            px[y * t + x] = Color.rgb(cl(r), cl(g), cl(b))
        }
        return Bitmap.createBitmap(px, t, t, Bitmap.Config.ARGB_8888)
    }

    /** Halo suave para dar brillo (se dibuja con mezcla aditiva). */
    fun makeGlow(col: Int): Bitmap {
        val sz = 96
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            sz / 2f, sz / 2f, sz / 2f,
            intArrayOf(Color.argb(255, Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(90, Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        Canvas(bmp).drawRect(0f, 0f, sz.toFloat(), sz.toFloat(), p)
        return bmp
    }

    /** Planeta volcánico: terreno fBm 3D proyectado en esfera, grietas de lava, bruma, iluminación y atmósfera. */
    fun makePlanet(radius: Int): Bitmap {
        val d = radius * 2
        val px = IntArray(d * d)
        var lx = -0.55f; var ly = -0.5f; var lz = 0.67f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= ll; ly /= ll; lz /= ll
        for (y in 0 until d) for (x in 0 until d) {
            val nx = (x + 0.5f - radius) / radius
            val ny = (y + 0.5f - radius) / radius
            val rr = nx * nx + ny * ny
            if (rr >= 1.04f) continue
            val edge = if (rr <= 1f) 1f else (1.04f - rr) / 0.04f
            val nz = sqrt(max(0f, 1f - rr))
            val qx = nx
            val qy = ny * 0.94f + nz * 0.34f
            val qz = nz * 0.94f - ny * 0.34f
            val sx = qx * 2.2f + 3.1f; val sy = qy * 2.2f + 1.7f; val sz = qz * 2.2f + 5.3f
            val hgt = Noise.fbm(sx, sy, sz, 6)
            val ridge = 1f - abs(2f * Noise.fbm(sx * 2.1f + 7f, sy * 2.1f, sz * 2.1f, 4) - 1f)
            val haze = Noise.fbm(sx * 1.3f + 20f, sy * 3f, sz * 1.3f, 4)
            var r: Float; var g: Float; var b: Float
            if (hgt < 0.47f) {
                val k = hgt / 0.47f
                r = 34f + 50f * k; g = 16f + 26f * k; b = 14f + 20f * k
            } else {
                val k = ((hgt - 0.47f) / 0.4f).coerceIn(0f, 1f)
                r = 150f + 95f * k; g = 64f + 100f * k; b = 30f + 60f * k
            }
            val hz = smooth(0.55f, 0.75f, haze) * 0.45f
            r += (230f - r) * hz; g += (190f - g) * hz; b += (150f - b) * hz
            val diff = max(0f, nx * lx + ny * ly + nz * lz)
            val lit = (0.10f + diff.pow(0.8f)) * (0.85f + 0.3f * hgt)
            r *= lit; g *= lit; b *= lit
            val lava = smooth(0.90f, 0.985f, ridge) * (if (hgt < 0.52f) 1f else 0.25f)
            r += 255f * lava; g += 110f * lava; b += 20f * lava
            val rim = (1f - nz).pow(3f) * 0.5f * (0.3f + diff)
            r += 255f * rim; g += 130f * rim; b += 70f * rim
            px[y * d + x] = Color.argb(cl(255f * edge), cl(r), cl(g), cl(b))
        }
        return Bitmap.createBitmap(px, d, d, Bitmap.Config.ARGB_8888)
    }
}
