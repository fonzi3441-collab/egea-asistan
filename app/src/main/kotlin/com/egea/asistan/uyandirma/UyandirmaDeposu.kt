package com.egea.asistan.uyandirma

import android.content.Context
import com.egea.asistan.core.UyandirmaDedektoru
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Kullanıcının öğrettiği "Hey Egea" örnekleri: her kaydın MFCC şablonu ve hangi söz olduğu
 * (0 = "Hey Egea", 1 = "Hey Ege"). Eşik artık kayıtlardan dedektör kurulurken hesaplanır.
 */
object UyandirmaDeposu {
    class Veri(val sablonlar: List<List<FloatArray>>, val gruplar: IntArray)

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
        // Eski sürümün dosyasında söz bilgisi yok: öğretme ekranının sırası (2 × "Hey Egea", 2 × "Hey Ege").
        val g = o.optJSONArray("gruplar")
        val gruplar = if (g != null && g.length() == sablonlar.size) {
            IntArray(g.length()) { g.getInt(it) }
        } else {
            UyandirmaDedektoru.varsayilanGruplar(sablonlar.size)
        }
        if (sablonlar.isEmpty()) null else Veri(sablonlar, gruplar)
    } catch (e: Exception) {
        null
    }

    fun kaydet(ctx: Context, sablonlar: List<List<FloatArray>>, gruplar: IntArray): Veri {
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
        val g = JSONArray()
        for (x in gruplar) g.put(x)
        dosya(ctx).writeText(JSONObject().put("surum", 2).put("gruplar", g).put("sablonlar", s).toString())
        return Veri(sablonlar, gruplar)
    }

    fun sil(ctx: Context) {
        dosya(ctx).delete()
    }

    /** Kayıtlı örneklerden, verilen duyarlılıkla dedektör. */
    fun dedektor(v: Veri, carpan: Float): UyandirmaDedektoru = UyandirmaDedektoru(v.sablonlar, v.gruplar, carpan)
}
