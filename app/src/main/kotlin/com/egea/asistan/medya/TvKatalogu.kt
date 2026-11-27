package com.egea.asistan.medya

import android.content.Context
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.BuildConfig
import com.egea.asistan.core.AdEslestirici
import com.egea.asistan.core.M3uOkuyucu
import com.egea.asistan.core.TurkceMetin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Canlı TV kanalları: M3U listesi. Varsayılan liste iptv-org projesinin Türkiye listesidir (herkese açık,
 * ücretsiz yayınlar). Ayarlardan başka bir M3U adresi girilebilir. Liste 12 saat önbelleklenir.
 */
object TvKatalogu {
    const val VARSAYILAN_LISTE = "https://iptv-org.github.io/iptv/countries/tr.m3u"
    private const val DOSYA = "tv_listesi.m3u"
    private const val GECERLILIK = 12L * 60 * 60 * 1000

    /** Önce gösterilecek popüler ulusal kanallar (katlanmış ad önekleri). */
    private val ONCELIK = listOf(
        "trt 1", "show tv", "kanal d", "atv", "star tv", "now", "tv8", "trt haber", "haberturk", "cnn turk", "ntv",
        "a haber", "haber global", "tgrt haber", "trt spor", "trt belgesel", "trt cocuk", "trt muzik", "beyaz tv",
        "kanal 7", "360", "halk tv", "sozcu", "tv100", "trt 2", "trt turk", "trt avaz", "a spor", "a2tv", "teve2"
    )

    @Volatile
    private var bellek: List<M3uOkuyucu.Kanal> = emptyList()

    @Volatile
    var sonGuncelleme: Long = 0
        private set

    @Volatile
    var sonHata: String? = null
        private set

    fun listeAdresi(): String = AsistanApp.ornek.ayarlar.tvListesi.ifBlank { VARSAYILAN_LISTE }

    fun liste(ctx: Context): List<M3uOkuyucu.Kanal> {
        if (bellek.isEmpty()) diskten(ctx)
        return bellek
    }

    suspend fun yukle(ctx: Context, zorla: Boolean = false): List<M3uOkuyucu.Kanal> = withContext(Dispatchers.IO) {
        if (bellek.isEmpty()) diskten(ctx)
        val taze = System.currentTimeMillis() - sonGuncelleme < GECERLILIK
        if (!zorla && bellek.isNotEmpty() && taze) return@withContext bellek
        try {
            var url = URL(listeAdresi())
            var metin: String? = null
            for (adim in 0 until 5) {
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 15000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "EgeaAsistan/${BuildConfig.VERSION_NAME}")
                val kod = c.responseCode
                if (kod in 300..399) {
                    url = URL(url, c.getHeaderField("Location") ?: break)
                    continue
                }
                if (kod == 200) metin = c.inputStream.bufferedReader().use { it.readText() } else sonHata = "HTTP $kod"
                break
            }
            if (metin != null) {
                val kanallar = sirala(M3uOkuyucu.oku(metin))
                if (kanallar.isNotEmpty()) {
                    bellek = kanallar
                    sonGuncelleme = System.currentTimeMillis()
                    sonHata = null
                    File(ctx.filesDir, DOSYA).writeText(metin)
                } else {
                    sonHata = "listede kanal yok"
                }
            }
        } catch (e: Exception) {
            sonHata = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(AsistanApp.ETIKET, "TV listesi indirilemedi", e)
        }
        bellek
    }

    private fun sirala(l: List<M3uOkuyucu.Kanal>): List<M3uOkuyucu.Kanal> {
        fun oncelik(k: M3uOkuyucu.Kanal): Int {
            val ad = TurkceMetin.katla(k.ad)
            val i = ONCELIK.indexOfFirst { ad == it || ad.startsWith("$it ") }
            return if (i >= 0) i else ONCELIK.size
        }
        return l.distinctBy { it.url }.sortedWith(compareBy({ oncelik(it) }, { TurkceMetin.katla(it.ad) }))
    }

    private fun diskten(ctx: Context) {
        try {
            val f = File(ctx.filesDir, DOSYA)
            if (!f.exists()) return
            bellek = sirala(M3uOkuyucu.oku(f.readText()))
            sonGuncelleme = f.lastModified()
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "TV önbelleği okunamadı", e)
        }
    }

    fun bul(liste: List<M3uOkuyucu.Kanal>, sorgu: String, esik: Double = 0.8): M3uOkuyucu.Kanal? =
        AdEslestirici.enIyi(sorgu, liste.map { it.ad }, esik)?.let { liste[it.first] }

    fun sonKanal(liste: List<M3uOkuyucu.Kanal>): M3uOkuyucu.Kanal? {
        val url = AsistanApp.ornek.ayarlar.sonKanal
        return liste.firstOrNull { it.url == url }
    }

    /** M3U grup adlarının Türkçesi. */
    fun grupAdi(grup: String?): String {
        val g = grup?.split(';')?.firstOrNull()?.trim().orEmpty()
        return when (g.lowercase()) {
            "general" -> "Genel"
            "news" -> "Haber"
            "kids" -> "Çocuk"
            "music" -> "Müzik"
            "sports" -> "Spor"
            "entertainment" -> "Eğlence"
            "documentary" -> "Belgesel"
            "religious" -> "Dini"
            "education" -> "Eğitim"
            "movies" -> "Film"
            "series" -> "Dizi"
            "culture" -> "Kültür"
            "business" -> "Ekonomi"
            "legislative" -> "Meclis"
            "weather" -> "Hava"
            "shop" -> "Alışveriş"
            "lifestyle" -> "Yaşam"
            "cooking" -> "Yemek"
            "travel" -> "Gezi"
            "outdoor" -> "Doğa"
            "auto" -> "Otomobil"
            "", "undefined" -> "Diğer"
            else -> g
        }
    }
}
