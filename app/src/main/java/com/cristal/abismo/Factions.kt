package com.cristal.abismo

import android.graphics.Color

/**
 * Facción estelar: solo cambia la apariencia de las naves (la forma es la misma).
 * hull: color base del casco; gloss/metal: brillo y reflejo; hueShift/sat: cambio de la paleta de franjas;
 * lens/window: luces; flameA/flameB: estela del motor; sparkle: destellos; special: 1 fibra de carbono,
 * 2 camuflaje, 3 iridiscente; sfx: sonido al colocarse (res/raw/place_N).
 */
class Faction(
    val name: String, val price: Int, val sfx: Int,
    val hull: Int, val gloss: Float, val metal: Float,
    val hueShift: Float, val sat: Float,
    val lens: Int, val window: Int, val flameA: Int, val flameB: Int,
    val sparkle: Float = 0f, val special: Int = 0,
    val classified: Boolean = false
) {
    /** Color de franja de cada tipo de nave: el de siempre, con el tono desplazado según la facción. */
    fun accent(base: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f
        hsv[1] = (hsv[1] * sat).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
}

object Factions {
    val all: Array<Faction> = arrayOf(
        Faction("Vanguardia Solar", 0, 0,
            Color.rgb(66, 74, 92), 0.55f, 0.55f, 0f, 1f,
            Color.rgb(255, 60, 40), Color.rgb(255, 230, 150), Color.rgb(255, 150, 50), Color.rgb(255, 90, 40)),
        Faction("Legión Umbra", 30, 1,
            Color.rgb(26, 28, 36), 0.85f, 0.25f, -20f, 0.75f,
            Color.rgb(190, 60, 255), Color.rgb(215, 175, 255), Color.rgb(170, 80, 255), Color.rgb(100, 40, 200),
            sparkle = 0.3f, special = 1),
        Faction("Hegemonía Zafiro", 50, 3,
            Color.rgb(205, 214, 228), 0.9f, 0.15f, 190f, 0.9f,
            Color.rgb(60, 220, 255), Color.rgb(200, 240, 255), Color.rgb(120, 220, 255), Color.rgb(60, 140, 255),
            sparkle = 0.2f),
        Faction("Consorcio Ámbar", 70, 4,
            Color.rgb(152, 112, 58), 0.7f, 0.95f, 30f, 0.85f,
            Color.rgb(255, 190, 40), Color.rgb(255, 225, 130), Color.rgb(255, 200, 80), Color.rgb(255, 130, 30),
            sparkle = 0.45f),
        Faction("Colectivo Esmeralda", 100, 2,
            Color.rgb(72, 94, 66), 0.3f, 0.2f, 90f, 0.9f,
            Color.rgb(80, 255, 140), Color.rgb(195, 255, 205), Color.rgb(120, 255, 170), Color.rgb(40, 200, 100),
            special = 2),
        Faction("Orden Carmesí", 135, 5,
            Color.rgb(112, 24, 30), 0.9f, 0.3f, 0f, 0.95f,
            Color.rgb(255, 210, 80), Color.rgb(255, 205, 175), Color.rgb(255, 90, 70), Color.rgb(255, 170, 60),
            sparkle = 0.35f),
        Faction("Alianza Nébula", 180, 6,
            Color.rgb(84, 74, 138), 0.92f, 0.5f, 250f, 0.9f,
            Color.rgb(255, 80, 200), Color.rgb(255, 190, 235), Color.rgb(255, 100, 220), Color.rgb(100, 120, 255),
            sparkle = 0.8f, special = 3),
        Faction("Dominio Cobalto", 210, 3,
            Color.rgb(40, 60, 110), 0.8f, 0.6f, 200f, 0.9f,
            Color.rgb(90, 160, 255), Color.rgb(190, 220, 255), Color.rgb(100, 170, 255), Color.rgb(40, 90, 255), sparkle = 0.2f),
        Faction("Clan Ceniza", 240, 4,
            Color.rgb(80, 80, 82), 0.2f, 0.3f, 0f, 0.3f,
            Color.rgb(255, 120, 40), Color.rgb(255, 200, 150), Color.rgb(255, 140, 60), Color.rgb(200, 60, 30), special = 2),
        Faction("Liga Aurora", 265, 6,
            Color.rgb(190, 230, 215), 0.9f, 0.2f, 120f, 0.8f,
            Color.rgb(90, 255, 180), Color.rgb(200, 255, 230), Color.rgb(120, 255, 200), Color.rgb(60, 200, 255), sparkle = 0.6f, special = 3),
        Faction("Sindicato Óxido", 295, 1,
            Color.rgb(130, 70, 40), 0.3f, 0.8f, 20f, 0.8f,
            Color.rgb(255, 150, 60), Color.rgb(255, 210, 150), Color.rgb(255, 120, 40), Color.rgb(200, 70, 20), sparkle = 0.1f),
        Faction("Hermandad Jade", 320, 3,
            Color.rgb(60, 120, 90), 0.85f, 0.3f, 140f, 0.85f,
            Color.rgb(120, 255, 190), Color.rgb(210, 255, 230), Color.rgb(120, 255, 170), Color.rgb(40, 180, 120)),
        Faction("Flota Tormenta", 350, 5,
            Color.rgb(50, 55, 75), 0.7f, 0.6f, 220f, 0.7f,
            Color.rgb(200, 220, 255), Color.rgb(220, 230, 255), Color.rgb(180, 200, 255), Color.rgb(90, 110, 255), sparkle = 0.5f),
        Faction("Coalición Marfil", 380, 3,
            Color.rgb(225, 215, 190), 0.85f, 0.15f, 40f, 0.6f,
            Color.rgb(255, 200, 120), Color.rgb(255, 240, 200), Color.rgb(255, 220, 150), Color.rgb(255, 150, 80)),
        Faction("Pacto Titanio", 405, 1,
            Color.rgb(150, 155, 165), 0.75f, 1.0f, 0f, 0.5f,
            Color.rgb(120, 200, 255), Color.rgb(220, 235, 255), Color.rgb(160, 210, 255), Color.rgb(80, 140, 255), sparkle = 0.3f),
        Faction("Horda Escarlata", 435, 5,
            Color.rgb(150, 20, 30), 0.5f, 0.4f, -10f, 1.0f,
            Color.rgb(255, 60, 40), Color.rgb(255, 150, 120), Color.rgb(255, 80, 40), Color.rgb(255, 40, 30), special = 1),
        Faction("Tribunal Plata", 460, 6,
            Color.rgb(190, 195, 205), 0.95f, 0.9f, 180f, 0.4f,
            Color.rgb(150, 230, 255), Color.rgb(230, 245, 255), Color.rgb(200, 235, 255), Color.rgb(120, 180, 255), sparkle = 0.7f),
        Faction("Cónclave Índigo", 505, 2,
            Color.rgb(50, 40, 100), 0.9f, 0.5f, 260f, 0.9f,
            Color.rgb(170, 110, 255), Color.rgb(220, 190, 255), Color.rgb(160, 100, 255), Color.rgb(90, 60, 220), sparkle = 0.4f, special = 3),
        Faction("Federación Boreal", 545, 4,
            Color.rgb(170, 200, 220), 0.8f, 0.4f, 170f, 0.7f,
            Color.rgb(120, 255, 255), Color.rgb(210, 255, 255), Color.rgb(150, 255, 255), Color.rgb(80, 200, 255)),
        Faction("Corsarios del Vacío", 590, 0,
            Color.rgb(18, 18, 22), 0.6f, 0.4f, 300f, 0.9f,
            Color.rgb(255, 40, 120), Color.rgb(255, 120, 180), Color.rgb(255, 60, 140), Color.rgb(150, 30, 200), sparkle = 0.2f, special = 1),
        Faction("Clasificado", 0, 0,
            Color.rgb(0, 0, 0), 0.5f, 0.5f, 0f, 1.0f,
            Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), classified = true),
        Faction("Clasificado", 0, 0,
            Color.rgb(0, 0, 0), 0.5f, 0.5f, 0f, 1.0f,
            Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), classified = true),
        Faction("Clasificado", 0, 0,
            Color.rgb(0, 0, 0), 0.5f, 0.5f, 0f, 1.0f,
            Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), classified = true),
        Faction("Clasificado", 0, 0,
            Color.rgb(0, 0, 0), 0.5f, 0.5f, 0f, 1.0f,
            Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), Color.rgb(0, 0, 0), classified = true)
    )
}
