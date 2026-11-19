package com.cristal.abismo

import android.graphics.Color

/** Aspecto del planeta: paletas y umbrales que usa Textures.makePlanet. */
class Skin(
    val name: String, val price: Int,
    val oceanDeep: Int, val oceanShallow: Int,
    val landLow: Int, val landHigh: Int, val desert: Int,
    val ice: Int, val cloud: Int, val atmo: Int, val city: Int,
    val landThr: Float, val iceBias: Float, val cloudAmt: Float
)

object Skins {
    val all: Array<Skin> = arrayOf(
        Skin("Terrestre", 0,
            Color.rgb(4, 22, 62), Color.rgb(28, 100, 152),
            Color.rgb(44, 92, 40), Color.rgb(125, 118, 108), Color.rgb(176, 148, 96),
            Color.rgb(238, 243, 252), Color.rgb(245, 247, 250), Color.rgb(90, 160, 255), Color.rgb(255, 190, 100),
            0.50f, 0f, 0.88f),
        Skin("Desierto", 8,
            Color.rgb(36, 20, 26), Color.rgb(112, 70, 52),
            Color.rgb(188, 112, 62), Color.rgb(146, 92, 74), Color.rgb(218, 172, 112),
            Color.rgb(240, 230, 214), Color.rgb(250, 232, 205), Color.rgb(255, 150, 80), Color.rgb(255, 170, 90),
            0.47f, -0.05f, 0.55f),
        Skin("Helado", 12,
            Color.rgb(16, 44, 86), Color.rgb(72, 124, 176),
            Color.rgb(205, 224, 240), Color.rgb(240, 248, 255), Color.rgb(190, 212, 232),
            Color.rgb(248, 252, 255), Color.rgb(250, 252, 255), Color.rgb(130, 205, 255), Color.rgb(185, 230, 255),
            0.50f, 0.22f, 0.80f),
        Skin("Oceánico", 16,
            Color.rgb(0, 40, 96), Color.rgb(24, 168, 188),
            Color.rgb(52, 130, 62), Color.rgb(150, 160, 120), Color.rgb(200, 184, 128),
            Color.rgb(236, 246, 252), Color.rgb(246, 250, 252), Color.rgb(90, 220, 230), Color.rgb(255, 230, 150),
            0.60f, 0f, 0.90f),
        Skin("Violeta", 24,
            Color.rgb(28, 10, 60), Color.rgb(112, 52, 164),
            Color.rgb(70, 150, 150), Color.rgb(204, 124, 206), Color.rgb(190, 142, 204),
            Color.rgb(236, 224, 250), Color.rgb(255, 224, 246), Color.rgb(212, 112, 255), Color.rgb(255, 120, 220),
            0.52f, 0.05f, 0.85f)
    )
}
