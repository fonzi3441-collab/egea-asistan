package com.egea.asistan.medya

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Radyo servisine uygulama genelinde tek MediaController ile bağlanır (ana iş parçacığında kullanılır). */
object RadyoKontrol {
    private var gelecek: ListenableFuture<MediaController>? = null

    fun hazirla(ctx: Context): ListenableFuture<MediaController> {
        val mevcut = gelecek
        if (mevcut != null) {
            if (!mevcut.isDone) return mevcut
            val k = try {
                mevcut.get()
            } catch (e: Exception) {
                null
            }
            if (k != null && k.isConnected) return mevcut
        }
        val uc = ctx.applicationContext
        val token = SessionToken(uc, ComponentName(uc, RadyoServisi::class.java))
        return MediaController.Builder(uc, token).buildAsync().also { gelecek = it }
    }

    suspend fun kontrolcu(ctx: Context): MediaController = suspendCancellableCoroutine { devam ->
        val g = hazirla(ctx)
        g.addListener({
            try {
                devam.resume(g.get())
            } catch (e: Exception) {
                devam.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** Bağlıysa denetleyici (beklemeden). */
    fun anlik(): MediaController? {
        val g = gelecek ?: return null
        if (!g.isDone) return null
        return try {
            g.get().takeIf { it.isConnected }
        } catch (e: Exception) {
            null
        }
    }

    /** İstasyon listesini kuyruğa koyar ve [indeks]teki istasyonu çalar. */
    suspend fun cal(ctx: Context, liste: List<Istasyon>, indeks: Int) {
        val k = kontrolcu(ctx)
        k.setMediaItems(liste.map { it.mediaItem() }, indeks.coerceIn(0, liste.lastIndex), C.TIME_UNSET)
        k.prepare()
        k.play()
        RadyoKatalogu.sonIstasyonKaydet(liste[indeks.coerceIn(0, liste.lastIndex)])
    }

    fun duraklat() {
        anlik()?.pause()
    }

    fun caliyorMu(): Boolean = anlik()?.isPlaying == true
}
