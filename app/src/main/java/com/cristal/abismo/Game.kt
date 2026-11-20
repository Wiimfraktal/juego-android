package com.cristal.abismo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Nave: cuatro celdas relativas al pivote, que se mueve en coordenadas continuas (fx, fy). */
class Piece(val type: Int, var cells: Array<IntArray>, var fx: Float, var fy: Float)

object Shapes {
    val all: Array<Array<IntArray>> = arrayOf(
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(2, 0)), // I
        arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1)),  // O
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1)), // T
        arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-1, 1), intArrayOf(0, 1)), // S
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 0), intArrayOf(0, 1), intArrayOf(1, 1)), // Z
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1)), // L
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-1, 1)) // J
    )
}

/**
 * Tetris invertido con movimiento libre: las naves nacen en el centro y avanzan hacia el borde.
 * El jugador las arrastra en coordenadas continuas, pero nunca puede acercarlas al centro.
 * Se pierde si algo se acumula dentro de la zona central.
 */
class Game(val cols: Int, val rows: Int) {
    val cx = cols / 2
    val cy = rows / 2
    val grid = Array(rows) { IntArray(cols) }
    /** Identificador de nave por celda (0 = vacío), para borrar naves completas. */
    val ids = Array(rows) { IntArray(cols) }
    private var nextId = 1

    var piece: Piece? = null
    var gx = 0
    var gy = 1
    var baseSpeed = 2.5f   // celdas por segundo al inicio
    var score = 0
    var barProgress = 0
    var barsFilled = 0
    var ingots = 0
    var lines = 0
    var level = 1
    var over = false
    var overTime = 0f

    /** Celdas eliminadas recientemente (x, y, tipo) para efectos. */
    val bursts = ArrayList<IntArray>()

    /** Hierro soltado (x, y, cantidad); la vista lo anima hasta la barra y luego llama addIron. */
    val ironDrops = ArrayList<IntArray>()

    private var contact = 0f
    /** Naves fijadas hasta ahora (la vista lo usa para el sonido de colocación). */
    var locks = 0
    private var nextType = Random.nextInt(Shapes.all.size)

    init { spawn() }

    fun barNeed() = 400 + 200 * barsFilled

    fun addIron(a: Int) {
        barProgress += a
        while (barProgress >= barNeed()) {
            barProgress -= barNeed()
            barsFilled++
            ingots += 3
        }
    }

    private fun speed() = baseSpeed * min(2.2f, 1f + 0.07f * (level - 1))

