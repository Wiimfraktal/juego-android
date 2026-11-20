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
    val sparkle: Float = 0f, val special: Int = 0
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
        Faction("Legión Umbra", 20, 1,
            Color.rgb(26, 28, 36), 0.85f, 0.25f, -20f, 0.75f,
            Color.rgb(190, 60, 255), Color.rgb(215, 175, 255), Color.rgb(170, 80, 255), Color.rgb(100, 40, 200),
            sparkle = 0.3f, special = 1),
        Faction("Hegemonía Zafiro", 35, 3,
            Color.rgb(205, 214, 228), 0.9f, 0.15f, 190f, 0.9f,
            Color.rgb(60, 220, 255), Color.rgb(200, 240, 255), Color.rgb(120, 220, 255), Color.rgb(60, 140, 255),
            sparkle = 0.2f),
        Faction("Consorcio Ámbar", 50, 4,
            Color.rgb(152, 112, 58), 0.7f, 0.95f, 30f, 0.85f,
            Color.rgb(255, 190, 40), Color.rgb(255, 225, 130), Color.rgb(255, 200, 80), Color.rgb(255, 130, 30),
            sparkle = 0.45f),
        Faction("Colectivo Esmeralda", 70, 2,
            Color.rgb(72, 94, 66), 0.3f, 0.2f, 90f, 0.9f,
            Color.rgb(80, 255, 140), Color.rgb(195, 255, 205), Color.rgb(120, 255, 170), Color.rgb(40, 200, 100),
            special = 2),
        Faction("Orden Carmesí", 95, 5,
            Color.rgb(112, 24, 30), 0.9f, 0.3f, 0f, 0.95f,
            Color.rgb(255, 210, 80), Color.rgb(255, 205, 175), Color.rgb(255, 90, 70), Color.rgb(255, 170, 60),
            sparkle = 0.35f),
        Faction("Alianza Nébula", 130, 6,
            Color.rgb(84, 74, 138), 0.92f, 0.5f, 250f, 0.9f,
            Color.rgb(255, 80, 200), Color.rgb(255, 190, 235), Color.rgb(255, 100, 220), Color.rgb(100, 120, 255),
            sparkle = 0.8f, special = 3)
    )
}
