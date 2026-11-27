package com.egea.asistan

import android.content.Context
import android.content.SharedPreferences
import com.egea.asistan.core.UyandirmaDedektoru
import org.json.JSONArray
import org.json.JSONObject

data class Favori(val ad: String, val numara: String)

/** Kalıcı ayarlar (SharedPreferences). */
class Ayarlar(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("egea_ayarlar", Context.MODE_PRIVATE)

    private fun yazi(anahtar: String, varsayilan: String = "") = sp.getString(anahtar, varsayilan) ?: varsayilan
    private fun yaziKaydet(anahtar: String, deger: String) = sp.edit().putString(anahtar, deger).apply()

    var kurulumTamam: Boolean
        get() = sp.getBoolean("kurulum_tamam", false)
        set(v) = sp.edit().putBoolean("kurulum_tamam", v).apply()

    /** "otomatik", "google", "waze", "yandex" */
    var navUygulama: String
        get() = yazi("nav_uygulama", "otomatik")
        set(v) = yaziKaydet("nav_uygulama", v)

    /** "otomatik", "spotify", "ytmusic", "youtube" */
    var muzikUygulama: String
        get() = yazi("muzik_uygulama", "otomatik")
        set(v) = yaziKaydet("muzik_uygulama", v)

    var evAdresi: String
        get() = yazi("ev_adresi")
        set(v) = yaziKaydet("ev_adresi", v)

    var isAdresi: String
        get() = yazi("is_adresi")
        set(v) = yaziKaydet("is_adresi", v)

    var youtubeAnahtari: String
        get() = yazi("youtube_anahtari")
        set(v) = yaziKaydet("youtube_anahtari", v.trim())

    /** "otomatik", "ozel_once", "call", "telecom", "dial", "ozel" */
    var aramaYontemi: String
        get() = yazi("arama_yontemi", "otomatik")
        set(v) = yaziKaydet("arama_yontemi", v)

    /** Özel arama intent'i: "activity", "broadcast", "service" */
    var ozelTur: String
        get() = yazi("ozel_tur", "activity")
        set(v) = yaziKaydet("ozel_tur", v)
    var ozelPaket: String
        get() = yazi("ozel_paket")
        set(v) = yaziKaydet("ozel_paket", v.trim())
    var ozelSinif: String
        get() = yazi("ozel_sinif")
        set(v) = yaziKaydet("ozel_sinif", v.trim())
    var ozelAction: String
        get() = yazi("ozel_action")
        set(v) = yaziKaydet("ozel_action", v.trim())
    var ozelData: String
        get() = yazi("ozel_data")
        set(v) = yaziKaydet("ozel_data", v.trim())
    var ozelExtraAnahtar: String
        get() = yazi("ozel_extra_anahtar")
        set(v) = yaziKaydet("ozel_extra_anahtar", v.trim())
    var ozelExtraDeger: String
        get() = yazi("ozel_extra_deger", "{numara}")
        set(v) = yaziKaydet("ozel_extra_deger", v.trim())

    val ozelTanimli: Boolean get() = ozelAction.isNotBlank() || ozelPaket.isNotBlank()

    /** Direksiyon tuşu kodu (KeyEvent keyCode); 0 = ayarlı değil. */
    var tetikTusKodu: Int
        get() = sp.getInt("tetik_tus_kodu", 0)
        set(v) = sp.edit().putInt("tetik_tus_kodu", v).apply()

    var yuzenButon: Boolean
        get() = sp.getBoolean("yuzen_buton", true)
        set(v) = sp.edit().putBoolean("yuzen_buton", v).apply()

    var balonX: Int
        get() = sp.getInt("balon_x", -1)
        set(v) = sp.edit().putInt("balon_x", v).apply()

    var balonY: Int
        get() = sp.getInt("balon_y", -1)
        set(v) = sp.edit().putInt("balon_y", v).apply()

    // ---------------------------------------------------------------- Hey Egea
    /** "Hey Egea" uyandırma kelimesiyle dinleme açık mı (öğretme yapılınca açılır). */
    var uyandirma: Boolean
        get() = sp.getBoolean("uyandirma", false)
        set(v) = sp.edit().putBoolean("uyandirma", v).apply()

    /**
     * Duyarlılık çarpanı: küçük = seçici (az yanlış alarm), büyük = kolay tetiklenir (1.1 … 2.0). Eski sürümün ölçeği
     * farklıydı; eski değer yanlış anlaşılmasın diye yeni anahtar kullanılır.
     */
    var uyandirmaCarpan: Float
        get() = sp.getFloat("uyandirma_carpan2", UyandirmaDedektoru.VARSAYILAN_CARPAN)
        set(v) = sp.edit().putFloat("uyandirma_carpan2", v).apply()

    /** "tanima" (VOICE_RECOGNITION), "iletisim" (VOICE_COMMUNICATION, yankı giderme), "mikrofon" (MIC) */
    var uyandirmaKaynak: String
        get() = yazi("uyandirma_kaynak", "tanima")
        set(v) = yaziKaydet("uyandirma_kaynak", v)

    /** Her dinlemede (buton/tuş ile de) önce "Dinliyorum" desin mi? Hey Egea ile her zaman der. */
    var dinliyorumDe: Boolean
        get() = sp.getBoolean("dinliyorum_de", false)
        set(v) = sp.edit().putBoolean("dinliyorum_de", v).apply()

    // ---------------------------------------------------------------- Radyo / TV / arayüz
    var sonIstasyon: String
        get() = yazi("son_istasyon")
        set(v) = yaziKaydet("son_istasyon", v)

    var favoriIstasyonlar: Set<String>
        get() = sp.getStringSet("favori_istasyonlar", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("favori_istasyonlar", v).apply()

    /** Boşsa varsayılan liste (iptv-org Türkiye) kullanılır. */
    var tvListesi: String
        get() = yazi("tv_listesi")
        set(v) = yaziKaydet("tv_listesi", v.trim())

    var sonKanal: String
        get() = yazi("son_kanal")
        set(v) = yaziKaydet("son_kanal", v)

    /** Araç hareket halindeyken TV görüntüsünü gizle (ses devam eder). */
    var surusKilidi: Boolean
        get() = sp.getBoolean("surus_kilidi", true)
        set(v) = sp.edit().putBoolean("surus_kilidi", v).apply()

    var fpsGoster: Boolean
        get() = sp.getBoolean("fps_goster", false)
        set(v) = sp.edit().putBoolean("fps_goster", v).apply()

    var favoriler: List<Favori>
        get() = try {
            val dizi = JSONArray(yazi("favoriler", "[]"))
            (0 until dizi.length()).mapNotNull { i ->
                val o = dizi.optJSONObject(i) ?: return@mapNotNull null
                val ad = o.optString("ad")
                val no = o.optString("numara")
                if (ad.isBlank() || no.isBlank()) null else Favori(ad, no)
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val dizi = JSONArray()
            for (f in v) dizi.put(JSONObject().put("ad", f.ad).put("numara", f.numara))
            yaziKaydet("favoriler", dizi.toString())
        }
}
