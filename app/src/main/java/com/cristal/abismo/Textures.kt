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

    /** Placa de casco de nave enemiga: metal oscuro, paneles, ventanas, franja de color y motor rojo. */
    fun makeTile(col: Int, seed: Int): Bitmap {
        val t = 96
        val px = IntArray(t * t)
        val sd = seed.toFloat()
        val cr = Color.red(col).toFloat(); val cg = Color.green(col).toFloat(); val cb = Color.blue(col).toFloat()
        val wins = arrayOf(
            intArrayOf(17, 24), intArrayOf(77, 24), intArrayOf(17, 71), intArrayOf(77, 71),
            intArrayOf(47, 17), intArrayOf(47, 78)
        )
        val lit = BooleanArray(wins.size) { Noise.n3(it * 1.7f, sd, 0.5f) > 0.38f }
        for (y in 0 until t) for (x in 0 until t) {
            val u = x / (t - 1f)
            val v = y / (t - 1f)
            val n = Noise.fbm(x * 0.04f, y * 0.4f, sd, 3)
            val grain = Noise.fbm(x * 0.6f, y * 0.6f, sd + 9f, 2)
            // casco gris azulado con iluminación diagonal
            var lum = (1.2f - 0.65f * ((u + v) / 2f)) * (0.75f + 0.4f * n) * (0.9f + 0.2f * grain)
            val panel = (x / 32) + (y / 32) * 3
            lum *= 0.9f + 0.2f * Noise.n3(panel.toFloat(), sd, 2.2f)
            var r = 66f * lum; var g = 74f * lum; var b = 90f * lum
            // costuras de paneles
            val sx = x % 32; val sy = y % 32
            if ((sx < 2 || sy < 2) && x > 5 && y > 5 && x < t - 6 && y < t - 6) { r *= 0.45f; g *= 0.45f; b *= 0.5f }
            val d = min(min(x, y), min(t - 1 - x, t - 1 - y))
            // biselado
            if (d < 3) { val f = if (d == x || d == y) 1.5f else 0.5f; r *= f; g *= f; b *= f }
            // franja de color de la facción
            else if (d in 4..7) {
                val k = if (d == 5 || d == 6) 1f else 0.55f
                r = cr * 0.85f * k + r * (1f - k) * 0.3f
                g = cg * 0.85f * k + g * (1f - k) * 0.3f
                b = cb * 0.85f * k + b * (1f - k) * 0.3f
            }
            // ventanas
            for (i in wins.indices) {
                val dx = x - wins[i][0]; val dy = y - wins[i][1]
                if (abs(dx) <= 3 && abs(dy) <= 2) {
                    if (lit[i]) { r = 255f; g = 230f; b = 150f } else { r *= 0.35f; g *= 0.35f; b *= 0.4f }
                }
            }
            // domo del motor
            val cdx = x - 47.5f; val cdy = y - 47.5f
            val cd = sqrt(cdx * cdx + cdy * cdy)
            if (cd < 22f) {
                if (cd > 18.5f) { r *= 0.35f; g *= 0.35f; b *= 0.4f }
                else {
                    val k = (1f - cd / 18.5f).coerceIn(0f, 1f).pow(1.2f)
                    r = 70f + 185f * k
                    g = 14f + 90f * k * k
                    b = 14f + 30f * k * k * k
                    val hl = smooth(7f, 0f, sqrt((x - 41f) * (x - 41f) + (y - 41f) * (y - 41f)))
                    r += 120f * hl; g += 120f * hl; b += 110f * hl
                }
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

    /** Polvo estelar: manchas muy suaves de azul/violeta con poco alfa. */
    fun makeHaze(w: Int, h: Int): Bitmap {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val n = Noise.fbm(x * 0.035f, y * 0.035f, 4.2f, 5)
            val m = Noise.fbm(x * 0.05f + 30f, y * 0.05f, 9f, 3)
            val a = smooth(0.38f, 0.78f, n) * 70f
            val r = 90f + 90f * m
            val g = 110f + 40f * m
            val b = 230f - 40f * m
            px[y * w + x] = Color.argb(cl(a), cl(r), cl(g), cl(b))
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Planeta tipo Tierra visto desde el espacio: océanos, continentes, casquetes polares, nubes,
     * luces de ciudades en la noche, brillo especular, terminador y resplandor atmosférico en el borde.
     */
    fun makePlanet(radius: Int): Bitmap {
        val d = radius * 2
        val px = IntArray(d * d)
        var lx = -0.62f; var ly = -0.38f; var lz = 0.68f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= ll; ly /= ll; lz /= ll
        // vector medio para el brillo especular (vista = +z)
        var hx = lx; var hy = ly; var hz = lz + 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        hx /= hl; hy /= hl; hz /= hl
        for (y in 0 until d) for (x in 0 until d) {
            val nx = (x + 0.5f - radius) / radius
            val ny = (y + 0.5f - radius) / radius
            val rr = nx * nx + ny * ny
            if (rr >= 1.03f) continue
            val edge = if (rr <= 1f) 1f else (1.03f - rr) / 0.03f
            val nz = sqrt(max(0f, 1f - rr))
            // inclinación del eje (~23°)
            val qx = nx * 0.92f + ny * 0.39f * 0.0f
            val qy = ny * 0.92f + nz * 0.39f
            val qz = nz * 0.92f - ny * 0.39f
            val sx = qx * 1.7f + 3.1f; val sy = qy * 1.7f + 1.7f; val sz = qz * 1.7f + 5.3f
            val c = Noise.fbm(sx, sy, sz, 6)
            val moist = Noise.fbm(sx * 2f + 11f, sy * 2f, sz * 2f, 4)
            val lat = abs(qy)
            val land = smooth(0.50f, 0.535f, c)
            var r: Float; var g: Float; var b: Float
            // océano: de somero a profundo
            val depth = smooth(0.30f, 0.52f, c)
            val or = 4f + 24f * depth; val og = 22f + 78f * depth; val ob = 62f + 90f * depth
            // tierra: verde, seco, montaña
            val e = ((c - 0.535f) / 0.25f).coerceIn(0f, 1f)
            var lr: Float; var lg: Float; var lb: Float
            if (moist < 0.45f && lat < 0.62f) { lr = 176f; lg = 148f; lb = 96f }
            else { lr = 44f; lg = 92f; lb = 40f }
            lr += (125f - lr) * e * 0.8f; lg += (118f - lg) * e * 0.8f; lb += (108f - lb) * e * 0.8f
            r = or + (lr - or) * land; g = og + (lg - og) * land; b = ob + (lb - ob) * land
            // hielo polar
            val ice = smooth(0.80f, 0.90f, lat + (Noise.fbm(sx * 3f, sy * 3f, sz * 3f, 3) - 0.5f) * 0.18f)
            r += (238f - r) * ice; g += (243f - g) * ice; b += (252f - b) * ice
            val oceanMask = (1f - land) * (1f - ice)
            // nubes
            val cn = Noise.fbm(sx * 1.5f + 40f, sy * 2.4f, sz * 1.5f + 7f, 5)
            val cloud = smooth(0.50f, 0.72f, cn) * 0.88f
            // iluminación
            val dot = nx * lx + ny * ly + nz * lz
            val day = smooth(-0.10f, 0.40f, dot)
            val shadeV = 0.03f + 0.97f * day
            val dr = (r * (1f - cloud) + 245f * cloud) * shadeV
            val dg = (g * (1f - cloud) + 247f * cloud) * shadeV
            val db = (b * (1f - cloud) + 250f * cloud) * shadeV
            r = dr; g = dg; b = db
            // luces de ciudades en la noche, solo sobre tierra sin nubes densas
            val cities = smooth(0.60f, 0.72f, Noise.fbm(sx * 9f, sy * 9f, sz * 9f, 3)) * land * (1f - ice) * (1f - cloud) * (1f - day)
            r += 255f * cities * 0.95f; g += 190f * cities * 0.95f; b += 100f * cities * 0.95f
            // brillo especular en el mar
            val sp = max(0f, nx * hx + ny * hy + nz * hz).pow(80f) * oceanMask * (1f - cloud) * 0.85f
            r += 255f * sp; g += 255f * sp; b += 240f * sp
            // oscurecimiento en el limbo y resplandor atmosférico (Fresnel)
            val limb = 0.55f + 0.45f * sqrt(nz)
            r *= limb; g *= limb; b *= limb
            val fres = (1f - nz).pow(2.6f)
            val atm = fres * (0.18f + 0.82f * day)
            r += 70f * atm * 1.4f; g += 140f * atm * 1.4f; b += 255f * atm * 1.4f
            px[y * d + x] = Color.argb(cl(255f * edge), cl(r), cl(g), cl(b))
        }
        return Bitmap.createBitmap(px, d, d, Bitmap.Config.ARGB_8888)
    }
}
