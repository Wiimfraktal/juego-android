package com.cristal.abismo

import kotlin.math.abs
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
    /** Rotación actual (0..3), para elegir el dibujo de la nave. */
    var rot = 0
}

/** Nave ya colocada: se guarda entera para dibujarla completa y para moverla con la gravedad. */
class Placed(val id: Int, val type: Int, val rot: Int, var px: Int, var py: Int) {
    val cells: Array<IntArray> = Shapes.cells(type, rot)
    /** Desfase visual (en celdas) que se anima hasta 0 cuando la nave se recorre. */
    var vy = 0f
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

    /** Celdas de un tipo con `rot` giros de 90° (el mismo giro que usa Game.rotate). */
    fun cells(type: Int, rot: Int): Array<IntArray> {
        var c = Array(4) { all[type][it].copyOf() }
        repeat(rot and 3) { c = Array(4) { i -> intArrayOf(-c[i][1], c[i][0]) } }
        return c
    }
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
    /** Naves colocadas por identificador. */
    val placed = LinkedHashMap<Int, Placed>()
    /** Filas completadas recientemente (la vista las usa para el destello de explosión). */
    val clearedRows = ArrayList<Int>()

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
    private var progMax = 0
    private var spawnY = 0
    private var accX = 0f
    private var accY = 0f
    private var nextType = Random.nextInt(Shapes.all.size)

    init { spawn() }

    fun barNeed() = 600 + 260 * barsFilled

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
        // dirección al azar (arriba o abajo); aparece desfasada del otro lado de la línea central y la cruza
        gx = 0
        gy = if (Random.nextBoolean()) 1 else -1
        spawnY = if (gy < 0) cy + 1 else cy - 2
        val p = Piece(t, Array(4) { Shapes.all[t][it].copyOf() }, cx, spawnY)
        piece = p
        contact = 0f; blocked = false; fallT = 0f; progMax = 0; accX = 0f; accY = 0f
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

    /** Avance recorrido en su dirección desde que apareció. */
    private fun prog(py: Int) = (py - spawnY) * gy

    private fun place(p: Piece, nx: Int, ny: Int) {
        p.px = nx; p.py = ny
        progMax = max(progMax, prog(ny))
    }

    /** Un paso del jugador (puede ser diagonal). Nunca más de 1 celda por debajo del radio máximo alcanzado. */
    private fun steer(dx: Int, dy: Int): Boolean {
        val p = piece ?: return false
        if (over || (dx == 0 && dy == 0)) return false
        val nx = p.px + dx
        val ny = p.py + dy
        if (collides(p.cells, nx, ny)) return false
        if (prog(ny) < progMax) return false
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
            if (prog(ny) < progMax) continue
            if (!collides(rot, nx, ny)) {
                p.cells = rot
                p.rot = (p.rot + 1) and 3
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
        // animación del recorrido de las naves colocadas
        val st = 9f * dt
        for (sh in placed.values) if (sh.vy != 0f) sh.vy = if (abs(sh.vy) <= st) 0f else sh.vy - Math.signum(sh.vy) * st
        if (over) { overTime += dt; return }
        val p = piece ?: return
        // lenta al nacer y cada vez más rápida hacia el borde
        val frac = max(0, prog(p.py)) / (rows / 2f)
        val fr = (frac / 0.7f).coerceIn(0f, 1f)
        val factor = 0.45f + 0.55f * fr * fr * (3f - 2f * fr)
        val interval = 1f / (speed() * factor)
        fallT += dt
        var guard = 0
        while (guard++ < 6) {
            if (fallT < interval) break
            fallT -= interval
            if (!fallStep(p)) { blocked = true; fallT = 0f; break }
            blocked = false
        }
        // visual continuo: la nave se desliza entre celdas según el avance, sin saltos
        val canFall = !collides(p.cells, p.px, p.py + gy)
        val prog = if (canFall) (fallT / interval).coerceIn(0f, 1f) else 0f
        val k = min(1f, dt * 16f)
        p.fx += (p.px - p.fx) * k
        p.fy += (p.py + gy * prog - p.fy) * k
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
        placed[id] = Placed(id, p.type, p.rot, p.px, p.py)
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

    /**
     * Al completar una línea se destruyen las naves enteras que la tocan. Después, las naves de la mitad de
     * arriba se recorren hacia arriba y las de abajo hacia abajo hasta topar; si así se completa otra línea,
     * se encadena.
     */
    private fun clearLines(): Int {
        var total = 0
        var guard = 0
        while (guard++ < 5) {
            val fr = (0 until rows).filter { it != cy && rowFull(it) }
            val fc = (0 until cols).filter { it != cx && colFull(it) }
            if (fr.isEmpty() && fc.isEmpty()) break
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
            for (k in kill) placed.remove(k)
            clearedRows.addAll(fr)
            total += fr.size + fc.size
            settle()
        }
        return total
    }

    /** Lado de la nave: -1 toda arriba del centro, 1 toda abajo, 0 si toca la fila central. */
    private fun side(sh: Placed): Int {
        var up = true; var down = true
        for (c in sh.cells) {
            val y = sh.py + c[1]
            if (y >= cy) up = false
            if (y <= cy) down = false
        }
        return if (up) -1 else if (down) 1 else 0
    }

    private fun canShift(sh: Placed, dir: Int): Boolean {
        for (c in sh.cells) {
            val x = sh.px + c[0]
            val y = sh.py + c[1] + dir
            if (y < 0 || y >= rows) return false
            if (grid[y][x] != 0 && ids[y][x] != sh.id) return false
        }
        return true
    }

    private fun shift(sh: Placed, dir: Int) {
        val t = grid[sh.py + sh.cells[0][1]][sh.px + sh.cells[0][0]]
        for (c in sh.cells) { grid[sh.py + c[1]][sh.px + c[0]] = 0; ids[sh.py + c[1]][sh.px + c[0]] = 0 }
        sh.py += dir
        for (c in sh.cells) { grid[sh.py + c[1]][sh.px + c[0]] = t; ids[sh.py + c[1]][sh.px + c[0]] = sh.id }
        sh.vy -= dir
    }

    /** Gravedad hacia afuera: cada nave avanza hacia el borde de su mitad hasta topar. */
    private fun settle() {
        var moved = true
        var iter = 0
        while (moved && iter++ < rows) {
            moved = false
            val list = placed.values.sortedBy { sh ->
                val sd = side(sh)
                var e = 99
                for (c in sh.cells) {
                    val y = sh.py + c[1]
                    e = min(e, if (sd < 0) y else rows - 1 - y)
                }
                e
            }
            for (sh in list) {
                val dir = side(sh)
                if (dir != 0 && canShift(sh, dir)) { shift(sh, dir); moved = true }
            }
        }
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
