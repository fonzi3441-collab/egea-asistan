package com.egea.asistan.veri

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.Izinler
import com.egea.asistan.core.TurkceMetin
import com.egea.asistan.core.VcfOkuyucu
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Kişi kaynakları: 1) elle eklenen favoriler, 2) Android rehberi (ContactsContract),
 * 3) içe aktarılan .vcf dosyası. Aynı isim tek kişi olarak birleştirilir.
 */
class Rehber(private val ctx: Context) {
    data class Numara(val numara: String, val tur: String, val birincil: Boolean)

    data class Kisi(val ad: String, val numaralar: List<Numara>, val kaynak: String) {
        /** Birincil işaretli → cep → ilk numara. */
        fun tercihliNumara(): Numara? =
            numaralar.firstOrNull { it.birincil } ?: numaralar.firstOrNull { it.tur == "cep" } ?: numaralar.firstOrNull()
    }

    private val vcfDosyasi = File(ctx.filesDir, "vcf_rehber.json")

    fun tumKisiler(): List<Kisi> {
        val harita = LinkedHashMap<String, Kisi>()
        fun ekle(k: Kisi) {
            val anahtar = TurkceMetin.katla(k.ad)
            if (anahtar.isEmpty() || k.numaralar.isEmpty()) return
            val eski = harita[anahtar]
            harita[anahtar] = if (eski == null) k else eski.copy(numaralar = (eski.numaralar + k.numaralar).distinctBy { sade(it.numara) })
        }
        AsistanApp.ornek.ayarlar.favoriler.forEach { ekle(Kisi(it.ad, listOf(Numara(it.numara, "favori", true)), "favori")) }
        rehberKisileri().forEach { ekle(it) }
        vcfKisileri().forEach { ekle(it) }
        return harita.values.toList()
    }

    /** Android rehberi. İzin yoksa boş liste. */
    fun rehberKisileri(): List<Kisi> {
        if (!Izinler.kisiler(ctx)) return emptyList()
        val sonuc = LinkedHashMap<String, MutableList<Numara>>()
        try {
            // Sütun adları sabit metin olarak yazıldı: display_name, data1 (numara), data2 (tür).
            val projeksiyon = arrayOf("display_name", "data1", "data2", "is_super_primary", "is_primary")
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projeksiyon, null, null, null
            )?.use { c ->
                val iAd = c.getColumnIndex("display_name")
                val iNo = c.getColumnIndex("data1")
                val iTur = c.getColumnIndex("data2")
                val iSuper = c.getColumnIndex("is_super_primary")
                val iBir = c.getColumnIndex("is_primary")
                while (c.moveToNext()) {
                    val ad = if (iAd >= 0) c.getString(iAd) else null
                    val no = if (iNo >= 0) c.getString(iNo) else null
                    if (ad.isNullOrBlank() || no.isNullOrBlank()) continue
                    val tur = when (if (iTur >= 0) c.getInt(iTur) else 0) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "cep"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "ev"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "iş"
                        else -> "diğer"
                    }
                    val birincil = (iSuper >= 0 && c.getInt(iSuper) > 0) || (iBir >= 0 && c.getInt(iBir) > 0)
                    sonuc.getOrPut(ad) { mutableListOf() } += Numara(no, tur, birincil)
                }
            }
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Rehber okunamadı", e)
        }
        return sonuc.map { (ad, nolar) -> Kisi(ad, nolar.distinctBy { sade(it.numara) }, "rehber") }
    }

    fun vcfKisileri(): List<Kisi> = try {
        if (!vcfDosyasi.exists()) emptyList() else {
            val dizi = JSONArray(vcfDosyasi.readText())
            (0 until dizi.length()).map { i ->
                val o = dizi.getJSONObject(i)
                val nd = o.getJSONArray("n")
                Kisi(
                    o.getString("ad"),
                    (0 until nd.length()).map { j ->
                        val no = nd.getJSONObject(j)
                        Numara(no.getString("no"), no.optString("tur"), j == 0 && no.optString("tur") == "cep")
                    },
                    "vcf"
                )
            }
        }
    } catch (e: Exception) {
        Log.w(AsistanApp.ETIKET, "VCF rehberi okunamadı", e)
        emptyList()
    }

    fun vcfKaydet(kisiler: List<VcfOkuyucu.Kisi>) {
        val dizi = JSONArray()
        for (k in kisiler) {
            val nd = JSONArray()
            for (n in k.numaralar) nd.put(JSONObject().put("no", n.numara).put("tur", n.tur))
            dizi.put(JSONObject().put("ad", k.ad).put("n", nd))
        }
        vcfDosyasi.writeText(dizi.toString())
    }

    fun vcfTemizle() {
        vcfDosyasi.delete()
    }

    fun vcfSayisi(): Int = vcfKisileri().size

    companion object {
        /** Karşılaştırma için numarayı sadeleştirir (+90/0 öneki ve boşluklar). */
        fun sade(numara: String): String {
            val r = numara.filter { it.isDigit() }
            return when {
                r.startsWith("90") && r.length == 12 -> r.substring(2)
                r.startsWith("0") && r.length == 11 -> r.substring(1)
                else -> r
            }
        }
    }
}
