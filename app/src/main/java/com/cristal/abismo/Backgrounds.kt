package com.cristal.abismo

import android.graphics.Color

/** Fondo espacial: degradado, dos nebulosas, densidad de estrellas, asteroides y adornos. */
class SpaceBg(
    val name: String, val price: Int,
    val top: Int, val mid: Int, val bottom: Int,
    val nebA: Int, val nebB: Int,
    val stars: Int, val rocks: Int,
    val galaxy: Boolean = false, val aurora: Boolean = false, val cluster: Boolean = false
)

object Backgrounds {
    val all: Array<SpaceBg> = arrayOf(
        SpaceBg("Nebulosa", 0, Color.rgb(3, 5, 18), Color.rgb(11, 7, 30), Color.rgb(3, 2, 10),
            Color.argb(60, 90, 30, 160), Color.argb(50, 20, 90, 170), 220, 3),
        SpaceBg("Esmeralda", 30, Color.rgb(2, 10, 12), Color.rgb(4, 22, 20), Color.rgb(1, 6, 6),
            Color.argb(70, 30, 160, 110), Color.argb(50, 20, 120, 150), 220, 3),
        SpaceBg("Carmesí", 45, Color.rgb(14, 3, 6), Color.rgb(30, 6, 12), Color.rgb(8, 2, 4),
            Color.argb(75, 180, 30, 50), Color.argb(55, 200, 90, 40), 200, 3),
        SpaceBg("Galaxia espiral", 70, Color.rgb(4, 4, 14), Color.rgb(8, 8, 24), Color.rgb(2, 2, 8),
            Color.argb(40, 120, 90, 200), Color.argb(35, 200, 150, 120), 260, 2, galaxy = true),
        SpaceBg("Campo de asteroides", 90, Color.rgb(6, 6, 10), Color.rgb(14, 12, 18), Color.rgb(4, 4, 6),
            Color.argb(40, 120, 100, 80), Color.argb(35, 80, 90, 120), 180, 12),
        SpaceBg("Vacío profundo", 110, Color.rgb(0, 0, 2), Color.rgb(1, 1, 4), Color.rgb(0, 0, 1),
            Color.argb(14, 60, 60, 120), Color.argb(10, 60, 80, 120), 320, 1),
        SpaceBg("Aurora cósmica", 130, Color.rgb(2, 6, 16), Color.rgb(6, 14, 28), Color.rgb(2, 4, 10),
            Color.argb(50, 40, 200, 160), Color.argb(45, 140, 60, 220), 220, 2, aurora = true),
        SpaceBg("Cúmulo dorado", 150, Color.rgb(10, 7, 4), Color.rgb(22, 16, 8), Color.rgb(5, 4, 2),
            Color.argb(65, 220, 160, 60), Color.argb(45, 230, 120, 50), 260, 3, cluster = true)
    )
}

/** Temas musicales de partida (archivo en res/raw). */
class MusicTheme(val name: String, val price: Int, val desc: String)

object MusicThemes {
    val all: Array<MusicTheme> = arrayOf(
        MusicTheme("Épica", 0, "Orquesta, tambores y guitarras"),
        MusicTheme("Tensión", 60, "Oscura y pulsante"),
        MusicTheme("Rock", 90, "Guitarras al frente"),
        MusicTheme("Heroica", 120, "Fanfarria de metales")
    )
}
