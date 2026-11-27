package com.egea.asistan.medya

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Hafif logo yükleyici: ağdan arka planda indirir, hedef boyuta küçültür, bellekte (LRU) ve diskte
 * önbellekler. Ana iş parçacığını hiç bekletmez (akıcı kaydırma için).
 */
object GorselYukleyici {
    private val bellek = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val havuz = Executors.newFixedThreadPool(3)
    private val ana = Handler(Looper.getMainLooper())
    private val basarisiz = ConcurrentHashMap.newKeySet<String>()
    private var klasor: File? = null

    fun yukle(iv: ImageView, url: String?, boyutPx: Int, yerTutucu: Drawable) {
        iv.tag = url
        if (url.isNullOrBlank() || url in basarisiz) {
            iv.setImageDrawable(yerTutucu)
            return
        }
        val hazir = bellek.get(url)
        if (hazir != null) {
            iv.setImageBitmap(hazir)
            return
        }
        iv.setImageDrawable(yerTutucu)
        val ctx = iv.context.applicationContext
        havuz.execute {
            val bmp = try {
                diskten(ctx, url) ?: indir(ctx, url, boyutPx)
            } catch (e: Throwable) {
                null
            }
            if (bmp == null) {
                basarisiz.add(url)
                return@execute
            }
            bellek.put(url, bmp)
            ana.post { if (iv.tag == url) iv.setImageBitmap(bmp) }
        }
    }

    private fun dosya(ctx: Context, url: String): File {
        val k = klasor ?: File(ctx.cacheDir, "logolar").also { it.mkdirs(); klasor = it }
        val ozet = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(k, "$ozet.png")
    }

    private fun diskten(ctx: Context, url: String): Bitmap? {
        val f = dosya(ctx, url)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    private fun indir(ctx: Context, url: String, boyutPx: Int): Bitmap? {
        var adres = URL(url)
        var bayt: ByteArray? = null
        for (adim in 0 until 4) {
            val c = adres.openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) EgeaAsistan")
            val kod = c.responseCode
            if (kod in 300..399) {
                adres = URL(adres, c.getHeaderField("Location") ?: return null)
                continue
            }
            if (kod != 200) return null
            if (c.contentLengthLong > 3_000_000) return null
            bayt = c.inputStream.use { it.readBytes() }
            break
        }
        val b = bayt ?: return null
        val sinir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, sinir)
        if (sinir.outWidth <= 0 || sinir.outHeight <= 0) return null
        var ornekleme = 1
        while (sinir.outWidth / (ornekleme * 2) >= boyutPx && sinir.outHeight / (ornekleme * 2) >= boyutPx) ornekleme *= 2
        val ham = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = ornekleme }) ?: return null
        val olcek = boyutPx.toFloat() / maxOf(ham.width, ham.height)
        val son = if (olcek < 1f) {
            Bitmap.createScaledBitmap(ham, (ham.width * olcek).toInt().coerceAtLeast(1), (ham.height * olcek).toInt().coerceAtLeast(1), true)
        } else {
            ham
        }
        try {
            dosya(ctx, url).outputStream().use { son.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            // önbellek yazılamazsa sorun değil
        }
        return son
    }
}
