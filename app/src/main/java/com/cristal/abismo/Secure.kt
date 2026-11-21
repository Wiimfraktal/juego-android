package com.cristal.abismo

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Protección básica de los datos de progreso (lingotes, power-ups y compras): cada vez que cambian se firma
 * su valor con HMAC-SHA256 usando una clave derivada del dispositivo. Si alguien edita el archivo de
 * preferencias a mano, la firma no coincide y los valores alterados se descartan.
 * No guarda datos personales ni los envía a ningún servidor.
 */
class Secure(ctx: Context, private val prefs: SharedPreferences) {
    private val key: ByteArray = run {
        val id = try { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "fo" } catch (e: Exception) { "fo" }
        ("fuga-orbital:" + id + ":" + ctx.packageName).toByteArray()
    }

    private fun mac(data: String): String = try {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        m.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "" }

    private fun payload(keys: Array<String>) = keys.joinToString("|") { "$it=${try { prefs.getInt(it, 0) } catch (e: Exception) { 0 }}" }

    /** Comprueba la firma; si no existe la crea (primera vez), si no coincide limpia los valores protegidos. */
    fun verify() {
        val sig = try { prefs.getString(SIG, null) } catch (e: Exception) { null }
        if (sig == null) { sign(); return }
        if (sig != mac(payload(KEYS))) {
            val ed = prefs.edit()
            for (k in KEYS) ed.putInt(k, 0)
            ed.apply()
            sign()
        }
    }

    /** Guarda valores protegidos y vuelve a firmar en una sola escritura. */
    fun put(vararg kv: Pair<String, Int>) {
        try {
            val ed = prefs.edit()
            for ((k, v) in kv) ed.putInt(k, v)
            ed.commit()
            sign()
        } catch (e: Exception) { }
    }

    fun sign() { try { prefs.edit().putString(SIG, mac(payload(KEYS))).apply() } catch (e: Exception) { } }

    companion object {
        private const val SIG = "sig_v1"
        val KEYS = arrayOf("ingots", "pu_regen", "pu_shield")
    }
}

/**
 * Punto de entrada para compras dentro de la aplicación (pendiente). Cuando se integre la tienda oficial del
 * sistema, la implementación real verificará cada compra antes de entregar el contenido y nunca guardará datos
 * de pago en el teléfono. Mientras tanto no hay productos y no se hace ninguna conexión.
 */
interface PurchaseProvider {
    val available: Boolean
    fun products(): List<Product>
    fun buy(id: String, onResult: (Boolean) -> Unit)

    class Product(val id: String, val title: String, val kind: Kind) {
        enum class Kind { CURRENCY, BUNDLE }
    }
}

object NoPurchases : PurchaseProvider {
    override val available = false
    override fun products() = emptyList<PurchaseProvider.Product>()
    override fun buy(id: String, onResult: (Boolean) -> Unit) = onResult(false)
}
