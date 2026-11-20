package com.cristal.abismo

import android.graphics.Color

/**
 * Superficie de planeta: paleta y diseño del relieve que usa Textures.makePlanet.
 * seed/freq: distribución y tamaño de continentes; ridge: relieve en crestas (cañones, cordilleras);
 * bands: bandas latitudinales; crack: grietas o ríos (glow = 1 son incandescentes); craters: cráteres;
 * cloudStretch: estiramiento de las nubes.
 */
class Skin(
    val name: String, val price: Int,
    val oceanDeep: Int, val oceanShallow: Int,
    val landLow: Int, val landHigh: Int, val desert: Int,
    val ice: Int, val cloud: Int, val atmo: Int, val city: Int,
    val landThr: Float, val iceBias: Float, val cloudAmt: Float,
    val seed: Float = 0f, val freq: Float = 1.7f, val ridge: Float = 0f, val bands: Float = 0f,
    val crack: Float = 0f, val crackColor: Int = 0, val crackGlow: Float = 0f,
    val craters: Float = 0f, val cloudStretch: Float = 1.6f
)

object Skins {
    val all: Array<Skin> = arrayOf(
        Skin("Terrestre", 0,
            Color.rgb(4, 22, 62), Color.rgb(28, 100, 152),
            Color.rgb(44, 92, 40), Color.rgb(125, 118, 108), Color.rgb(176, 148, 96),
            Color.rgb(238, 243, 252), Color.rgb(245, 247, 250), Color.rgb(90, 160, 255), Color.rgb(255, 190, 100),
            0.50f, 0f, 0.88f),
        Skin("Desierto", 10,
            Color.rgb(36, 20, 26), Color.rgb(112, 70, 52),
            Color.rgb(188, 112, 62), Color.rgb(146, 92, 74), Color.rgb(218, 172, 112),
            Color.rgb(240, 230, 214), Color.rgb(250, 232, 205), Color.rgb(255, 150, 80), Color.rgb(255, 170, 90),
            0.47f, -0.05f, 0.55f,
            seed = 11f, freq = 1.4f, ridge = 0.65f, bands = 0.25f, craters = 0.35f, cloudStretch = 1.0f),
        Skin("Helado", 15,
            Color.rgb(16, 44, 86), Color.rgb(72, 124, 176),
            Color.rgb(205, 224, 240), Color.rgb(240, 248, 255), Color.rgb(190, 212, 232),
            Color.rgb(248, 252, 255), Color.rgb(250, 252, 255), Color.rgb(130, 205, 255), Color.rgb(185, 230, 255),
            0.50f, 0.22f, 0.80f,
            seed = 23f, freq = 2.0f, ridge = 0.3f, crack = 0.9f, crackColor = Color.rgb(40, 90, 160)),
        Skin("Oceánico", 20,
            Color.rgb(0, 40, 96), Color.rgb(24, 168, 188),
            Color.rgb(52, 130, 62), Color.rgb(150, 160, 120), Color.rgb(200, 184, 128),
            Color.rgb(236, 246, 252), Color.rgb(246, 250, 252), Color.rgb(90, 220, 230), Color.rgb(255, 230, 150),
            0.60f, 0f, 0.90f,
            seed = 37f, freq = 3.4f),
        Skin("Violeta", 30,
            Color.rgb(28, 10, 60), Color.rgb(112, 52, 164),
            Color.rgb(70, 150, 150), Color.rgb(204, 124, 206), Color.rgb(190, 142, 204),
            Color.rgb(236, 224, 250), Color.rgb(255, 224, 246), Color.rgb(212, 112, 255), Color.rgb(255, 120, 220),
            0.52f, 0.05f, 0.85f,
            seed = 51f, freq = 2.4f, ridge = 0.8f, bands = 0.5f),
        Skin("Esmeralda", 38,
            Color.rgb(0, 44, 38), Color.rgb(30, 160, 120),
            Color.rgb(30, 110, 50), Color.rgb(170, 200, 120), Color.rgb(200, 190, 110),
            Color.rgb(235, 250, 240), Color.rgb(246, 252, 248), Color.rgb(120, 255, 170), Color.rgb(255, 255, 170),
            0.54f, 0f, 0.70f,
            seed = 67f, freq = 1.9f, ridge = 0.25f, crack = 0.7f, crackColor = Color.rgb(40, 170, 200)),
        Skin("Volcánico", 46,
            Color.rgb(40, 6, 4), Color.rgb(200, 70, 10),
            Color.rgb(40, 34, 34), Color.rgb(110, 86, 70), Color.rgb(90, 60, 40),
            Color.rgb(210, 200, 190), Color.rgb(120, 110, 105), Color.rgb(255, 110, 50), Color.rgb(255, 120, 60),
            0.50f, -0.08f, 0.45f,
            seed = 83f, freq = 1.8f, ridge = 0.55f, crack = 1.0f, crackColor = Color.rgb(255, 110, 20), crackGlow = 1f),
        Skin("Carmesí", 55,
            Color.rgb(40, 8, 24), Color.rgb(150, 40, 70),
            Color.rgb(150, 50, 40), Color.rgb(220, 130, 100), Color.rgb(230, 150, 110),
            Color.rgb(255, 235, 230), Color.rgb(255, 200, 190), Color.rgb(255, 110, 110), Color.rgb(255, 200, 140),
            0.50f, 0f, 0.75f,
            seed = 97f, freq = 1.5f, bands = 0.3f, craters = 0.9f),
        Skin("Dorado", 66,
            Color.rgb(60, 40, 10), Color.rgb(190, 150, 60),
            Color.rgb(200, 160, 70), Color.rgb(240, 210, 140), Color.rgb(250, 225, 160),
            Color.rgb(255, 250, 230), Color.rgb(255, 240, 210), Color.rgb(255, 210, 110), Color.rgb(255, 255, 200),
            0.48f, -0.05f, 0.65f,
            seed = 113f, freq = 1.2f, ridge = 0.4f, bands = 0.55f),
        Skin("Abismo", 80,
            Color.rgb(2, 6, 20), Color.rgb(20, 50, 110),
            Color.rgb(20, 30, 50), Color.rgb(80, 100, 150), Color.rgb(70, 90, 130),
            Color.rgb(190, 210, 255), Color.rgb(150, 170, 230), Color.rgb(80, 120, 255), Color.rgb(120, 200, 255),
            0.50f, 0.05f, 0.60f,
            seed = 131f, freq = 2.2f, ridge = 0.5f, crack = 0.9f, crackColor = Color.rgb(80, 200, 255), crackGlow = 1f)
    )
}
