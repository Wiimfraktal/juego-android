package com.cristal.abismo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Guarda en disco las texturas generadas para que las siguientes aperturas sean casi instantáneas.
 * Lo que se carga en cada inicio va en volcado binario de píxeles (lectura directa, sin decodificar PNG);
 * lo que solo se usa al equipar (superficies y facciones compradas) va en PNG para ocupar menos.
 * Todo se escribe primero a un temporal y luego se renombra, así nunca se lee un archivo a medias.
 */
object DiskCache {
    // subir al cambiar el aspecto o la resolución de los generadores de texturas
    private const val VER = "v10"

    private fun file(dir: File, name: String) = File(dir, "${VER}_$name")

    /** Borra archivos de versiones anteriores de la caché. */
    fun purgeOld(dir: File) {
        try {
            dir.listFiles()?.forEach { f ->
                if (Regex("^v\\d+_.*").matches(f.name) && !f.name.startsWith("${VER}_")) f.delete()
            }
        } catch (e: Exception) { }
    }

    private fun commit(tmp: File, f: File) { if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) } }

    private fun savePng(f: File, b: Bitmap) {
        val tmp = File(f.path + ".tmp")
        try { FileOutputStream(tmp).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; commit(tmp, f) } catch (e: Exception) { tmp.delete() }
    }

    private fun loadPng(f: File): Bitmap? =
        try { if (f.exists()) BitmapFactory.decodeFile(f.path) else null } catch (e: Exception) { null }

    /** Varios mapas de bits en un solo archivo: [n] y por cada uno [ancho, alto, píxeles ARGB]. */
    private fun saveRaw(f: File, arr: Array<Bitmap>) {
        val tmp = File(f.path + ".tmp")
        try {
            FileOutputStream(tmp).channel.use { ch ->
                val hd = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                hd.putInt(arr.size).flip(); while (hd.hasRemaining()) ch.write(hd)
                for (b in arr) {
                    val h2 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    h2.putInt(b.width).putInt(b.height).flip(); while (h2.hasRemaining()) ch.write(h2)
                    val buf = ByteBuffer.allocateDirect(b.byteCount)
                    b.copyPixelsToBuffer(buf); buf.flip()
                    while (buf.hasRemaining()) ch.write(buf)
                }
            }
            commit(tmp, f)
        } catch (e: Exception) { tmp.delete() }
    }

    private fun loadRaw(f: File): Array<Bitmap>? {
        if (!f.exists()) return null
        return try {
            FileInputStream(f).channel.use { ch ->
                val size = ch.size()
                val map = ch.map(FileChannel.MapMode.READ_ONLY, 0, size).order(ByteOrder.LITTLE_ENDIAN)
                val n = map.getInt()
                if (n <= 0 || n > 64) return null
                val out = arrayOfNulls<Bitmap>(n)
                for (i in 0 until n) {
                    val w = map.getInt(); val h = map.getInt()
                    if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || map.remaining() < w * h * 4) return null
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val slice = map.slice()
                    slice.limit(w * h * 4)
                    b.copyPixelsFromBuffer(slice)
                    map.position(map.position() + w * h * 4)
                    out[i] = b
                }
                Array(n) { out[it]!! }
            }
        } catch (e: Exception) { null }
    }

    private fun loadAny(dir: File, base: String): Bitmap? =
        loadRaw(file(dir, "$base.bin"))?.firstOrNull() ?: loadPng(file(dir, "$base.png"))

    private fun saveAny(dir: File, base: String, b: Bitmap, raw: Boolean) {
        if (raw) saveRaw(file(dir, "$base.bin"), arrayOf(b)) else savePng(file(dir, "$base.png"), b)
    }

    fun loadPlanet(dir: File, skin: Int, r: Int): PlanetData? {
        val b = loadAny(dir, "planet_${skin}_$r") ?: return null
        return try {
            val parts = file(dir, "planet_${skin}_$r.cit").readText().split(",")
            PlanetData(b, FloatArray(parts.size) { parts[it].toFloat() })
        } catch (e: Exception) { null }
    }

    /** ¿El planeta está guardado en formato binario (rápido)? */
    fun planetIsRaw(dir: File, skin: Int, r: Int) = file(dir, "planet_${skin}_$r.bin").exists()

    fun savePlanet(dir: File, skin: Int, r: Int, pd: PlanetData, raw: Boolean = true) {
        try {
            file(dir, "planet_${skin}_$r.cit").writeText(pd.cities.joinToString(","))
            saveAny(dir, "planet_${skin}_$r", pd.bmp, raw)
            if (raw) file(dir, "planet_${skin}_$r.png").delete()
        } catch (e: Exception) { }
    }

    fun hasPlanet(dir: File, skin: Int, r: Int) =
        (file(dir, "planet_${skin}_$r.bin").exists() || file(dir, "planet_${skin}_$r.png").exists()) && file(dir, "planet_${skin}_$r.cit").exists()

    /** Naves completas de una facción (28 sprites) en un solo archivo. */
    fun loadSprites(dir: File, fac: Int, n: Int): Array<Bitmap>? {
        val arr = loadRaw(file(dir, "spr_$fac.bin"))
        if (arr == null || arr.size != n) return null
        for (b in arr) b.setHasMipMap(true)
        return arr
    }

    fun saveSprites(dir: File, fac: Int, arr: Array<Bitmap>) = saveRaw(file(dir, "spr_$fac.bin"), arr)

    fun hasSprites(dir: File, fac: Int) = file(dir, "spr_$fac.bin").exists()

    fun loadNamed(dir: File, name: String): Bitmap? = loadAny(dir, name)

    fun saveNamed(dir: File, name: String, b: Bitmap) = saveAny(dir, name, b, true)
}
