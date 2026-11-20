package com.cristal.abismo

import android.content.SharedPreferences

/** Logro: se desbloquea cuando la estadística `stat` llega a `goal`; da `reward` lingotes. */
class Ach(val id: String, val name: String, val desc: String, val stat: String, val goal: Int, val reward: Int)

/** Medallas de partida (estilo Halo). El índice coincide con la frase del anunciador. */
object Medals {
    val names = arrayOf(
        "Línea", "Doble línea", "Triple línea", "Devastación",
        "Racha", "Frenesí", "Imparable", "Arrasador", "Intocable", "Invencible",
        "Detonación", "Demolición", "Al límite"
    )
}

object Achievements {
    val all: Array<Ach> = arrayOf(
        Ach("vuelo1", "Primer vuelo", "Juega 1 partida", "games", 1, 5),
        Ach("vuelo25", "Veterano", "Juega 25 partidas", "games", 25, 30),
        Ach("vuelo100", "Piloto incansable", "Juega 100 partidas", "games", 100, 80),
        Ach("naves50", "Constructor", "Coloca 50 naves", "ships", 50, 10),
        Ach("naves500", "Arquitecto", "Coloca 500 naves", "ships", 500, 40),
        Ach("naves3000", "Astillero", "Coloca 3000 naves", "ships", 3000, 100),
        Ach("linea1", "Primera línea", "Completa 1 línea", "lines", 1, 5),
        Ach("lineas100", "Limpieza", "Completa 100 líneas", "lines", 100, 40),
        Ach("lineas1000", "Exterminador", "Completa 1000 líneas", "lines", 1000, 120),
        Ach("doble", "Doble", "Consigue una doble línea", "double", 1, 10),
        Ach("triple", "Triple", "Consigue una triple línea", "triple", 1, 25),
        Ach("devast", "Devastador", "Consigue una devastación", "quad", 1, 50),
        Ach("racha5", "Frenético", "Racha de 5 colocaciones con línea", "streak", 5, 30),
        Ach("racha10", "Imparable", "Racha de 10 colocaciones con línea", "streak", 10, 70),
        Ach("bomba1", "Detonador", "Detona 1 nave bomba", "bombs", 1, 10),
        Ach("bombas25", "Experto en demolición", "Detona 25 naves bomba", "bombs", 25, 50),
        Ach("demol", "Demolición", "Destruye 3 o más naves con una bomba", "demol", 1, 30),
        Ach("limite", "Al límite", "Limpia 5 líneas con la alarma encendida", "limit", 5, 40),
        Ach("nivel5", "Ascenso orbital", "Llega al nivel 5", "level", 5, 25),
        Ach("nivel10", "Fuga perfecta", "Llega al nivel 10", "level", 10, 80),
        Ach("pts5k", "Cinco mil", "Consigue 5000 puntos", "best", 5000, 30),
        Ach("pts25k", "Veinticinco mil", "Consigue 25000 puntos", "best", 25000, 100),
        Ach("barras50", "Forjador", "Llena 50 barras de hierro", "bars", 50, 40),
        Ach("coleccion", "Coleccionista", "Ten 10 artículos de la tienda", "owned", 10, 60)
    )

    /** Símbolos de emblema que se desbloquean con un logro (índice de símbolo -> id de logro). */
    val symbolLocks = mapOf(8 to "racha10", 9 to "devast", 10 to "bombas25", 11 to "nivel10", 12 to "pts25k", 13 to "lineas1000",
        20 to "racha5", 24 to "nivel5", 26 to "naves500", 29 to "barras50")
}

/** Estadísticas del piloto guardadas en SharedPreferences (claves "st_<nombre>"). */
class Stats(private val prefs: SharedPreferences) {
    fun get(k: String): Int = try { prefs.getInt("st_$k", 0) } catch (e: Exception) { 0 }
    fun add(k: String, v: Int = 1) { if (v != 0) put(k, get(k) + v) }
    fun max(k: String, v: Int) { if (v > get(k)) put(k, v) }
    fun put(k: String, v: Int) { try { prefs.edit().putInt("st_$k", v).apply() } catch (e: Exception) { } }
    fun unlocked(id: String) = try { prefs.getBoolean("ach_$id", false) } catch (e: Exception) { false }
    fun unlock(id: String) { try { prefs.edit().putBoolean("ach_$id", true).apply() } catch (e: Exception) { } }

    /** Devuelve los logros recién desbloqueados. */
    fun check(): List<Ach> {
        val out = ArrayList<Ach>()
        for (a in Achievements.all) if (!unlocked(a.id) && get(a.stat) >= a.goal) { unlock(a.id); out.add(a) }
        return out
    }
}