    private fun spawn() {
        val t = nextType
        nextType = Random.nextInt(Shapes.all.size)
        val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0))
        val d = dirs[Random.nextInt(4)]
        gx = d[0]; gy = d[1]
        val p = Piece(t, Array(4) { Shapes.all[t][it].copyOf() }, cx.toFloat(), cy.toFloat())
        piece = p
        contact = 0f
        if (hits(p.cells, p.fx, p.fy)) over = true
    }

    private fun occupied(x: Int, y: Int) = x < 0 || y < 0 || x >= cols || y >= rows || grid[y][x] != 0

    /** Colisión continua: el rectángulo de cada celda se compara con las celdas de la rejilla que toca. */
    private fun hits(cells: Array<IntArray>, fx: Float, fy: Float): Boolean {
        val e = 0.04f
        for (c in cells) {
            val x0 = floor(fx + c[0] + e).toInt()
            val x1 = floor(fx + c[0] + 1f - e).toInt()
            val y0 = floor(fy + c[1] + e).toInt()
            val y1 = floor(fy + c[1] + 1f - e).toInt()
            for (iy in y0..y1) for (ix in x0..x1) if (occupied(ix, iy)) return true
        }
        return false
    }

    /** Distancia² de la celda más cercana de la nave al centro. */
    private fun minDist2(cells: Array<IntArray>, fx: Float, fy: Float): Float {
        var best = Float.MAX_VALUE
        for (c in cells) {
            val dx = fx + c[0] - cx
            val dy = fy + c[1] - cy
            val d = dx * dx + dy * dy
            if (d < best) best = d
        }
        return best
    }

    /** La nave avanza siempre hacia afuera según el eje dominante respecto al centro. */
    private fun updateDir() {
        val p = piece ?: return
        val dx = p.fx - cx
        val dy = p.fy - cy
        if (abs(dx) > abs(dy) + 0.01f) { gx = if (dx > 0) 1 else -1; gy = 0 }
        else if (abs(dy) > abs(dx) + 0.01f) { gx = 0; gy = if (dy > 0) 1 else -1 }
    }

    private fun tryShift(p: Piece, dx: Float, dy: Float): Boolean {
        if (dx == 0f && dy == 0f) return false
        val nx = p.fx + dx
        val ny = p.fy + dy
        if (nx < -1f || ny < -1f || nx > cols + 1f || ny > rows + 1f) return false
        if (hits(p.cells, nx, ny)) return false
        if (minDist2(p.cells, nx, ny) < minDist2(p.cells, p.fx, p.fy) - 1e-3f) return false
        p.fx = nx; p.fy = ny
        updateDir()
        return true
    }

    /** Mueve a una posición absoluta (órbita): el radio del pivote no puede disminuir. */
    private fun tryMoveTo(p: Piece, nx: Float, ny: Float): Boolean {
        if (nx < -1f || ny < -1f || nx > cols + 1f || ny > rows + 1f) return false
        if (hits(p.cells, nx, ny)) return false
        if (hypot(nx - cx, ny - cy) < hypot(p.fx - cx, p.fy - cy) - 1e-3f) return false
        if (minDist2(p.cells, nx, ny) < minDist2(p.cells, p.fx, p.fy) - 0.4f) return false
        p.fx = nx; p.fy = ny
        updateDir()
        return true
    }

    /**
     * Arrastre libre en celdas. El desplazamiento se descompone en radial y tangencial respecto al centro:
     * lo tangencial rota la nave alrededor del centro manteniendo el radio; lo radial solo hacia afuera.
     */
    fun drag(dx: Float, dy: Float) {
        val p = piece ?: return
        if (over) return
        val n = max(1, Math.ceil((max(abs(dx), abs(dy)) / 0.2f).toDouble()).toInt())
        val sx = dx / n
        val sy = dy / n
        for (i in 0 until n) orbitStep(p, sx, sy)
    }

    private fun orbitStep(p: Piece, dx: Float, dy: Float) {
        val vx = p.fx - cx
        val vy = p.fy - cy
        val r = hypot(vx, vy)
        if (r < 1.5f) {
            if (!tryShift(p, dx, dy)) { if (!tryShift(p, dx, 0f)) tryShift(p, 0f, dy) }
            return
        }
        val erx = vx / r; val ery = vy / r
        val rc = max(0f, dx * erx + dy * ery)
        val tc = dx * (-ery) + dy * erx
        val a0 = atan2(vy, vx)
        val a1 = a0 + tc / r
        // completo (tangencial + radial hacia afuera), solo tangencial, solo radial y por último ejes
        if (tryMoveTo(p, cx + (r + rc) * cos(a1), cy + (r + rc) * sin(a1))) return
        if (tryMoveTo(p, cx + r * cos(a1), cy + r * sin(a1))) return
        if (rc > 0f && tryMoveTo(p, cx + (r + rc) * cos(a0), cy + (r + rc) * sin(a0))) return
        if (!tryShift(p, dx, dy)) { if (!tryShift(p, dx, 0f)) tryShift(p, 0f, dy) }
    }

    fun rotate() {
        val p = piece ?: return
        if (over || p.type == 1) return
        val rot = Array(4) { intArrayOf(-p.cells[it][1], p.cells[it][0]) }
        val kicks = arrayOf(
            floatArrayOf(0f, 0f), floatArrayOf(0.5f, 0f), floatArrayOf(-0.5f, 0f),
            floatArrayOf(0f, 0.5f), floatArrayOf(0f, -0.5f), floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f),
            floatArrayOf(0f, 1f), floatArrayOf(0f, -1f)
        )
        val cur = minDist2(p.cells, p.fx, p.fy)
        for (k in kicks) {
            val nx = p.fx + k[0]
            val ny = p.fy + k[1]
            if (minDist2(rot, nx, ny) < cur - 1e-3f) continue
            if (!hits(rot, nx, ny)) {
                p.cells = rot
                p.fx = nx; p.fy = ny
                updateDir()
                return
            }
        }
    }

    private fun tryFall(p: Piece, step: Float): Boolean {
        val nx = p.fx + gx * step
        val ny = p.fy + gy * step
        if (!hits(p.cells, nx, ny)) { p.fx = nx; p.fy = ny; updateDir(); return true }
        // avanza lo posible hasta el contacto
        var t = 0f
        while (t + 0.02f <= step && !hits(p.cells, p.fx + gx * 0.02f, p.fy + gy * 0.02f)) {
            p.fx += gx * 0.02f; p.fy += gy * 0.02f; t += 0.02f
        }
        return false
    }

    fun hardDrop() {
        val p = piece ?: return
        if (over) return
        while (tryFall(p, 0.25f)) { /* sigue hasta chocar */ }
        lock()
    }

    fun update(dt: Float) {
        if (over) { overTime += dt; return }
        val p = piece ?: return
        // lenta al nacer y cada vez más rápida hacia el borde
        val axisDist = if (gx != 0) abs(p.fx - cx) else abs(p.fy - cy)
        val half = if (gx != 0) cols / 2f else rows / 2f
        val fr = (axisDist / half / 0.55f).coerceIn(0f, 1f)
        val factor = 0.15f + 0.85f * fr * fr * (3f - 2f * fr)
        var rem = speed() * factor * dt
        var blocked = false
        while (rem > 1e-4f) {
            val step = min(rem, 0.2f)
            rem -= step
            if (!tryFall(p, step)) { blocked = true; break }
        }
        if (blocked) {
            contact += dt
            if (contact > 0.18f) lock()
        } else contact = 0f
    }

    /** Ajusta la nave a la rejilla más cercana libre. */
    private fun snap(p: Piece): Boolean {
        val bx = Math.round(p.fx)
        val by = Math.round(p.fy)
        var bestX = 0; var bestY = 0; var bestD = Float.MAX_VALUE; var found = false
        for (oy in -1..1) for (ox in -1..1) {
            val x = bx + ox
            val y = by + oy
            if (hits(p.cells, x.toFloat(), y.toFloat())) continue
            val d = (x - p.fx) * (x - p.fx) + (y - p.fy) * (y - p.fy)
            if (d < bestD) { bestD = d; bestX = x; bestY = y; found = true }
        }
        if (found) { p.fx = bestX.toFloat(); p.fy = bestY.toFloat() }
        return found
    }

    private fun lock() {
        val p = piece ?: return
        if (!snap(p)) { over = true; return }
        locks++
        var danger = false
        val id = nextId++
        val ix = p.fx.toInt()
        val iy = p.fy.toInt()
        for (c in p.cells) {
            val x = ix + c[0]
            val y = iy + c[1]
            grid[y][x] = p.type + 1
            ids[y][x] = id
            ironDrops.add(intArrayOf(x, y, 10))
            if (abs(x - cx) <= 1 && abs(y - cy) <= 1) danger = true
        }
        piece = null
        val n = clearLines()
        if (n > 0) {
            lines += n
            level = 1 + lines / 6
            score += intArrayOf(0, 100, 300, 500, 800, 1000)[min(n, 5)] * level
        }
        score += 10
        if (danger && n == 0) { over = true; return }
        spawn()
    }

    private fun rowFull(y: Int) = grid[y].all { it != 0 }
    private fun colFull(x: Int) = (0 until rows).all { grid[it][x] != 0 }

    /** Al completar una línea se destruyen las naves enteras que la tocan (no quedan restos de 1x1). */
    private fun clearLines(): Int {
        val fr = (0 until rows).filter { it != cy && rowFull(it) }
        val fc = (0 until cols).filter { it != cx && colFull(it) }
        if (fr.isEmpty() && fc.isEmpty()) return 0
        val kill = HashSet<Int>()
        for (y in fr) for (x in 0 until cols) kill.add(ids[y][x])
        for (x in fc) for (y in 0 until rows) kill.add(ids[y][x])
        for (y in 0 until rows) for (x in 0 until cols) {
            if (grid[y][x] != 0 && ids[y][x] in kill) {
                bursts.add(intArrayOf(x, y, grid[y][x]))
                ironDrops.add(intArrayOf(x, y, 15))
                grid[y][x] = 0
                ids[y][x] = 0
            }
        }
        return fr.size + fc.size
    }

    /** Distancia (Chebyshev) de la celda ocupada más cercana al centro. */
    fun nearestFilled(): Int {
        var best = 99
        for (y in 0 until rows) for (x in 0 until cols) {
            if (grid[y][x] != 0) {
                val d = max(abs(x - cx), abs(y - cy))
                if (d < best) best = d
            }
        }
        return best
    }
}
