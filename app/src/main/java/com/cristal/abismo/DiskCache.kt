package com.cristal.abismo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File
import java.io.FileOutputStream

/** Guarda en disco las texturas generadas para que las siguientes aperturas sean casi instantáneas. */
object DiskCache {
    // subir al cambiar el aspecto de los generadores de texturas
    private const val VER = "v6"

    private fun file(dir: File, name: String) = File(dir, "${VER}_$name")

    private fun saveBmp(f: File, b: Bitmap) {
        try { FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { f.delete() }
    }

    private fun loadBmp(f: File): Bitmap? =
        try { if (f.exists()) BitmapFactory.decodeFile(f.path) else null } catch (e: Exception) { null }

    fun loadPlanet(dir: File, skin: Int, r: Int): PlanetData? {
        val b = loadBmp(file(dir, "planet_${skin}_$r.png")) ?: return null
        return try {
            val parts = file(dir, "planet_${skin}_$r.cit").readText().split(",")
            PlanetData(b, FloatArray(parts.size) { parts[it].toFloat() })
        } catch (e: Exception) { null }
    }

    fun savePlanet(dir: File, skin: Int, r: Int, pd: PlanetData) {
        try {
            file(dir, "planet_${skin}_$r.cit").writeText(pd.cities.joinToString(","))
            saveBmp(file(dir, "planet_${skin}_$r.png"), pd.bmp)
        } catch (e: Exception) { }
    }

    /** Naves completas de una facción (28 sprites). */
    fun loadSprites(dir: File, fac: Int, n: Int): Array<Bitmap>? {
        val arr = arrayOfNulls<Bitmap>(n)
        for (i in 0 until n) {
            val b = loadBmp(file(dir, "spr_${fac}_$i.png")) ?: return null
            b.setHasMipMap(true)
            arr[i] = b
        }
        return Array(n) { arr[it]!! }
    }

    fun saveSprites(dir: File, fac: Int, arr: Array<Bitmap>) {
        for (i in arr.indices) saveBmp(file(dir, "spr_${fac}_$i.png"), arr[i])
    }

    fun hasPlanet(dir: File, skin: Int, r: Int) = file(dir, "planet_${skin}_$r.png").exists() && file(dir, "planet_${skin}_$r.cit").exists()

    fun hasSprites(dir: File, fac: Int, n: Int) = (0 until n).all { file(dir, "spr_${fac}_$it.png").exists() }

    fun loadNamed(dir: File, name: String): Bitmap? = loadBmp(file(dir, name))

    fun saveNamed(dir: File, name: String, b: Bitmap) = saveBmp(file(dir, name), b)

    fun loadThumb(dir: File, skin: Int, r: Int): Bitmap? = loadBmp(file(dir, "thumb_${skin}_$r.png"))

    fun saveThumb(dir: File, skin: Int, r: Int, b: Bitmap) = saveBmp(file(dir, "thumb_${skin}_$r.png"), b)

    /** Las 112 placas de naves se guardan en un solo atlas de 16 columnas. */
    fun loadShips(dir: File, count: Int, fac: Int): Array<Bitmap>? {
        val atlas = loadBmp(file(dir, "ships_$fac.png")) ?: return null
        val t = 128
        if (atlas.width != 16 * t || atlas.height != (count / 16) * t) return null
        return try {
            Array(count) {
                val b = Bitmap.createBitmap(atlas, (it % 16) * t, (it / 16) * t, t, t)
                b.setHasMipMap(true)
                b
            }
        } catch (e: Exception) { null }
    }

    fun saveShips(dir: File, tiles: Array<Bitmap>, fac: Int) {
        val t = 128
        val atlas = Bitmap.createBitmap(16 * t, (tiles.size / 16) * t, Bitmap.Config.ARGB_8888)
        val c = Canvas(atlas)
        for (i in tiles.indices) c.drawBitmap(tiles[i], ((i % 16) * t).toFloat(), ((i / 16) * t).toFloat(), null)
        saveBmp(file(dir, "ships_$fac.png"), atlas)
    }
}
