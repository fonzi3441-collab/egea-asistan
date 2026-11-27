package com.egea.asistan.uyandirma

import android.content.Context
import com.egea.asistan.core.UyandirmaDedektoru
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Kullanıcının öğrettiği "Hey Egea" örnekleri (MFCC şablonları) ve eşik tabanı. */
object UyandirmaDeposu {
    data class Veri(val sablonlar: List<List<FloatArray>>, val tabanMesafe: Float)

    private fun dosya(ctx: Context) = File(ctx.filesDir, "uyandirma.json")

    fun varMi(ctx: Context): Boolean = dosya(ctx).exists()

    fun yukle(ctx: Context): Veri? = try {
        val o = JSONObject(dosya(ctx).readText())
        val s = o.getJSONArray("sablonlar")
        val sablonlar = (0 until s.length()).map { i ->
            val cerceveler = s.getJSONArray(i)
            (0 until cerceveler.length()).map { j ->
                val c = cerceveler.getJSONArray(j)
                FloatArray(c.length()) { k -> c.getDouble(k).toFloat() }
            }
        }
        if (sablonlar.isEmpty()) null else Veri(sablonlar, o.optDouble("taban", 0.0).toFloat())
    } catch (e: Exception) {
        null
    }

    fun kaydet(ctx: Context, sablonlar: List<List<FloatArray>>): Veri {
        val taban = UyandirmaDedektoru.tabanMesafe(sablonlar)
        val s = JSONArray()
        for (sablon in sablonlar) {
            val cerceveler = JSONArray()
            for (c in sablon) {
                val d = JSONArray()
                for (x in c) d.put((x * 1000).toInt() / 1000.0)
                cerceveler.put(d)
            }
            s.put(cerceveler)
        }
        dosya(ctx).writeText(JSONObject().put("taban", taban.toDouble()).put("sablonlar", s).toString())
        return Veri(sablonlar, taban)
    }

    fun sil(ctx: Context) {
        dosya(ctx).delete()
    }

    /** Eşik = şablonlar arası en büyük mesafe × çarpan (en az 1). */
    fun esik(v: Veri, carpan: Float): Float = maxOf(v.tabanMesafe * carpan, 1f)
}
