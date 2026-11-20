package com.cristal.abismo

import kotlin.math.pow
import kotlin.math.roundToInt

/** Rango militar: grupo (0..), grado dentro del grupo (1..3) y barras acumuladas necesarias. */
class Rank(val name: String, val group: Int, val grade: Int, val bars: Int)

object Ranks {
    // grupos con tres grados (I, II, III) y grupos únicos al final
    private val graded = arrayOf(
        "Soldado", "Cabo", "Sargento", "Sargento Mayor", "Teniente", "Capitán", "Mayor",
        "Teniente Coronel", "Coronel", "Brigadier", "General", "Mariscal"
    )
    private val unique = arrayOf("Comandante", "Héroe", "Leyenda", "Mítico", "Noble", "Eclipse", "Nova", "Guardián", "Heredero")
    private val roman = arrayOf("I", "II", "III")

    val all: Array<Rank> = build()

    private fun build(): Array<Rank> {
        val names = ArrayList<Triple<String, Int, Int>>()
        names.add(Triple("Recluta", 0, 1))
        names.add(Triple("Aprendiz", 1, 1))
        for ((gi, n) in graded.withIndex()) for (g in 1..3) names.add(Triple("$n ${roman[g - 1]}", gi + 2, g))
        for ((ui, n) in unique.withIndex()) names.add(Triple(n, graded.size + 2 + ui, 1))
        return Array(names.size) { i ->
            val bars = if (i == 0) 0 else (1.2 * i.toDouble().pow(1.75)).roundToInt()
            Rank(names[i].first, names[i].second, names[i].third, bars)
        }
    }

    /** Índice del rango alcanzado con esas barras acumuladas. */
    fun indexFor(totalBars: Int): Int {
        var idx = 0
        for (i in all.indices) if (totalBars >= all[i].bars) idx = i
        return idx
    }
}
