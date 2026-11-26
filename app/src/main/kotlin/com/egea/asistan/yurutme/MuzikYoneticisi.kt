package com.egea.asistan.yurutme

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.core.HedefUygulama
import com.egea.asistan.core.Niyet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Şarkı açma / arama. Spotify ve YouTube Music: önce açık medya oturumunda playFromSearch (ekran değişmez),
 * olmazsa paket belirtilmiş MEDIA_PLAY_FROM_SEARCH. YouTube: API anahtarı varsa ilk videoyu bulup açar,
 * yoksa arama sonuçlarını açar.
 */
class MuzikYoneticisi(private val ctx: Context, private val medya: MedyaYoneticisi) {
    data class MuzikUygulama(val kod: String, val ad: String, val paket: String)

    private val ayarlar get() = AsistanApp.ornek.ayarlar

    @Suppress("DEPRECATION")
    fun yukluMu(paket: String): Boolean = try {
        ctx.packageManager.getPackageInfo(paket, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun secili(): MuzikUygulama? {
        val s = HEPSI.firstOrNull { it.kod == ayarlar.muzikUygulama }
        if (s != null && yukluMu(s.paket)) return s
        return HEPSI.firstOrNull { yukluMu(it.paket) }
    }

    /** Konuşmada söylenen uygulama varsa o, yoksa ayarlardaki/yüklü müzik uygulaması. */
    fun hedef(h: HedefUygulama?): MuzikUygulama? = when (h) {
        HedefUygulama.SPOTIFY -> SPOTIFY
        HedefUygulama.YOUTUBE_MUSIC -> YTMUSIC
        HedefUygulama.YOUTUBE -> YOUTUBE
        HedefUygulama.GOOGLE -> null
        null -> secili()
    }

    @Suppress("DEPRECATION")
    private fun cozuluyor(i: Intent): Boolean = try {
        ctx.packageManager.queryIntentActivities(i, 0).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private fun baslat(aktivite: Activity, i: Intent): Boolean {
        if (!cozuluyor(i)) return false
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            aktivite.startActivity(i)
            true
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Başlatılamadı: $i", e)
            false
        }
    }

    private fun aramaIntenti(paket: String?, sorgu: String) = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        if (paket != null) setPackage(paket)
        putExtra(SearchManager.QUERY, sorgu)
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
    }

    private fun goruntule(uri: String, paket: String?) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        if (paket != null) setPackage(paket)
    }

    /** Açık medya oturumu üzerinden çal (ekran değişmez). */
    private fun oturumlaCal(paket: String, sorgu: String): Boolean {
        val c = medya.aktifKontrolcu(paket) ?: return false
        val eylemler = c.playbackState?.actions ?: 0L
        if (eylemler and PlaybackState.ACTION_PLAY_FROM_SEARCH == 0L) return false
        return try {
            c.transportControls.playFromSearch(sorgu, Bundle())
            true
        } catch (e: Exception) {
            false
        }
    }

