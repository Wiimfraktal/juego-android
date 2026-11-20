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
        // floor rápido sin pasar por double (mismo resultado que floor)
        var xi = x.toInt(); if (x < xi) xi--
        var yi = y.toInt(); if (y < yi) yi--
        var zi = z.toInt(); if (z < zi) zi--
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

    /** 7 tipos x 16 máscaras de vecinos (1 arriba, 2 derecha, 4 abajo, 8 izquierda), con el aspecto de una facción. */
    fun makeShipTiles(colors: IntArray, f: Faction): Array<Bitmap> {
        val n = colors.size * 16
        val out = arrayOfNulls<Bitmap>(n)
        val next = java.util.concurrent.atomic.AtomicInteger(0)
        val nt = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val ths = Array(nt) {
            Thread {
                while (true) {
                    val i = next.getAndIncrement()
                    if (i >= n) break
                    out[i] = makeShip(f.accent(colors[i / 16]), (i / 16) * 17 + 3, i % 16, f)
                }
            }.also { t -> t.start() }
        }
        for (t in ths) t.join()
        return Array(n) { out[it]!! }
    }

    /**
     * Sección de nave con iluminación real: un campo de alturas (biseles, juntas de paneles, remaches, cúpulas)
     * genera normales y se ilumina con difuso, especular, reflejo metálico y oclusión. Las luces son emisivas.
     * Los lados con vecino continúan el casco; las puntas se afinan como proa y las esquinas se achaflanan.
     */
    fun makeShip(accent: Int, seed: Int, mask: Int, f: Faction): Bitmap {
        val t = 64f
        val big = 128
        val px = IntArray(big * big)
        val up = mask and 1 != 0
        val right = mask and 2 != 0
        val down = mask and 4 != 0
        val left = mask and 8 != 0
        val nb = (if (up) 1 else 0) + (if (right) 1 else 0) + (if (down) 1 else 0) + (if (left) 1 else 0)
        val corner = nb == 2 && ((up && right) || (right && down) || (down && left) || (left && up))
        val ins = 2f
        val iL = if (left) 0f else ins
        val iR = if (right) 0f else ins
        val iT = if (up) 0f else ins
        val iB = if (down) 0f else ins
        val k = if (nb <= 1) 9f else 6f
        val sd = seed.toFloat() + mask * 0.37f
        val h = t / 2f
        val rLens = when {
            nb == 4 -> 17f
            nb == 3 -> 11f
            nb == 1 -> 11f
            nb == 0 -> 14f
            corner -> 6f
            else -> 0f
        }
        val horiz = (left || right) && !(up || down)
        val vert = (up || down) && !(left || right)
        val acR = Color.red(accent).toFloat(); val acG = Color.green(accent).toFloat(); val acB = Color.blue(accent).toFloat()
        val huR = Color.red(f.hull).toFloat(); val huG = Color.green(f.hull).toFloat(); val huB = Color.blue(f.hull).toFloat()
        val lnR = Color.red(f.lens).toFloat(); val lnG = Color.green(f.lens).toFloat(); val lnB = Color.blue(f.lens).toFloat()
        val wnR = Color.red(f.window).toFloat(); val wnG = Color.green(f.window).toFloat(); val wnB = Color.blue(f.window).toFloat()
        val fbR = Color.red(f.flameB).toFloat(); val fbG = Color.green(f.flameB).toFloat(); val fbB = Color.blue(f.flameB).toFloat()
        val ll = sqrt(0.5f * 0.5f + 0.6f * 0.6f + 0.62f * 0.62f)
        val lx = -0.5f / ll; val ly = -0.6f / ll; val lz = 0.62f / ll
        val hl = sqrt(lx * lx + ly * ly + (lz + 1f) * (lz + 1f))
        val hx = lx / hl; val hy = ly / hl; val hz = (lz + 1f) / hl
        val wins = arrayOf(
            intArrayOf(17, 24), intArrayOf(77, 24), intArrayOf(17, 71), intArrayOf(77, 71),
            intArrayOf(47, 17), intArrayOf(47, 78)
        )
        val lit = BooleanArray(wins.size) { Noise.n3(it * 1.7f, sd, 0.5f) > 0.38f }

        fun silDist(fx: Float, fy: Float): Float {
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
            return d
        }

        fun hgt(fx: Float, fy: Float): Float {
            val d = silDist(fx, fy)
            var hh = 0.55f * smooth(0f, 4.5f, d) + 0.45f * smooth(0f, 12f, d)
            if (d > 6f) {
                val sxm = fx % 32f; val sym = fy % 32f
                val gd = min(min(sxm, 32f - sxm), min(sym, 32f - sym))
                hh -= 0.22f * (1f - smooth(0.4f, 1.8f, gd))
            }
            if (d > 8f) {
                for (rv in 0 until 4) {
                    val rx = if (rv % 2 == 0) 9f else 55f
                    val ry = if (rv < 2) 9f else 55f
                    val dd = sqrt((fx - rx) * (fx - rx) + (fy - ry) * (fy - ry))
                    hh += 0.25f * (1f - smooth(1.5f, 3.2f, dd))
                }
            }
            if (rLens > 0f) {
                val cd = sqrt((fx - h) * (fx - h) + (fy - h) * (fy - h))
                if (cd < rLens) hh += 0.9f * sqrt(max(0f, 1f - (cd / rLens) * (cd / rLens)))
                else if (cd < rLens + 3f) hh -= 0.25f
            }
            return hh
        }

        for (y in 0 until big) for (x in 0 until big) {
            val fx = (x + 0.5f) * t / big
            val fy = (y + 0.5f) * t / big
            val d = silDist(fx, fy)
            if (d <= -1f) continue
            val alpha = ((d + 1f) / 1.5f).coerceIn(0f, 1f)
            val e = 0.6f
            val h0 = hgt(fx, fy)
            var nx = -(hgt(fx + e, fy) - hgt(fx - e, fy)) / (2f * e) * 5f
            var ny = -(hgt(fx, fy + e) - hgt(fx, fy - e)) / (2f * e) * 5f
            val nl = sqrt(nx * nx + ny * ny + 1f)
            nx /= nl; ny /= nl
            val nz = 1f / nl

            // albedo del casco con desgaste
            val n = Noise.fbm(fx * 0.05f, fy * 0.3f, sd, 3)
            val grain = Noise.fbm(fx * 0.7f, fy * 0.7f, sd + 9f, 2)
            var lum = (0.8f + 0.4f * n) * (0.9f + 0.2f * grain)
            lum *= 0.82f + 0.36f * Noise.fbm(fx * 0.9f, fy * 0.05f, sd + 4f, 3)
            val panel = (fx / 32f).toInt() + (fy / 32f).toInt() * 3
            lum *= 0.9f + 0.2f * Noise.n3(panel.toFloat(), sd, 2.2f)
            var r = huR * lum; var g = huG * lum; var b = huB * lum
            when (f.special) {
                1 -> {
                    val cell = 2.2f
                    val uu = (fx % cell) / cell
                    val par = ((fx / cell).toInt() + (fy / cell).toInt()) % 2 == 0
                    val wv = 0.7f + 0.3f * (if (par) uu else 1f - uu)
                    r *= wv; g *= wv; b *= wv
                }
                2 -> {
                    val cn = Noise.fbm(fx * 0.12f + sd, fy * 0.12f, 3.3f, 3)
                    if (cn > 0.56f) { r *= 0.62f; g *= 0.78f; b *= 0.55f }
                    else if (cn < 0.42f) { r *= 1.35f; g *= 1.2f; b *= 0.9f }
                }
                3 -> {
                    val ia = (nx + ny) * 5f + fx * 0.04f
                    r *= 0.75f + 0.45f * kotlin.math.sin(ia)
                    g *= 0.75f + 0.45f * kotlin.math.sin(ia + 2.1f)
                    b *= 0.75f + 0.45f * kotlin.math.sin(ia + 4.2f)
                }
            }
            val cdn = sqrt((fx - h) * (fx - h) + (fy - h) * (fy - h)) / t
            var er = 0f; var eg = 0f; var eb = 0f
            // franja de facción
            val stripe = when {
                horiz -> abs(fy - h) < 3.8f
                vert -> abs(fx - h) < 3.8f
                else -> cdn > 0.30f && cdn < 0.35f
            }
            if (stripe && d > 3f) {
                r = acR * 0.8f; g = acG * 0.8f; b = acB * 0.8f
                er += acR * 0.22f; eg += acG * 0.22f; eb += acB * 0.22f
            }
            // ventanas
            for (i in wins.indices) {
                val dx = fx - wins[i][0] * t / 64f
                val dy = fy - wins[i][1] * t / 64f
                if (abs(dx) <= 3f && abs(dy) <= 2f) {
                    if (lit[i]) { r = wnR * 0.4f; g = wnG * 0.4f; b = wnB * 0.4f; er += wnR; eg += wnG; eb += wnB }
                    else { r *= 0.3f; g *= 0.3f; b *= 0.35f }
                }
            }
            // rejillas de motor en cuerpos rectos
            if (nb == 2 && !corner && d > 4f) {
                val vy = if (horiz) min(abs(fy - (iT + 9f)), abs(fy - (t - iB - 9f))) else min(abs(fx - (iL + 9f)), abs(fx - (t - iR - 9f)))
                val along = if (horiz) fx else fy
                if (vy < 1.8f && along > 14f && along < 50f) {
                    r = 16f; g = 16f; b = 22f
                    if (vy < 0.7f) { er += fbR; eg += fbG; eb += fbB }
                }
            }
            // lente / reactor
            if (rLens > 0f) {
                val cd = cdn * t
                if (cd < rLens) {
                    val kk = (1f - cd / rLens).coerceIn(0f, 1f).pow(1.1f)
                    r = lnR * 0.3f; g = lnG * 0.3f; b = lnB * 0.3f
                    er += lnR * (0.35f + 0.9f * kk); eg += lnG * (0.35f + 0.9f * kk); eb += lnB * (0.35f + 0.9f * kk)
                } else if (cd < rLens + 3f) { r *= 0.3f; g *= 0.3f; b *= 0.35f }
            }
            // iluminación
            val ndl = max(0f, nx * lx + ny * ly + nz * lz)
            val ndh = max(0f, nx * hx + ny * hy + nz * hz)
            val spec = ndh.pow(18f + 70f * f.gloss) * (0.12f + 0.9f * f.gloss)
            val ao = 0.7f + 0.3f * smooth(0f, 0.55f, h0)
            val env = f.metal * (0.5f - 0.5f * ny)
            val shade = (0.26f + 0.9f * ndl) * ao
            var sp = 0f
            if (f.sparkle > 0f && Noise.fbm(fx * 2.5f + 20f, fy * 2.5f, sd + 11f, 2) > 0.78f) sp = f.sparkle
            val tr = huR / 255f; val tg = huG / 255f; val tb = huB / 255f
            r = r * shade + 255f * spec * (0.55f + 0.45f * f.metal * tr) + 70f * env * (tr + 0.3f) + 255f * sp * ndl + er
            g = g * shade + 255f * spec * (0.55f + 0.45f * f.metal * tg) + 70f * env * (tg + 0.3f) + 255f * sp * ndl + eg
            b = b * shade + 255f * spec * (0.55f + 0.45f * f.metal * tb) + 70f * env * (tb + 0.3f) + 255f * sp * ndl + eb
            if (d < 1.2f) { r *= 0.35f; g *= 0.35f; b *= 0.4f }
            px[y * big + x] = Color.argb(cl(255f * alpha), cl(r), cl(g), cl(b))
        }
        val bmp = Bitmap.createBitmap(px, big, big, Bitmap.Config.ARGB_8888)
        bmp.setHasMipMap(true)
        return bmp
    }

    /** Transformada de distancia (chaflán 1 / 1.414): distancia de cada píxel "dentro" al píxel "fuera" más cercano. */
    private fun distField(inside: BooleanArray, w: Int, h: Int, oobInside: Boolean): FloatArray {
        val big = 1e6f
        val d = FloatArray(w * h) { if (inside[it]) big else 0f }
        val ob = if (oobInside) big else 0f
        fun at(x: Int, y: Int) = if (x < 0 || y < 0 || x >= w || y >= h) ob else d[y * w + x]
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (d[i] == 0f) continue
            var v = d[i]
            v = min(v, at(x - 1, y) + 1f)
            v = min(v, at(x, y - 1) + 1f)
            v = min(v, at(x - 1, y - 1) + 1.4142f)
            v = min(v, at(x + 1, y - 1) + 1.4142f)
            d[i] = v
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (d[i] == 0f) continue
            var v = d[i]
            v = min(v, at(x + 1, y) + 1f)
            v = min(v, at(x, y + 1) + 1f)
            v = min(v, at(x + 1, y + 1) + 1.4142f)
            v = min(v, at(x - 1, y + 1) + 1.4142f)
            d[i] = v
        }
        return d
    }

    /** 7 tipos x 4 rotaciones de naves completas para una facción (en paralelo). */
    fun makeShipSprites(colors: IntArray, f: Faction): Array<Bitmap> {
        val n = 28
        val out = arrayOfNulls<Bitmap>(n)
        val next = java.util.concurrent.atomic.AtomicInteger(0)
        val nt = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val ths = Array(nt) {
            Thread {
                while (true) {
                    val i = next.getAndIncrement()
                    if (i >= n) break
                    out[i] = makeShipSprite(i / 4, i % 4, f.accent(colors[i / 4]), f)
                }
            }.also { th -> th.start() }
        }
        for (th in ths) th.join()
        return Array(n) { out[it]!! }
    }

    /**
     * Nave completa (no placas por celda): silueta de la unión de sus celdas con esquinas exteriores redondeadas,
     * casco continuo con paneles a lo largo del eje, una cabina al frente, dos toberas atrás, franjas de facción,
     * ventanas, luces de navegación en las alas y relieve iluminado (difuso, especular, reflejo, oclusión).
     */
    fun makeShipSprite(type: Int, rot: Int, accent: Int, f: Faction): Bitmap {
        val cells = Shapes.cells(type, rot)
        var mnx = 9; var mny = 9; var mxx = -9; var mxy = -9
        for (c in cells) { mnx = min(mnx, c[0]); mny = min(mny, c[1]); mxx = max(mxx, c[0]); mxy = max(mxy, c[1]) }
        val cw = mxx - mnx + 1
        val ch = mxy - mny + 1
        val cp = 96
        val w = cw * cp
        val h = ch * cp
        val sc = 64f / cp
        val occ = BooleanArray(w * h)
        val cellOcc = Array(ch) { BooleanArray(cw) }
        for (c in cells) cellOcc[c[1] - mny][c[0] - mnx] = true
        for (y in 0 until h) for (x in 0 until w) occ[y * w + x] = cellOcc[y / cp][x / cp]
        val rr = 20f
        val d1 = distField(occ, w, h, false)
        val eroded = BooleanArray(w * h) { d1[it] > rr }
        val d2 = distField(BooleanArray(w * h) { !eroded[it] }, w, h, true)
        val lim = rr - 3f
        val sil = BooleanArray(w * h) { d2[it] <= lim }
        val d3 = distField(sil, w, h, false)

        // ejes de la nave: columna principal (la fila/columna con más celdas)
        val horiz = cw >= ch
        var spineIdx = 0; var best = -1
        if (horiz) {
            for (r in 0 until ch) { val k = cellOcc[r].count { it }; if (k > best) { best = k; spineIdx = r } }
        } else {
            for (q in 0 until cw) { var k = 0; for (r in 0 until ch) if (cellOcc[r][q]) k++; if (k > best) { best = k; spineIdx = q } }
        }
        var sMin = 99; var sMax = -1
        if (horiz) { for (q in 0 until cw) if (cellOcc[spineIdx][q]) { sMin = min(sMin, q); sMax = max(sMax, q) } }
        else { for (r in 0 until ch) if (cellOcc[r][spineIdx]) { sMin = min(sMin, r); sMax = max(sMax, r) } }
        val spine = (spineIdx + 0.5f) * 64f
        val aStart = sMin * 64f
        val aEnd = (sMax + 1) * 64f
        val cockA = aStart + 26f
        val engA = aEnd - 9f
        // luces de navegación en las celdas de ala (fuera de la columna)
        val navs = ArrayList<FloatArray>()
        for (r in 0 until ch) for (q in 0 until cw) {
            if (!cellOcc[r][q]) continue
            val onSpine = if (horiz) r == spineIdx else q == spineIdx
            if (onSpine) continue
            val ca = ((if (horiz) q else r) + 0.5f) * 64f
            val cb = ((if (horiz) r else q) + 0.5f) * 64f
            val dirB = if (cb > spine) 1f else -1f
            navs.add(floatArrayOf(ca, cb + dirB * 20f, if (dirB > 0) 1f else 0f))
        }

        val acR = Color.red(accent).toFloat(); val acG = Color.green(accent).toFloat(); val acB = Color.blue(accent).toFloat()
        val huR = Color.red(f.hull).toFloat(); val huG = Color.green(f.hull).toFloat(); val huB = Color.blue(f.hull).toFloat()
        val lnR = Color.red(f.lens).toFloat(); val lnG = Color.green(f.lens).toFloat(); val lnB = Color.blue(f.lens).toFloat()
        val wnR = Color.red(f.window).toFloat(); val wnG = Color.green(f.window).toFloat(); val wnB = Color.blue(f.window).toFloat()
        val fbR = Color.red(f.flameA).toFloat(); val fbG = Color.green(f.flameA).toFloat(); val fbB = Color.blue(f.flameA).toFloat()
        val sd = type * 17f + 3f

        // campo de alturas
        val hgt = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!sil[i]) continue
            val lx = (x + 0.5f) * sc
            val ly = (y + 0.5f) * sc
            val along = if (horiz) lx else ly
            val across = if (horiz) ly else lx
            val dIn = d3[i] * sc
            var hh = 0.55f * smooth(0f, 4.5f, dIn) + 0.45f * smooth(0f, 14f, dIn)
            if (dIn > 6f) {
                val pa = ((along - 20f) % 40f + 40f) % 40f
                hh -= 0.2f * (1f - smooth(0.4f, 1.8f, min(pa, 40f - pa)))
                if (along > aStart + 4f && along < aEnd - 4f) {
                    hh -= 0.17f * (1f - smooth(0.4f, 1.6f, abs(abs(across - spine) - 17f)))
                }
            }
            val ca = (along - cockA) / 16f
            val cb = (across - spine) / 9.5f
            val cr2 = ca * ca + cb * cb
            if (cr2 < 1f) hh += 0.95f * sqrt(1f - cr2)
            else if (cr2 < 1.35f) hh -= 0.2f
            for (sgn in intArrayOf(-1, 1)) {
                val dn = sqrt((along - engA) * (along - engA) + (across - spine - sgn * 10f) * (across - spine - sgn * 10f))
                if (dn < 6.5f) hh -= 0.4f * (1f - dn / 6.5f)
            }
            hgt[i] = hh
        }

        val ll = sqrt(0.5f * 0.5f + 0.6f * 0.6f + 0.62f * 0.62f)
        val lx0 = -0.5f / ll; val ly0 = -0.6f / ll; val lz0 = 0.62f / ll
        val hl = sqrt(lx0 * lx0 + ly0 * ly0 + (lz0 + 1f) * (lz0 + 1f))
        val hx = lx0 / hl; val hy = ly0 / hl; val hz = (lz0 + 1f) / hl
        val px = IntArray(w * h)
        fun hAt(x: Int, y: Int): Float {
            val xx = x.coerceIn(0, w - 1); val yy = y.coerceIn(0, h - 1)
            return hgt[yy * w + xx]
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val alpha = ((lim - d2[i]) + 1f).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val lx = (x + 0.5f) * sc
            val ly = (y + 0.5f) * sc
            val along = if (horiz) lx else ly
            val across = if (horiz) ly else lx
            val dIn = d3[i] * sc
            var nx = -(hAt(x + 1, y) - hAt(x - 1, y)) / (2f * sc) * 5f
            var ny = -(hAt(x, y + 1) - hAt(x, y - 1)) / (2f * sc) * 5f
            val nl = sqrt(nx * nx + ny * ny + 1f)
            nx /= nl; ny /= nl
            val nz = 1f / nl

            val n0 = Noise.fbm(along * 0.05f, across * 0.3f, sd, 3)
            val grain = Noise.fbm(lx * 0.7f, ly * 0.7f, sd + 9f, 2)
            var lum = (0.8f + 0.4f * n0) * (0.9f + 0.2f * grain)
            lum *= 0.84f + 0.32f * Noise.fbm(along * 0.9f, across * 0.05f, sd + 4f, 3)
            val panel = (((along - 20f) / 40f).toInt() + 40) * 3 + (if (across > spine) 1 else 0)
            lum *= 0.9f + 0.2f * Noise.n3(panel.toFloat(), sd, 2.2f)
            var r = huR * lum; var g = huG * lum; var b = huB * lum
            when (f.special) {
                1 -> {
                    val cell = 2.2f
                    val uu = (lx % cell) / cell
                    val par = ((lx / cell).toInt() + (ly / cell).toInt()) % 2 == 0
                    val wv = 0.7f + 0.3f * (if (par) uu else 1f - uu)
                    r *= wv; g *= wv; b *= wv
                }
                2 -> {
                    val cn = Noise.fbm(lx * 0.12f + sd, ly * 0.12f, 3.3f, 3)
                    if (cn > 0.56f) { r *= 0.62f; g *= 0.78f; b *= 0.55f }
                    else if (cn < 0.42f) { r *= 1.35f; g *= 1.2f; b *= 0.9f }
                }
                3 -> {
                    val ia = (nx + ny) * 5f + lx * 0.04f
                    r *= 0.75f + 0.45f * kotlin.math.sin(ia)
                    g *= 0.75f + 0.45f * kotlin.math.sin(ia + 2.1f)
                    b *= 0.75f + 0.45f * kotlin.math.sin(ia + 4.2f)
                }
            }
            var er = 0f; var eg = 0f; var eb = 0f
            // franjas de facción a lo largo de la columna y en las alas
            val dsp = abs(across - spine)
            if (along > aStart + 8f && along < aEnd - 14f && dsp > 7.5f && dsp < 10.5f && dIn > 3f) {
                r = acR * 0.8f; g = acG * 0.8f; b = acB * 0.8f
                er += acR * 0.2f; eg += acG * 0.2f; eb += acB * 0.2f
            }
            for (nv in navs) {
                if (abs(across - nv[1]) < 2.2f && abs(along - nv[0]) < 18f && dIn > 3f) {
                    r = acR * 0.75f; g = acG * 0.75f; b = acB * 0.75f
                }
            }
            // ventanas a los lados de la columna
            if (along > cockA + 22f && along < engA - 14f && dIn > 4f) {
                val wa = ((along - cockA) % 12f)
                if (wa < 3.2f && abs(dsp - 13f) < 1.6f) {
                    val litw = Noise.n3((along / 12f).toInt().toFloat(), across, sd) > 0.35f
                    if (litw) { r = wnR * 0.4f; g = wnG * 0.4f; b = wnB * 0.4f; er += wnR; eg += wnG; eb += wnB }
                    else { r *= 0.3f; g *= 0.3f; b *= 0.35f }
                }
            }
            // cabina
            val ca = (along - cockA) / 16f
            val cb = (across - spine) / 9.5f
            val cr2 = ca * ca + cb * cb
            if (cr2 < 1f) {
                val kk = 1f - sqrt(cr2)
                r = lnR * 0.25f; g = lnG * 0.25f; b = lnB * 0.25f
                er += lnR * (0.25f + 0.7f * kk); eg += lnG * (0.25f + 0.7f * kk); eb += lnB * (0.25f + 0.7f * kk)
            } else if (cr2 < 1.35f) { r *= 0.35f; g *= 0.35f; b *= 0.4f }
            // toberas con brillo del motor
            for (sgn in intArrayOf(-1, 1)) {
                val dn = sqrt((along - engA) * (along - engA) + (across - spine - sgn * 10f) * (across - spine - sgn * 10f))
                if (dn < 6.5f) {
                    val k2 = (1f - dn / 6.5f)
                    r = 20f; g = 18f; b = 22f
                    er += fbR * k2 * 1.2f; eg += fbG * k2 * 1.2f; eb += fbB * k2 * 1.2f
                }
            }
            // luces de navegación (rojo / verde)
            for (nv in navs) {
                val dn = sqrt((along - nv[0]) * (along - nv[0]) + (across - nv[1]) * (across - nv[1]))
                if (dn < 7f) {
                    val k3 = (1f - dn / 7f).pow(2f)
                    if (nv[2] > 0.5f) { er += 60f * k3; eg += 255f * k3; eb += 120f * k3 } else { er += 255f * k3; eg += 50f * k3; eb += 40f * k3 }
                }
            }
            val ndl = max(0f, nx * lx0 + ny * ly0 + nz * lz0)
            val ndh = max(0f, nx * hx + ny * hy + nz * hz)
            val spec = ndh.pow(18f + 70f * f.gloss) * (0.12f + 0.9f * f.gloss)
            val ao = 0.7f + 0.3f * smooth(0f, 0.55f, hgt[i])
            val env = f.metal * (0.5f - 0.5f * ny)
            val shade = (0.26f + 0.9f * ndl) * ao
            var sp = 0f
            if (f.sparkle > 0f && Noise.fbm(lx * 2.5f + 20f, ly * 2.5f, sd + 11f, 2) > 0.78f) sp = f.sparkle
            val tr = huR / 255f; val tg = huG / 255f; val tb = huB / 255f
            r = r * shade + 255f * spec * (0.55f + 0.45f * f.metal * tr) + 70f * env * (tr + 0.3f) + 255f * sp * ndl + er
            g = g * shade + 255f * spec * (0.55f + 0.45f * f.metal * tg) + 70f * env * (tg + 0.3f) + 255f * sp * ndl + eg
            b = b * shade + 255f * spec * (0.55f + 0.45f * f.metal * tb) + 70f * env * (tb + 0.3f) + 255f * sp * ndl + eb
            if (dIn < 1.0f) { r *= 0.4f; g *= 0.4f; b *= 0.45f }
            px[i] = Color.argb(cl(255f * alpha), cl(r), cl(g), cl(b))
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        bmp.setHasMipMap(true)
        return bmp
    }

    /** Galaxia espiral procedural (núcleo cálido, brazos azulados con nubes rosadas). */
    fun makeGalaxy(sz: Int): Bitmap {
        val px = IntArray(sz * sz)
        val h = sz / 2f
        for (y in 0 until sz) for (x in 0 until sz) {
            val nx = (x + 0.5f - h) / h
            val ny = (y + 0.5f - h) / h
            val r = sqrt(nx * nx + ny * ny)
            if (r >= 1f) continue
            val a = kotlin.math.atan2(ny, nx)
            val phi = a - kotlin.math.ln(r + 0.05f) * 2.6f
            val arm = ((0.5f + 0.5f * kotlin.math.cos(2f * phi)).pow(3f))
            val n = Noise.fbm(nx * 4f + 3f, ny * 4f, 1.5f, 4)
            val core = kotlin.math.exp(-r * 9f)
            val disk = kotlin.math.exp(-r * 2.6f)
            val armB = arm * disk * (0.45f + 0.9f * n)
            val pink = smooth(0.62f, 0.75f, n) * arm * disk
            val br = (core * 1.6f + armB * 0.9f).coerceIn(0f, 1.5f)
            val rr = 255f * (core * 1.0f + armB * 0.62f + pink * 0.8f)
            val gg = 255f * (core * 0.88f + armB * 0.72f + pink * 0.3f)
            val bb = 255f * (core * 0.7f + armB * 1.0f + pink * 0.55f)
            val al = (br * 0.9f).coerceIn(0f, 1f) * smooth(1f, 0.85f, r)
            px[y * sz + x] = Color.argb(cl(255f * al), cl(rr), cl(gg), cl(bb))
        }
        return Bitmap.createBitmap(px, sz, sz, Bitmap.Config.ARGB_8888)
    }

    /** Roca irregular con relieve e iluminación lateral. */
    fun makeRock(size: Int, seed: Int): Bitmap {
        val px = IntArray(size * size)
        val h = size / 2f
        val sd = seed * 3.7f
        for (y in 0 until size) for (x in 0 until size) {
            val nx = (x + 0.5f - h) / h
            val ny = (y + 0.5f - h) / h
            val ang = kotlin.math.atan2(ny, nx)
            val rad = 0.70f + 0.24f * Noise.fbm(kotlin.math.cos(ang) * 1.6f + sd, kotlin.math.sin(ang) * 1.6f, sd * 0.7f, 3)
            val dist = sqrt(nx * nx + ny * ny)
            if (dist > rad + 0.04f) continue
            val edge = ((rad + 0.04f - dist) / 0.05f).coerceIn(0f, 1f)
            val q = dist / rad
            val z = sqrt(max(0f, 1f - q * q))
            val dot = (nx / rad) * -0.5f + (ny / rad) * -0.5f + z * 0.7f
            val cr = Noise.fbm(nx * 3.2f + sd, ny * 3.2f, 2f, 5)
            val crat = smooth(0.58f, 0.66f, Noise.fbm(nx * 5f + sd, ny * 5f, 7f, 3))
            val grain = Noise.fbm(nx * 9f + sd, ny * 9f, 5f, 3)
            val hr1 = Noise.fbm(nx * 5f + sd, ny * 5f, 2f, 4)
            val hr2 = Noise.fbm((nx - 0.03f) * 5f + sd, (ny - 0.03f) * 5f, 2f, 4)
            val rel = (hr1 - hr2) * 14f
            val shade = (0.22f + 0.95f * max(0f, dot) + (cr - 0.5f) * 0.55f + rel * 0.5f) * (0.8f + 0.4f * grain) * (1f - 0.4f * crat)
            val r = 128f * shade; val g = 112f * shade; val b = 98f * shade
            px[y * size + x] = Color.argb(cl(255f * edge), cl(r), cl(g), cl(b))
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Luna con cráteres y terminador. */
    fun makeMoon(size: Int, seed: Int, tint: Int): Bitmap {
        val px = IntArray(size * size)
        val h = size / 2f
        val sd = seed * 5.1f
        val tr = Color.red(tint).toFloat(); val tg = Color.green(tint).toFloat(); val tb = Color.blue(tint).toFloat()
        for (y in 0 until size) for (x in 0 until size) {
            val nx = (x + 0.5f - h) / h
            val ny = (y + 0.5f - h) / h
            val rr = nx * nx + ny * ny
            if (rr >= 1.03f) continue
            val edge = if (rr <= 1f) 1f else (1.03f - rr) / 0.03f
            val nz = sqrt(max(0f, 1f - rr))
            val n1 = Noise.fbm(nx * 2f + sd, ny * 2f, nz * 2f, 5)
            val crater = smooth(0.60f, 0.70f, Noise.fbm(nx * 4.5f + sd, ny * 4.5f, nz * 4.5f + 3f, 3))
            val dot = nx * -0.62f + ny * -0.38f + nz * 0.68f
            val lit = (0.05f + 0.95f * smooth(-0.1f, 0.45f, dot))
            val base = (0.75f + 0.5f * n1) * (1f - 0.35f * crater)
            val limb = 0.6f + 0.4f * sqrt(nz)
            val f = base * lit * limb
            px[y * size + x] = Color.argb(cl(255f * edge), cl(tr * f), cl(tg * f), cl(tb * f))
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
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
    private const val RS = 0.93f
    private val LMAX = sqrt(1f - RS * RS)

    /** lite = vista previa: mismo diseño con menos detalle fino (bastante más rápida de generar). */
    fun makePlanet(radius: Int, skin: Skin = Skins.all[0], lite: Boolean = false): PlanetData {
        val wo = if (lite) 2 else 3
        val d = radius * 2
        val px = IntArray(d * d)
        val ll = sqrt(0.62f * 0.62f + 0.38f * 0.38f + 0.68f * 0.68f)
        val lx = -0.62f / ll; val ly = -0.38f / ll; val lz = 0.68f / ll
        val hl = sqrt(lx * lx + ly * ly + (lz + 1f) * (lz + 1f))
        val hx = lx / hl; val hy = ly / hl; val hz = (lz + 1f) / hl
        val cand = ArrayList<FloatArray>()
        val oDr = Color.red(skin.oceanDeep).toFloat(); val oDg = Color.green(skin.oceanDeep).toFloat(); val oDb = Color.blue(skin.oceanDeep).toFloat()
        val oSr = Color.red(skin.oceanShallow).toFloat(); val oSg = Color.green(skin.oceanShallow).toFloat(); val oSb = Color.blue(skin.oceanShallow).toFloat()
        val lLr = Color.red(skin.landLow).toFloat(); val lLg = Color.green(skin.landLow).toFloat(); val lLb = Color.blue(skin.landLow).toFloat()
        val lHr = Color.red(skin.landHigh).toFloat(); val lHg = Color.green(skin.landHigh).toFloat(); val lHb = Color.blue(skin.landHigh).toFloat()
        val dsr = Color.red(skin.desert).toFloat(); val dsg = Color.green(skin.desert).toFloat(); val dsb = Color.blue(skin.desert).toFloat()
        val icr = Color.red(skin.ice).toFloat(); val icg = Color.green(skin.ice).toFloat(); val icb = Color.blue(skin.ice).toFloat()
        val clr = Color.red(skin.cloud).toFloat(); val clg = Color.green(skin.cloud).toFloat(); val clb = Color.blue(skin.cloud).toFloat()
        val atr = Color.red(skin.atmo) / 255f; val atg = Color.green(skin.atmo) / 255f; val atb = Color.blue(skin.atmo) / 255f
        val ctr = Color.red(skin.city).toFloat(); val ctg = Color.green(skin.city).toFloat(); val ctb = Color.blue(skin.city).toFloat()
        val thr = skin.landThr
        val ccR = Color.red(skin.crackColor).toFloat(); val ccG = Color.green(skin.crackColor).toFloat(); val ccB = Color.blue(skin.crackColor).toFloat()
        fun work(ya: Int, yb: Int, out: ArrayList<FloatArray>) {
        for (y in ya until yb) for (x in 0 until d) {
            // el bitmap incluye la capa de atmósfera: la superficie ocupa RS del radio y el aire llega a 1
            val bx = (x + 0.5f - radius) / radius
            val by = (y + 0.5f - radius) / radius
            val rb2 = bx * bx + by * by
            if (rb2 >= 1f) continue
            val rb = sqrt(rb2)
            if (rb >= RS) {
                // solo aire: espesor atravesado por la línea de visión, más denso cerca de la superficie
                val path = 2f * sqrt(1f - rb2) / (2f * LMAX)
                val alt = (rb - RS) / (1f - RS)
                val dens = path * kotlin.math.exp(-alt * 2.4f)
                val sunSide = (bx * lx + by * ly) / (rb * sqrt(lx * lx + ly * ly))
                val dayR = smooth(-0.45f, 0.6f, sunSide)
                val dusk = smooth(-0.45f, -0.05f, sunSide) * (1f - smooth(-0.05f, 0.45f, sunSide))
                val k = dens * (0.18f + 0.82f * dayR)
                val ar = min(1f, 255f * atr * 1.25f * (1f - dusk * 0.5f) + 255f * dusk * 0.6f)
                val ag = 255f * atg * 1.25f * (1f - dusk * 0.4f) + 140f * dusk * 0.6f
                val ab = 255f * atb * 1.25f * (1f - dusk * 0.6f) + 70f * dusk * 0.6f
                px[y * d + x] = Color.argb(cl(255f * min(1f, k * 1.7f)), cl(ar.coerceAtLeast(0f) * 1f + 0f), cl(ag), cl(ab))
                continue
            }
            val nx = bx / RS
            val ny = by / RS
            val rr = nx * nx + ny * ny
            val edge = 1f
            val nz = sqrt(max(0f, 1f - rr))
            val qx = nx * 0.92f
            val qy = ny * 0.92f + nz * 0.39f
            val qz = nz * 0.92f - ny * 0.39f
            val sx0 = qx * skin.freq + 3.1f + skin.seed; val sy0 = qy * skin.freq + 1.7f + skin.seed * 0.7f; val sz0 = qz * skin.freq + 5.3f + skin.seed * 1.3f
            // deformación de dominio: costas y continentes más naturales
            val sx = sx0 + (Noise.fbm(sx0 * 0.7f + 17f, sy0 * 0.7f, sz0 * 0.7f, wo) - 0.5f) * 0.9f
            val sy = sy0 + (Noise.fbm(sx0 * 0.7f, sy0 * 0.7f + 29f, sz0 * 0.7f, wo) - 0.5f) * 0.9f
            val sz = sz0 + (Noise.fbm(sx0 * 0.7f, sy0 * 0.7f, sz0 * 0.7f + 41f, wo) - 0.5f) * 0.9f
            val c0 = Noise.fbm(sx, sy, sz, if (lite) 5 else 7)
            val cRid = 1f - abs(2f * c0 - 1f)
            val cMix = c0 * (1f - skin.ridge) + cRid * 0.72f * skin.ridge
            val c = if (skin.bands > 0f) cMix + skin.bands * 0.10f * kotlin.math.sin(qy * 12f + 4f * c0) else cMix
            val lat = abs(qy)
            val land = smooth(thr, thr + 0.035f, c)
            val c2 = if (land > 0.02f && !lite) Noise.fbm(sx + lx * 0.05f, sy + ly * 0.05f, sz + lz * 0.05f, 6) else c0
            val moist = if (land > 0.02f) Noise.fbm(sx * 2f + 11f, sy * 2f, sz * 2f, 4) else 0.5f
            var r: Float; var g: Float; var b: Float
            val depth = smooth(thr - 0.20f, thr + 0.02f, c)
            val orr = oDr + (oSr - oDr) * depth; val og = oDg + (oSg - oDg) * depth; val ob = oDb + (oSb - oDb) * depth
            val e = ((c - thr - 0.035f) / 0.25f).coerceIn(0f, 1f)
            var lr: Float; var lg: Float; var lb: Float
            if (moist < 0.45f && lat < 0.62f) { lr = dsr; lg = dsg; lb = dsb }
            else { lr = lLr; lg = lLg; lb = lLb }
            lr += (lHr - lr) * e * 0.8f; lg += (lHg - lg) * e * 0.8f; lb += (lHb - lb) * e * 0.8f
            // relieve: sombreado según la pendiente en dirección de la luz
            val micro = if (land > 0.3f && !lite) (Noise.fbm(sx * 7f, sy * 7f, sz * 7f, 3) - 0.5f) * 0.35f * land else 0f
            val bump = ((c0 - c2) * 26f).coerceIn(-0.55f, 0.55f) * land + micro
            r = orr + (lr - orr) * land; g = og + (lg - og) * land; b = ob + (lb - ob) * land
            r *= 1f + bump; g *= 1f + bump; b *= 1f + bump
            val crat = if (skin.craters > 0f && land > 0.3f) smooth(0.60f, 0.70f, Noise.fbm(sx * 3.5f, sy * 3.5f, sz * 3.5f + 9f, 3)) else 0f
            val cf = 1f - 0.38f * skin.craters * crat
            r *= cf; g *= cf; b *= cf
            var cm = 0f
            if (skin.crack > 0f && land > 0.2f) {
                val rid = 1f - abs(2f * Noise.fbm(sx * 2.3f + 7f, sy * 2.3f, sz * 2.3f, 4) - 1f)
                cm = smooth(0.93f - 0.05f * skin.crack, 0.985f, rid) * skin.crack * land
            }
            if (skin.crackGlow < 0.5f && cm > 0f) {
                r += (ccR - r) * cm; g += (ccG - g) * cm; b += (ccB - b) * cm
            }
            val ice = smooth(0.80f - skin.iceBias, 0.90f - skin.iceBias, lat + (if (lat > 0.45f) Noise.fbm(sx * 3f, sy * 3f, sz * 3f, 3) - 0.5f else 0f) * 0.18f)
            r += (icr - r) * ice; g += (icg - g) * ice; b += (icb - b) * ice
            val oceanMask = (1f - land) * (1f - ice)
            val cn = Noise.fbm(sx * 1.5f + 40f, sy * 1.5f * skin.cloudStretch, sz * 1.5f + 7f, if (lite) 4 else 5)
            val cloud = smooth(0.50f, 0.72f, cn) * skin.cloudAmt
            val dot = nx * lx + ny * ly + nz * lz
            val day = smooth(-0.10f, 0.40f, dot)
            val csh = if (lite) 0f else smooth(0.50f, 0.72f, Noise.fbm(sx * 1.5f + 40f - lx * 0.12f, sy * 1.5f * skin.cloudStretch - ly * 0.12f, sz * 1.5f + 7f - lz * 0.12f, 4))
            val shadeV = (0.03f + 0.97f * day) * (1f - 0.3f * csh * (1f - cloud))
            r = (r * (1f - cloud) + clr * cloud) * shadeV
            g = (g * (1f - cloud) + clg * cloud) * shadeV
            b = (b * (1f - cloud) + clb * cloud) * shadeV
            val cityRaw = if (land > 0.5f) Noise.fbm(sx * 9f, sy * 9f, sz * 9f, 3) else 0f
            val cities = smooth(0.60f, 0.72f, cityRaw) * land * (1f - ice) * (1f - cloud) * (1f - day)
            r += ctr * cities * 0.95f; g += ctg * cities * 0.95f; b += ctb * cities * 0.95f
            if (skin.crackGlow >= 0.5f && cm > 0f) { r += ccR * cm; g += ccG * cm; b += ccB * cm }
            if (cityRaw > 0.70f && land > 0.95f && ice < 0.1f && nz > 0.3f && ((x * 7 + y * 13) % 41 == 0)) {
                out.add(floatArrayOf(bx, by))
            }
            val wave = if (oceanMask > 0.3f && !lite) Noise.fbm(sx * 9f, sy * 9f, sz * 9f, 2) - 0.5f else 0f
            val sp = max(0f, nx * hx + ny * hy + nz * hz + wave * 0.12f).pow(70f) * oceanMask * (1f - cloud) * 0.85f
            r += 255f * sp; g += 255f * sp; b += 240f * sp
            if (!lite && ice > 0.5f && Noise.fbm(sx * 14f, sy * 14f, sz * 14f, 2) > 0.8f) { r += 80f * day; g += 85f * day; b += 90f * day }
            val limb = 0.6f + 0.4f * sqrt(nz)
            r *= limb; g *= limb; b *= limb
            // dispersión atmosférica: bruma proporcional al aire entre el ojo y la superficie
            val haze = ((sqrt(1f - rb2) - sqrt(max(0f, RS * RS - rb2))) / LMAX).coerceIn(0f, 1f)
            val hz = haze * haze * 0.9f + haze * 0.1f
            val sunset = smooth(-0.12f, 0.12f, dot) * (1f - smooth(0.12f, 0.5f, dot))
            val sc = 0.12f + 0.88f * day
            r = r * (1f - 0.55f * hz) + (255f * atr * (1f - sunset * 0.5f) + 255f * sunset * 0.5f) * hz * sc
            g = g * (1f - 0.55f * hz) + (255f * atg * (1f - sunset * 0.4f) + 140f * sunset * 0.5f) * hz * sc
            b = b * (1f - 0.55f * hz) + (255f * atb * (1f - sunset * 0.6f) + 70f * sunset * 0.5f) * hz * sc
            val sn = sunset * 0.12f
            r += 255f * sn; g += 140f * sn; b += 60f * sn
            px[y * d + x] = Color.argb(cl(255f * edge), cl(r), cl(g), cl(b))
        }
        }
        val nt = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val parts = Array(nt) { ArrayList<FloatArray>() }
        val ths = Array(nt) { i -> Thread { work(d * i / nt, d * (i + 1) / nt, parts[i]) }.also { t -> t.start() } }
        for (t in ths) t.join()
        for (p in parts) cand.addAll(p)
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
