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
import kotlin.random.Random

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

class PlanetData(val bmp: Bitmap, val cities: FloatArray)

object Textures {
    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun cl(v: Float) = v.toInt().coerceIn(0, 255)

    /** 7 tipos x 16 máscaras de vecinos (1 arriba, 2 derecha, 4 abajo, 8 izquierda). */
    fun makeShipTiles(colors: IntArray): Array<Bitmap> =
        Array(colors.size * 16) { makeShip(colors[it / 16], (it / 16) * 17 + 3, it % 16) }

    /**
     * Sección de una nave enemiga. Los lados con vecino continúan el casco; los abiertos llevan borde,
     * las esquinas se achaflanan y los extremos se afinan como proa. Según los vecinos hay lente,
     * reactor, rejillas de motor o placa blindada, y una franja del color de la facción.
     */
    fun makeShip(col: Int, seed: Int, mask: Int): Bitmap {
        val t = 64
        val px = IntArray(t * t)
        val up = mask and 1 != 0
        val right = mask and 2 != 0
        val down = mask and 4 != 0
        val left = mask and 8 != 0
        val nb = (if (up) 1 else 0) + (if (right) 1 else 0) + (if (down) 1 else 0) + (if (left) 1 else 0)
        val corner = nb == 2 && ((up && right) || (right && down) || (down && left) || (left && up))
        val ins = 3f
        val iL = if (left) 0f else ins
        val iR = if (right) 0f else ins
        val iT = if (up) 0f else ins
        val iB = if (down) 0f else ins
        val k = if (nb <= 1) 20f else 13f
        val sd = seed.toFloat() + mask * 0.37f
        val cr = Color.red(col).toFloat(); val cg = Color.green(col).toFloat(); val cb = Color.blue(col).toFloat()
        val h = t / 2f
        val rLens = when {
            nb == 4 -> 17f
            nb == 3 -> 11f
            nb == 1 -> 11f
            nb == 0 -> 14f
            corner -> 6f
            else -> 0f
        }
        for (y in 0 until t) for (x in 0 until t) {
            val fx = x + 0.5f
            val fy = y + 0.5f
            var d = 1000f
            if (!left) d = min(d, fx - iL)
            if (!right) d = min(d, (t - iR) - fx)
            if (!up) d = min(d, fy - iT)
            if (!down) d = min(d, (t - iB) - fy)
            if (!left && !up) d = min(d, ((fx - iL) + (fy - iT) - k) * 0.7071f)
            if (!right && !up) d = min(d, (((t - iR) - fx) + (fy - iT) - k) * 0.7071f)
            if (!left && !down) d = min(d, ((fx - iL) + ((t - iB) - fy) - k) * 0.7071f)
            if (!right && !down) d = min(d, (((t - iR) - fx) + ((t - iB) - fy) - k) * 0.7071f)
            if (nb == 1) {
                val a: Float
                val l: Float
                if (up) { a = fy / t; l = (fx - h) / h }
                else if (down) { a = 1f - fy / t; l = (fx - h) / h }
                else if (left) { a = fx / t; l = (fy - h) / h }
                else { a = 1f - fx / t; l = (fy - h) / h }
                val half = 1f - 0.55f * a.pow(1.4f)
                d = min(d, (half - abs(l)) * h)
            }
            if (d <= -1f) continue
            val alpha = ((d + 1f) / 1.5f).coerceIn(0f, 1f)

            val u = fx / t
            val v = fy / t
            val n = Noise.fbm(x * 0.05f, y * 0.3f, sd, 3)
            val grain = Noise.fbm(x * 0.7f, y * 0.7f, sd + 9f, 2)
            var lum = (1.2f - 0.65f * ((u + v) / 2f)) * (0.75f + 0.4f * n) * (0.9f + 0.2f * grain)
            var r = 66f * lum; var g = 74f * lum; var b = 92f * lum

            // costuras de paneles
            if (d > 6f && ((x % 32) < 1 || (y % 32) < 1)) { r *= 0.55f; g *= 0.55f; b *= 0.6f }
            // remaches
            if (d > 8f) {
                for (rv in 0 until 4) {
                    val rx = if (rv % 2 == 0) 9f else 55f
                    val ry = if (rv < 2) 9f else 55f
                    val dd = sqrt((fx - rx) * (fx - rx) + (fy - ry) * (fy - ry))
                    if (dd < 2.2f) { val f = if (fx + fy < rx + ry) 1.6f else 0.6f; r *= f; g *= f; b *= f }
                    else if (dd < 3f) { r *= 0.5f; g *= 0.5f; b *= 0.5f }
                }
            }
            // franja de facción
            val horiz = (left || right) && !(up || down)
            val vert = (up || down) && !(left || right)
            val cdn = sqrt((fx - h) * (fx - h) + (fy - h) * (fy - h)) / t
            val stripe = when {
                horiz -> abs(v - 0.5f) < 0.06f
                vert -> abs(u - 0.5f) < 0.06f
                else -> cdn > 0.30f && cdn < 0.35f
            }
            if (stripe && d > 3f) { r = cr * 0.9f; g = cg * 0.9f; b = cb * 0.9f }
            // rejillas de motor en cuerpos rectos
            if (nb == 2 && !corner && d > 4f) {
                val vy = if (horiz) min(abs(fy - (iT + 9f)), abs(fy - (t - iB - 9f))) else min(abs(fx - (iL + 9f)), abs(fx - (t - iR - 9f)))
                val along = if (horiz) fx else fy
                if (vy < 1.8f && along > 14f && along < 50f) {
                    if (vy < 0.7f) { r = 255f; g = 140f; b = 50f } else { r = 18f; g = 18f; b = 24f }
                }
            }
            // lente / reactor
            if (rLens > 0f) {
                val cd = cdn * t
                if (cd < rLens + 3f) {
                    if (cd > rLens) { r *= 0.3f; g *= 0.3f; b *= 0.35f }
                    else {
                        val kk = (1f - cd / rLens).coerceIn(0f, 1f).pow(1.1f)
                        r = 70f + 185f * kk
                        g = 14f + 90f * kk * kk
                        b = 14f + 30f * kk * kk * kk
                        val hl = smooth(rLens * 0.35f, 0f, sqrt((fx - h + rLens * 0.3f) * (fx - h + rLens * 0.3f) + (fy - h + rLens * 0.3f) * (fy - h + rLens * 0.3f)))
                        r += 120f * hl; g += 120f * hl; b += 110f * hl
                    }
                }
            }
            // borde: contorno oscuro y biselado iluminado arriba-izquierda
            if (d < 1.6f) { r *= 0.3f; g *= 0.3f; b *= 0.35f }
            else if (d < 4f) {
                val f = if (min(fx, fy) < min(t - fx, t - fy)) 1.45f else 0.6f
                r *= f; g *= f; b *= f
            }
            px[y * t + x] = Color.argb(cl(255f * alpha), cl(r), cl(g), cl(b))
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
     * Planeta tipo Tierra en esfera 3D: océanos, continentes con relieve, casquetes polares, nubes,
     * ciudades iluminadas de noche, brillo especular, terminador y resplandor atmosférico.
     * Devuelve también posiciones de ciudades (normalizadas al disco, -1..1) para efectos; la primera
     * es la más cercana al centro y sirve de epicentro.
     */
    fun makePlanet(radius: Int): PlanetData {
        val d = radius * 2
        val px = IntArray(d * d)
        var lx = -0.62f; var ly = -0.38f; var lz = 0.68f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= ll; ly /= ll; lz /= ll
        var hx = lx; var hy = ly; var hz = lz + 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        hx /= hl; hy /= hl; hz /= hl
        val cand = ArrayList<FloatArray>()
        for (y in 0 until d) for (x in 0 until d) {
            val nx = (x + 0.5f - radius) / radius
            val ny = (y + 0.5f - radius) / radius
            val rr = nx * nx + ny * ny
            if (rr >= 1.03f) continue
            val edge = if (rr <= 1f) 1f else (1.03f - rr) / 0.03f
            val nz = sqrt(max(0f, 1f - rr))
            val qx = nx * 0.92f
            val qy = ny * 0.92f + nz * 0.39f
            val qz = nz * 0.92f - ny * 0.39f
            val sx = qx * 1.7f + 3.1f; val sy = qy * 1.7f + 1.7f; val sz = qz * 1.7f + 5.3f
            val c = Noise.fbm(sx, sy, sz, 6)
            val c2 = Noise.fbm(sx + lx * 0.05f, sy + ly * 0.05f, sz + lz * 0.05f, 6)
            val moist = Noise.fbm(sx * 2f + 11f, sy * 2f, sz * 2f, 4)
            val lat = abs(qy)
            val land = smooth(0.50f, 0.535f, c)
            var r: Float; var g: Float; var b: Float
            val depth = smooth(0.30f, 0.52f, c)
            val orr = 4f + 24f * depth; val og = 22f + 78f * depth; val ob = 62f + 90f * depth
            val e = ((c - 0.535f) / 0.25f).coerceIn(0f, 1f)
            var lr: Float; var lg: Float; var lb: Float
            if (moist < 0.45f && lat < 0.62f) { lr = 176f; lg = 148f; lb = 96f }
            else { lr = 44f; lg = 92f; lb = 40f }
            lr += (125f - lr) * e * 0.8f; lg += (118f - lg) * e * 0.8f; lb += (108f - lb) * e * 0.8f
            // relieve: sombreado según la pendiente en dirección de la luz
            val bump = ((c - c2) * 16f).coerceIn(-0.4f, 0.4f) * land
            r = orr + (lr - orr) * land; g = og + (lg - og) * land; b = ob + (lb - ob) * land
            r *= 1f + bump; g *= 1f + bump; b *= 1f + bump
            val ice = smooth(0.80f, 0.90f, lat + (Noise.fbm(sx * 3f, sy * 3f, sz * 3f, 3) - 0.5f) * 0.18f)
            r += (238f - r) * ice; g += (243f - g) * ice; b += (252f - b) * ice
            val oceanMask = (1f - land) * (1f - ice)
            val cn = Noise.fbm(sx * 1.5f + 40f, sy * 2.4f, sz * 1.5f + 7f, 5)
            val cloud = smooth(0.50f, 0.72f, cn) * 0.88f
            val dot = nx * lx + ny * ly + nz * lz
            val day = smooth(-0.10f, 0.40f, dot)
            val shadeV = 0.03f + 0.97f * day
            r = (r * (1f - cloud) + 245f * cloud) * shadeV
            g = (g * (1f - cloud) + 247f * cloud) * shadeV
            b = (b * (1f - cloud) + 250f * cloud) * shadeV
            val cityRaw = Noise.fbm(sx * 9f, sy * 9f, sz * 9f, 3)
            val cities = smooth(0.60f, 0.72f, cityRaw) * land * (1f - ice) * (1f - cloud) * (1f - day)
            r += 255f * cities * 0.95f; g += 190f * cities * 0.95f; b += 100f * cities * 0.95f
            if (cityRaw > 0.70f && land > 0.95f && ice < 0.1f && nz > 0.3f && ((x * 7 + y * 13) % 41 == 0)) {
                cand.add(floatArrayOf(nx, ny))
            }
            val sp = max(0f, nx * hx + ny * hy + nz * hz).pow(80f) * oceanMask * (1f - cloud) * 0.85f
            r += 255f * sp; g += 255f * sp; b += 240f * sp
            val limb = 0.55f + 0.45f * sqrt(nz)
            r *= limb; g *= limb; b *= limb
            val fres = (1f - nz).pow(2.6f)
            val atm = fres * (0.18f + 0.82f * day)
            r += 70f * atm * 1.4f; g += 140f * atm * 1.4f; b += 255f * atm * 1.4f
            px[y * d + x] = Color.argb(cl(255f * edge), cl(r), cl(g), cl(b))
        }
        if (cand.isEmpty()) cand.add(floatArrayOf(0.1f, 0.1f))
        cand.sortBy { it[0] * it[0] + it[1] * it[1] }
        val first = cand[0]
        val rest = ArrayList<FloatArray>(cand.subList(1, cand.size))
        rest.shuffle(Random(7))
        val out = ArrayList<FloatArray>()
        out.add(first)
        for (i in 0 until min(45, rest.size)) out.add(rest[i])
        val flat = FloatArray(out.size * 2)
        for (i in out.indices) { flat[i * 2] = out[i][0]; flat[i * 2 + 1] = out[i][1] }
        return PlanetData(Bitmap.createBitmap(px, d, d, Bitmap.Config.ARGB_8888), flat)
    }
}
