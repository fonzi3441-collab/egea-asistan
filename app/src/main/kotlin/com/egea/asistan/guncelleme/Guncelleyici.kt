package com.egea.asistan.guncelleme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.egea.asistan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub "latest" release'ini kontrol eder, egea-asistan.apk'yı indirir ve kurulum ekranını açar.
 * Depo (OWNER/REPO) derleme sırasında git remote / GITHUB_REPOSITORY'den alınır.
 */
object Guncelleyici {
    val repo: String get() = BuildConfig.GUNCELLEME_REPO
    val apkLinki: String get() = "https://github.com/$repo/releases/latest/download/egea-asistan.apk"
    private val apiLinki: String get() = "https://api.github.com/repos/$repo/releases/latest"

    data class Bilgi(val surumKodu: Int?, val ad: String, val notlar: String)

    private fun baglan(url: URL): HttpURLConnection {
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "EgeaAsistan/${BuildConfig.VERSION_NAME}")
        return c
    }

    /** Yönlendirmeleri (github.com → objects/release-assets) elle izler. */
    private fun ac(baslangic: String, kabul: String? = null): HttpURLConnection {
        var url = URL(baslangic)
        repeat(8) {
            val c = baglan(url)
            if (kabul != null) c.setRequestProperty("Accept", kabul)
            val kod = c.responseCode
            if (kod in 300..399) {
                val yer = c.getHeaderField("Location") ?: throw IOException("Yönlendirme adresi yok (HTTP $kod)")
                c.disconnect()
                url = URL(url, yer)
            } else if (kod == 200) {
                return c
            } else {
                c.disconnect()
                throw IOException("HTTP $kod ($url)")
            }
        }
        throw IOException("Çok fazla yönlendirme")
    }

    suspend fun kontrol(): Bilgi = withContext(Dispatchers.IO) {
        val c = ac(apiLinki, "application/vnd.github+json")
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val notlar = json.optString("body")
        val kod = Regex("versionCode:\\s*(\\d+)").find(notlar)?.groupValues?.get(1)?.toIntOrNull()
        Bilgi(kod, json.optString("name"), notlar)
    }

    suspend fun indir(ctx: Context, ilerleme: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val klasor = File(ctx.cacheDir, "guncelleme")
        klasor.mkdirs()
        val hedef = File(klasor, "egea-asistan.apk")
        val gecici = File(klasor, "egea-asistan.apk.part")
        val c = ac(apkLinki)
        val toplam = c.contentLengthLong
        var okunan = 0L
        var sonYuzde = -1
        c.inputStream.use { giris ->
            gecici.outputStream().use { cikis ->
                val tampon = ByteArray(64 * 1024)
                while (true) {
                    val n = giris.read(tampon)
                    if (n < 0) break
                    cikis.write(tampon, 0, n)
                    okunan += n
                    if (toplam > 0) {
                        val yuzde = (okunan * 100 / toplam).toInt()
                        if (yuzde != sonYuzde) {
                            sonYuzde = yuzde
                            withContext(Dispatchers.Main) { ilerleme(yuzde) }
                        }
                    }
                }
            }
        }
        if (okunan < 10_000) throw IOException("İndirilen dosya çok küçük ($okunan bayt)")
        hedef.delete()
        if (!gecici.renameTo(hedef)) throw IOException("Dosya kaydedilemedi")
        hedef
    }

    /** Android 8+ için "bilinmeyen uygulamaları yükle" izni var mı? */
    fun kurulumIzniVar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun kurulumIzniEkrani(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun kur(ctx: Context, dosya: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".dosya", dosya)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