    /** YouTube Data API ile ilk videonun kimliği; anahtar yok/hata varsa null. */
    suspend fun youtubeVideoBul(sorgu: String): String? {
        val anahtar = ayarlar.youtubeAnahtari
        if (anahtar.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1" +
                        "&regionCode=TR&relevanceLanguage=tr&q=" + URLEncoder.encode(sorgu, "UTF-8") +
                        "&key=" + URLEncoder.encode(anahtar, "UTF-8")
                )
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                // Android'e kısıtlanmış API anahtarları için paket adı ve imza parmak izi.
                c.setRequestProperty("X-Android-Package", ctx.packageName)
                imzaSha1(ctx)?.let { c.setRequestProperty("X-Android-Cert", it.replace(":", "")) }
                val kod = c.responseCode
                if (kod != 200) {
                    Log.w(AsistanApp.ETIKET, "YouTube API yanıtı: $kod")
                    return@withContext null
                }
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                json.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("id")?.optString("videoId")
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w(AsistanApp.ETIKET, "YouTube API hatası", e)
                null
            }
        }
    }

    /** Şarkıyı çalar ya da arar; kullanılan yöntemi döndürür. */
    suspend fun calistir(aktivite: Activity, n: Niyet.SarkiCal): String {
        val q = n.gorunen.ifBlank { n.sorgu }
        if (n.uygulama == HedefUygulama.GOOGLE) {
            val web = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
            if (baslat(aktivite, web)) return "ACTION_WEB_SEARCH"
            if (baslat(aktivite, goruntule("https://www.google.com/search?q=" + Uri.encode(q), null))) return "tarayıcı (google.com)"
            return "başarısız: web araması açılamadı"
        }
        val u = hedef(n.uygulama)
        if (u == null) {
            if (baslat(aktivite, aramaIntenti(null, q))) return "MEDIA_PLAY_FROM_SEARCH (paketsiz)"
            if (baslat(aktivite, goruntule("https://www.youtube.com/results?search_query=" + Uri.encode(q), null))) {
                return "tarayıcı (YouTube arama)"
            }
            return "başarısız: müzik uygulaması yok"
        }
        return when (u.kod) {
            "youtube" -> youtube(aktivite, q, n.sadeceAra)
            else -> {
                if (n.sadeceAra) {
                    val uri = if (u.kod == "spotify") "spotify:search:" + Uri.encode(q) else "https://music.youtube.com/search?q=" + Uri.encode(q)
                    if (baslat(aktivite, goruntule(uri, u.paket))) "${u.ad} arama ($uri)" else "başarısız: ${u.ad} arama açılamadı"
                } else {
                    when {
                        oturumlaCal(u.paket, q) -> "${u.ad} oturum playFromSearch"
                        baslat(aktivite, aramaIntenti(u.paket, q)) -> "${u.ad} MEDIA_PLAY_FROM_SEARCH"
                        u.kod == "spotify" && baslat(aktivite, goruntule("spotify:search:" + Uri.encode(q), u.paket)) -> "Spotify arama (spotify:search)"
                        baslat(aktivite, goruntule("https://music.youtube.com/search?q=" + Uri.encode(q), u.paket)) -> "${u.ad} arama sayfası"
                        else -> "başarısız: ${u.ad} açılamadı"
                    }
                }
            }
        }
    }

    private suspend fun youtube(aktivite: Activity, q: String, sadeceAra: Boolean): String {
        val paket = YOUTUBE.paket
        val yuklu = yukluMu(paket)
        if (!sadeceAra) {
            val id = youtubeVideoBul(q)
            if (id != null) {
                if (yuklu && baslat(aktivite, goruntule("vnd.youtube:$id", paket))) return "YouTube API → video $id"
                if (baslat(aktivite, goruntule("https://www.youtube.com/watch?v=$id", null))) return "YouTube API → tarayıcı $id"
            }
            if (yuklu && ayarlar.youtubeAnahtari.isBlank() && baslat(aktivite, aramaIntenti(paket, q))) {
                return "YouTube MEDIA_PLAY_FROM_SEARCH"
            }
        }
        if (yuklu && baslat(aktivite, Intent(Intent.ACTION_SEARCH).setPackage(paket).putExtra("query", q))) {
            return "YouTube ACTION_SEARCH (arama sonuçları)"
        }
        val uri = "https://www.youtube.com/results?search_query=" + Uri.encode(q)
        if (baslat(aktivite, goruntule(uri, if (yuklu) paket else null))) return "YouTube arama sayfası"
        return "başarısız: YouTube açılamadı"
    }

    companion object {
        val SPOTIFY = MuzikUygulama("spotify", "Spotify", "com.spotify.music")
        val YTMUSIC = MuzikUygulama("ytmusic", "YouTube Music", "com.google.android.apps.youtube.music")
        val YOUTUBE = MuzikUygulama("youtube", "YouTube", "com.google.android.youtube")
        val HEPSI = listOf(SPOTIFY, YTMUSIC, YOUTUBE)

        /** "YouTube'da", "Spotify'da" gibi cevaplar için. */
        fun bulunmaHali(h: HedefUygulama?): String = when (h) {
            HedefUygulama.SPOTIFY -> "Spotify'da"
            HedefUygulama.YOUTUBE_MUSIC -> "YouTube Music'te"
            HedefUygulama.YOUTUBE -> "YouTube'da"
            HedefUygulama.GOOGLE -> "Google'da"
            null -> ""
        }

        /** Uygulamanın imza sertifikası SHA-1 parmak izi (AA:BB:...). */
        @Suppress("DEPRECATION")
        fun imzaSha1(ctx: Context): String? = try {
            val pm = ctx.packageManager
            val imzalar: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val b = imzalar?.firstOrNull()?.toByteArray()
            b?.let { MessageDigest.getInstance("SHA-1").digest(it).joinToString(":") { x -> "%02X".format(x) } }
        } catch (e: Exception) {
            null
        }
    }
}
