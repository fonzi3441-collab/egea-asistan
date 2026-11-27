package com.egea.asistan.medya

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Radyo servisine uygulama genelinde tek MediaController ile bağlanır.
 *
 * Media3 MediaController yalnız kendi iş parçacığında (burada her zaman ana iş parçacığı) kullanılabilir;
 * başka iş parçacığından çağrılırsa IllegalStateException fırlatır ve uygulama çöker. Bu yüzden:
 * denetleyici ana iş parçacığına bağlanır, [anlik] başka iş parçacığında null döner, [cal] ve [duraklat]
 * kendileri ana iş parçacığına geçer, arka plandan durum okumak isteyenler [ozet] kullanır.
 */
object RadyoKontrol {
    /** Arka plan iş parçacıklarından güvenle okunabilen son çalma durumu. */
    data class Ozet(val baslik: String?, val caliyor: Boolean)

    private var gelecek: ListenableFuture<MediaController>? = null
    private val ana = Handler(Looper.getMainLooper())

    @Volatile
    var ozet: Ozet = Ozet(null, false)
        private set

    private val ozetDinleyici = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            ozetGuncelle(player)
        }
    }

    private fun anaIplikMi(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun ozetGuncelle(p: Player) {
        val m = p.currentMediaItem?.mediaMetadata
        ozet = Ozet((m?.station ?: m?.title)?.toString(), p.isPlaying)
    }

    /** Yalnız ana iş parçacığında çağrılır. */
    private fun hazirla(ctx: Context): ListenableFuture<MediaController> {
        check(anaIplikMi()) { "RadyoKontrol.hazirla ana iş parçacığında çağrılmalı" }
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
        val yeni = MediaController.Builder(uc, token)
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()
        gelecek = yeni
        yeni.addListener({
            val k = try {
                yeni.get()
            } catch (e: Exception) {
                null
            }
            if (k != null) {
                k.addListener(ozetDinleyici)
                ozetGuncelle(k)
            }
        }, ContextCompat.getMainExecutor(uc))
        return yeni
    }

    /**
     * Bağlı denetleyiciyi verir (gerekirse bağlanır). Dönen nesne yalnız ana iş parçacığında
     * kullanılmalıdır.
     */
    suspend fun kontrolcu(ctx: Context): MediaController = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { devam ->
            val g = hazirla(ctx)
            g.addListener({
                try {
                    devam.resume(g.get())
                } catch (e: Exception) {
                    devam.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
    }

    /** Bağlıysa denetleyici (beklemeden). Ana iş parçacığı dışında her zaman null döner. */
    fun anlik(): MediaController? {
        if (!anaIplikMi()) return null
        val g = gelecek ?: return null
        if (!g.isDone) return null
        return try {
            g.get().takeIf { it.isConnected }
        } catch (e: Exception) {
            null
        }
    }

    /** İstasyon listesini kuyruğa koyar ve [indeks]teki istasyonu çalar (her iş parçacığından çağrılabilir). */
    suspend fun cal(ctx: Context, liste: List<Istasyon>, indeks: Int): Unit = withContext(Dispatchers.Main.immediate) {
        if (liste.isEmpty()) return@withContext
        val i = indeks.coerceIn(0, liste.lastIndex)
        val k = kontrolcu(ctx)
        k.setMediaItems(liste.map { it.mediaItem() }, i, C.TIME_UNSET)
        k.prepare()
        k.play()
        RadyoKatalogu.sonIstasyonKaydet(liste[i])
    }

    /** Radyoyu duraklatır (her iş parçacığından çağrılabilir). */
    fun duraklat() {
        if (!anaIplikMi()) {
            ana.post { duraklat() }
            return
        }
        anlik()?.pause()
    }
}
