package com.cristal.abismo

import kotlin.random.Random

/** Cristal: cuatro celdas relativas al pivote (px,py). */
class Piece(val type: Int, var cells: Array<IntArray>, var px: Int, var py: Int)

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
 * Tetris invertido: las piezas nacen en el centro y caen hacia el borde de la pantalla.
 * Se pierde si algo se acumula dentro de la zona central.
 */
class Game(val cols: Int, val rows: Int) {
    val cx = cols / 2
    val cy = rows / 2
    val grid = Array(rows) { IntArray(cols) }

    var piece: Piece? = null
    var gx = 0
    var gy = 1
    var score = 0
    var lines = 0
    var level = 1
    var over = false
    var overTime = 0f

    // desplazamiento visual (en celdas) para animar movimientos
    var visX = 0f
    var visY = 0f

    /** Celdas eliminadas recientemente (x, y, color) para efectos. */
    val bursts = ArrayList<IntArray>()
    var lockedFlash = 0f

    private var timer = 0f
    private var nextType = Random.nextInt(Shapes.all.size)

    init { spawn() }

    private fun interval() = maxOf(0.08f, 0.30f - 0.022f * (level - 1))

    private fun spawn() {
        val t = nextType
        nextType = Random.nextInt(Shapes.all.size)
        val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0))
        val d = dirs[Random.nextInt(4)]
        gx = d[0]; gy = d[1]
        val p = Piece(t, Array(4) { Shapes.all[t][it].copyOf() }, cx, cy)
        piece = p
        visX = 0f; visY = 0f
        timer = 0f
        if (collides(p.cells, p.px, p.py)) {
            over = true
        }
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

    private fun move(dx: Int, dy: Int): Boolean {
        val p = piece ?: return false
        if (collides(p.cells, p.px + dx, p.py + dy)) return false
        p.px += dx; p.py += dy
        visX -= dx; visY -= dy
        return true
    }

    /** El jugador dirige la pieza: se mueve y además cambia hacia dónde cae. */
    fun steer(dx: Int, dy: Int) {
        if (over || piece == null) return
        if (move(dx, dy)) { gx = dx; gy = dy }
    }

    fun rotate() {
        val p = piece ?: return
        if (over || p.type == 1) return
        val rot = Array(4) { intArrayOf(-p.cells[it][1], p.cells[it][0]) }
        val kicks = arrayOf(
            intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-1, 0),
            intArrayOf(0, 1), intArrayOf(0, -1), intArrayOf(2, 0), intArrayOf(-2, 0)
        )
        for (k in kicks) {
            if (!collides(rot, p.px + k[0], p.py + k[1])) {
                p.cells = rot
                p.px += k[0]; p.py += k[1]
                return
            }
        }
    }

    fun hardDrop(dx: Int, dy: Int) {
        if (over || piece == null) return
        gx = dx; gy = dy
        while (move(dx, dy)) { /* sigue hasta chocar */ }
        lock()
    }

    fun update(dt: Float) {
        if (lockedFlash > 0f) lockedFlash -= dt
        val decay = Math.exp((-dt * 16f).toDouble()).toFloat()
        visX *= decay; visY *= decay
        if (over) { overTime += dt; return }
        timer += dt
        while (timer >= interval() && !over) {
            timer -= interval()
            if (!move(gx, gy)) { lock(); break }
        }
    }

    private fun lock() {
        val p = piece ?: return
        var danger = false
        for (c in p.cells) {
            val x = p.px + c[0]
            val y = p.py + c[1]
            grid[y][x] = p.type + 1
            if (Math.abs(x - cx) <= 1 && Math.abs(y - cy) <= 1) danger = true
        }
        piece = null
        lockedFlash = 0.15f
        val n = clearLines()
        if (n > 0) {
            lines += n
            level = 1 + lines / 5
            score += intArrayOf(0, 100, 300, 500, 800, 1000)[minOf(n, 5)] * level
        }
        score += 10
        if (danger && n == 0) { over = true; return }
        spawn()
    }

    private fun rowFull(y: Int) = grid[y].all { it != 0 }
    private fun colFull(x: Int) = (0 until rows).all { grid[it][x] != 0 }

    private fun clearLines(): Int {
        val fr = (0 until rows).filter { it != cy && rowFull(it) }
        val fc = (0 until cols).filter { it != cx && colFull(it) }
        if (fr.isEmpty() && fc.isEmpty()) return 0
        for (y in fr) for (x in 0 until cols) { bursts.add(intArrayOf(x, y, grid[y][x])); grid[y][x] = 0 }
        for (x in fc) for (y in 0 until rows) {
            if (grid[y][x] != 0) bursts.add(intArrayOf(x, y, grid[y][x]))
            grid[y][x] = 0
        }
        // las filas se acercan al borde (hacia afuera) para rellenar huecos
        if (fr.isNotEmpty()) {
            val top = (0 until cy).filter { it !in fr }.map { grid[it].copyOf() }
            for (i in 0 until cy) grid[i] = if (i < top.size) top[i] else IntArray(cols)
            val bottom = (rows - 1 downTo cy + 1).filter { it !in fr }.map { grid[it].copyOf() }
            for (j in 0 until rows - 1 - cy) grid[rows - 1 - j] = if (j < bottom.size) bottom[j] else IntArray(cols)
        }
        if (fc.isNotEmpty()) {
            val left = (0 until cx).filter { it !in fc }.map { x -> IntArray(rows) { grid[it][x] } }
            for (i in 0 until cx) for (y in 0 until rows) grid[y][i] = if (i < left.size) left[i][y] else 0
            val right = (cols - 1 downTo cx + 1).filter { it !in fc }.map { x -> IntArray(rows) { grid[it][x] } }
            for (j in 0 until cols - 1 - cx) for (y in 0 until rows) grid[y][cols - 1 - j] = if (j < right.size) right[j][y] else 0
        }
        return fr.size + fc.size
    }

    /** Distancia (Chebyshev) de la celda ocupada más cercana al centro. */
    fun nearestFilled(): Int {
        var best = 99
        for (y in 0 until rows) for (x in 0 until cols) {
            if (grid[y][x] != 0) {
                val d = maxOf(Math.abs(x - cx), Math.abs(y - cy))
                if (d < best) best = d
            }
        }
        return best
    }
}
