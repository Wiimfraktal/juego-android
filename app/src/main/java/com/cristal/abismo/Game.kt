package com.cristal.abismo

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Nave: cuatro celdas relativas al pivote. (px, py) es la celda real (siempre entera, para encajar
 * exactamente en la cuadrícula); (fx, fy) solo es la posición visual, que se acerca rápido a la real.
 */
class Piece(val type: Int, var cells: Array<IntArray>, var px: Int, var py: Int) {
    var fx = px.toFloat()
    var fy = py.toFloat()
}

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
 * Tetris invertido en cuadrícula rígida con movimiento en 8 direcciones: las naves nacen en el centro y
 * avanzan hacia afuera (en eje o en diagonal, según dónde estén). El jugador las empuja celda a celda
 * en cualquier dirección, pero nunca puede acercarlas al centro; sí puede orbitar alrededor.
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

    /** Naves fijadas hasta ahora (la vista lo usa para el sonido de colocación). */
    var locks = 0

    private var contact = 0f
    private var blocked = false
    private var fallT = 0f
    private var rMax = 0f
    private var accX = 0f
    private var accY = 0f
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
        val dirs = arrayOf(
            intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0),
            intArrayOf(1, 1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(-1, -1)
        )
        val d = dirs[Random.nextInt(8)]
        gx = d[0]; gy = d[1]
        val p = Piece(t, Array(4) { Shapes.all[t][it].copyOf() }, cx, cy)
        piece = p
        contact = 0f; blocked = false; fallT = 0f; rMax = 0f; accX = 0f; accY = 0f
        if (collides(p.cells, p.px, p.py)) over = true
    }

    private fun collides(cells: Array<IntArray>, px: Int, py: Int): Boolean {
        for (c in cells) {
            val x = px + c[0]
            val y = py + c[1]
            if (x < 0 || y < 0 || x >= cols || y >= rows) return true
            if (grid[y][x] != 0) return true
        }
        return false
    }

    private fun radius(px: Int, py: Int) = hypot((px - cx).toFloat(), (py - cy).toFloat())

    /** Dirección de avance según el octante respecto al centro: eje X, eje Y o diagonal. */
    private fun updateDir() {
        val p = piece ?: return
        val dx = p.px - cx
        val dy = p.py - cy
        val ax = abs(dx)
        val ay = abs(dy)
        if (ax == 0 && ay == 0) return
        val sx = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val sy = if (dy > 0) 1 else if (dy < 0) -1 else 0
        if (ax >= 2 * ay) { gx = sx; gy = 0 }
        else if (ay >= 2 * ax) { gx = 0; gy = sy }
        else { gx = sx; gy = sy }
    }

    private fun place(p: Piece, nx: Int, ny: Int) {
        p.px = nx; p.py = ny
        rMax = max(rMax, radius(nx, ny))
        updateDir()
    }

    /** Un paso del jugador (puede ser diagonal). Nunca más de 1 celda por debajo del radio máximo alcanzado. */
    private fun steer(dx: Int, dy: Int): Boolean {
        val p = piece ?: return false
        if (over || (dx == 0 && dy == 0)) return false
        val nx = p.px + dx
        val ny = p.py + dy
        if (collides(p.cells, nx, ny)) return false
        if (radius(nx, ny) < rMax - 1.0f) return false
        place(p, nx, ny)
        blocked = false
        return true
    }

    /**
     * Arrastre del dedo en celdas (fraccionario). Se acumula y cada ~0.55 celda se da un paso en X, en Y
     * o en ambos a la vez (diagonal); si el paso diagonal no cabe, se prueba cada eje por separado.
     */
    fun drag(dx: Float, dy: Float) {
        if (piece == null || over) return
        accX += dx
        accY += dy
        val step = 0.55f
        var guard = 0
        while ((abs(accX) >= step || abs(accY) >= step) && guard++ < 12) {
            val sx = if (abs(accX) >= step) (if (accX > 0f) 1 else -1) else 0
            val sy = if (abs(accY) >= step) (if (accY > 0f) 1 else -1) else 0
            if (!steer(sx, sy)) {
                if (sx != 0 && sy != 0) { if (!steer(sx, 0)) steer(0, sy) }
            }
            accX -= sx * step
            accY -= sy * step
        }
        accX = accX.coerceIn(-step, step)
        accY = accY.coerceIn(-step, step)
    }

    fun rotate() {
        val p = piece ?: return
        if (over || p.type == 1) return
        val rot = Array(4) { intArrayOf(-p.cells[it][1], p.cells[it][0]) }
        val kicks = arrayOf(
            intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
            intArrayOf(2, 0), intArrayOf(-2, 0), intArrayOf(0, 2), intArrayOf(0, -2)
        )
        for (k in kicks) {
            val nx = p.px + k[0]
            val ny = p.py + k[1]
            if (radius(nx, ny) < rMax - 1.0f) continue
            if (!collides(rot, nx, ny)) {
                p.cells = rot
                place(p, nx, ny)
                blocked = false
                return
            }
        }
    }

    /** Un paso de avance automático; si choca en diagonal se desliza por un solo eje. */
    private fun fallStep(p: Piece): Boolean {
        if (!collides(p.cells, p.px + gx, p.py + gy)) { place(p, p.px + gx, p.py + gy); return true }
        if (gx != 0 && gy != 0) {
            if (!collides(p.cells, p.px + gx, p.py)) { place(p, p.px + gx, p.py); return true }
            if (!collides(p.cells, p.px, p.py + gy)) { place(p, p.px, p.py + gy); return true }
        }
        return false
    }

    fun hardDrop() {
        val p = piece ?: return
        if (over) return
        while (fallStep(p)) { /* sigue hasta chocar */ }
        lock()
    }

    fun update(dt: Float) {
        if (over) { overTime += dt; return }
        val p = piece ?: return
        // visual: la nave se desliza rápido hacia su celda real
        val k = min(1f, dt * 22f)
        p.fx += (p.px - p.fx) * k
        p.fy += (p.py - p.fy) * k
        // lenta al nacer y cada vez más rápida hacia el borde
        val frac = max(abs(p.px - cx) / (cols / 2f), abs(p.py - cy) / (rows / 2f))
        val fr = (frac / 0.7f).coerceIn(0f, 1f)
        val factor = 0.45f + 0.55f * fr * fr * (3f - 2f * fr)
        val stepLen = if (gx != 0 && gy != 0) 1.4142f else 1f
        fallT += dt
        var guard = 0
        while (guard++ < 6) {
            val interval = stepLen / (speed() * factor)
            if (fallT < interval) break
            fallT -= interval
            if (!fallStep(p)) { blocked = true; break }
            blocked = false
        }
        if (blocked) {
            contact += dt
            if (contact > 0.18f) lock()
        } else contact = 0f
    }

    private fun lock() {
        val p = piece ?: return
        locks++
        var danger = false
        val id = nextId++
        for (c in p.cells) {
            val x = p.px + c[0]
            val y = p.py + c[1]
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
