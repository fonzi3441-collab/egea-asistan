package com.egea.asistan.medya

import android.content.Context
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.BuildConfig
import com.egea.asistan.core.AdEslestirici
import com.egea.asistan.core.RadyoAdi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Türkiye radyoları: Radio Browser (radio-browser.info, ücretsiz ve açık veritabanı). Liste 24 saat
 * önbelleklenir; internet yoksa son liste kullanılır. Uygulama hiçbir reklam eklemez.
 */
object RadyoKatalogu {
    private const val DOSYA = "radyo_katalogu.json"
    private const val GECERLILIK = 24L * 60 * 60 * 1000
    private val SUNUCULAR = listOf(
        "de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info",
        "nl1.api.radio-browser.info", "at1.api.radio-browser.info"
    )

    @Volatile
    private var bellek: List<Istasyon> = emptyList()

    @Volatile
    var sonGuncelleme: Long = 0
        private set

    @Volatile
    var sonHata: String? = null
        private set

    /** Bellekteki/diskteki liste (ağ beklemez). */
    fun liste(ctx: Context): List<Istasyon> {
        if (bellek.isEmpty()) diskten(ctx)
        return bellek
    }

    /** Liste eskiyse ya da [zorla] ise internetten yeniler. Hata olursa eldeki liste döner. */
    suspend fun yukle(ctx: Context, zorla: Boolean = false): List<Istasyon> = withContext(Dispatchers.IO) {
        if (bellek.isEmpty()) diskten(ctx)
        val taze = System.currentTimeMillis() - sonGuncelleme < GECERLILIK
        if (!zorla && bellek.isNotEmpty() && taze) return@withContext bellek
        val metin = indir("/json/stations/search?countrycode=TR&hidebroken=true&order=clickcount&reverse=true&limit=600")
            ?: return@withContext bellek
        try {
            val dizi = JSONArray(metin)
            val yeni = kopyalariAt((0 until dizi.length()).mapNotNull { Istasyon.radioBrowserdan(dizi.getJSONObject(it)) })
            if (yeni.isNotEmpty()) {
                bellek = yeni
                sonGuncelleme = System.currentTimeMillis()
                sonHata = null
                diskeYaz(ctx)
            }
        } catch (e: Exception) {
            sonHata = "liste çözülemedi: ${e.message}"
            Log.w(AsistanApp.ETIKET, "Radyo listesi çözülemedi", e)
        }
        bellek
    }

    /** Türkiye listesinde olmayan istasyonlar için ada göre arama (tüm dünya). */
    suspend fun ara(ad: String): List<Istasyon> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(ad, "UTF-8")
        val metin = indir("/json/stations/search?name=$q&hidebroken=true&order=clickcount&reverse=true&limit=20")
            ?: return@withContext emptyList()
        try {
            val dizi = JSONArray(metin)
            kopyalariAt((0 until dizi.length()).mapNotNull { Istasyon.radioBrowserdan(dizi.getJSONObject(it)) })
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Aynı istasyonun kopyalarını atar: sadeleştirilmiş ad ("JOY Türk" = "JOY Türk Turkey") ya da aynı yayın adresi.
     * Liste dinlenme sayısına göre sıralı geldiği için en popüler kopya kalır.
     */
    fun kopyalariAt(liste: List<Istasyon>): List<Istasyon> {
        val adlar = HashSet<String>()
        val adresler = HashSet<String>()
        return liste.filter { ist ->
            val a = RadyoAdi.anahtar(ist.ad)
            val u = RadyoAdi.adresAnahtari(ist.url)
            val yeni = a !in adlar && u !in adresler
            adlar += a
            adresler += u
            yeni
        }
    }

    fun bul(liste: List<Istasyon>, sorgu: String, esik: Double = 0.75): Istasyon? =
        AdEslestirici.enIyi(sorgu, liste.map { it.ad }, esik)?.let { liste[it.first] }

    private fun indir(yol: String): String? {
        for (sunucu in SUNUCULAR.shuffled()) {
            try {
                val c = URL("https://$sunucu$yol").openConnection() as HttpURLConnection
                c.connectTimeout = 7000
                c.readTimeout = 12000
                c.setRequestProperty("User-Agent", "EgeaAsistan/${BuildConfig.VERSION_NAME}")
                if (c.responseCode == 200) return c.inputStream.bufferedReader().use { it.readText() }
                sonHata = "HTTP ${c.responseCode} ($sunucu)"
            } catch (e: Exception) {
                sonHata = "${e.javaClass.simpleName}: ${e.message} ($sunucu)"
            }
        }
        Log.w(AsistanApp.ETIKET, "Radio Browser'a ulaşılamadı: $sonHata")
        return null
    }

    private fun diskten(ctx: Context) {
        try {
            val f = File(ctx.filesDir, DOSYA)
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            val dizi = o.getJSONArray("liste")
            bellek = kopyalariAt((0 until dizi.length()).mapNotNull { Istasyon.jsondan(dizi.getJSONObject(it)) })
            sonGuncelleme = o.optLong("zaman")
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Radyo önbelleği okunamadı", e)
        }
    }

    private fun diskeYaz(ctx: Context) {
        try {
            val dizi = JSONArray()
            for (i in bellek) dizi.put(i.json())
            File(ctx.filesDir, DOSYA).writeText(JSONObject().put("zaman", sonGuncelleme).put("liste", dizi).toString())
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Radyo önbelleği yazılamadı", e)
        }
    }

    // ---------------------------------------------------------------- son çalan ve favoriler
    fun sonIstasyon(): Istasyon? = try {
        val s = AsistanApp.ornek.ayarlar.sonIstasyon
        if (s.isBlank()) null else Istasyon.jsondan(JSONObject(s))
    } catch (e: Exception) {
        null
    }

    fun sonIstasyonKaydet(i: Istasyon) {
        AsistanApp.ornek.ayarlar.sonIstasyon = i.json().toString()
    }

    fun favoriMi(i: Istasyon) = i.kimlik in AsistanApp.ornek.ayarlar.favoriIstasyonlar

    fun favoriDegistir(i: Istasyon): Boolean {
        val a = AsistanApp.ornek.ayarlar
        val k = a.favoriIstasyonlar.toMutableSet()
        val simdi = if (!k.add(i.kimlik)) {
            k.remove(i.kimlik)
            false
        } else {
            true
        }
        a.favoriIstasyonlar = k
        return simdi
    }
}
