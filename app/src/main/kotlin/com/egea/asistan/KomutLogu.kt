package com.egea.asistan

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Son 50 komutun kaydı: tanınan metin → niyet → yöntem → sonuç. Tanı ekranında gösterilir. */
class KomutLogu(ctx: Context) {
    data class Kayit(val zaman: Long, val metin: String, val niyet: String, val yontem: String, val sonuc: String)

    private val dosya = File(ctx.filesDir, "komut_logu.json")
    private val kayitlar = ArrayList<Kayit>()

    init {
        yukle()
    }

    @Synchronized
    fun ekle(metin: String, niyet: String, yontem: String, sonuc: String) {
        Log.i(AsistanApp.ETIKET, "KOMUT \"$metin\" → $niyet → $yontem → $sonuc")
        kayitlar.add(0, Kayit(System.currentTimeMillis(), metin, niyet, yontem, sonuc))
        while (kayitlar.size > ENFAZLA) kayitlar.removeAt(kayitlar.lastIndex)
        kaydet()
    }

    @Synchronized
    fun hepsi(): List<Kayit> = ArrayList(kayitlar)

    @Synchronized
    fun temizle() {
        kayitlar.clear()
        kaydet()
    }

    private fun yukle() {
        try {
            if (!dosya.exists()) return
            val dizi = JSONArray(dosya.readText())
            for (i in 0 until dizi.length()) {
                val o = dizi.getJSONObject(i)
                kayitlar += Kayit(
                    o.optLong("z"), o.optString("m"), o.optString("n"), o.optString("y"), o.optString("s")
                )
            }
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Komut logu okunamadı", e)
        }
    }

    private fun kaydet() {
        try {
            val dizi = JSONArray()
            for (k in kayitlar) {
                dizi.put(JSONObject().put("z", k.zaman).put("m", k.metin).put("n", k.niyet).put("y", k.yontem).put("s", k.sonuc))
            }
            dosya.writeText(dizi.toString())
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Komut logu yazılamadı", e)
        }
    }

    companion object {
        const val ENFAZLA = 50
    }
}
