package com.egea.asistan.medya

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.egea.asistan.AsistanApp
import com.egea.asistan.BuildConfig
import com.egea.asistan.ui.AnaEkranActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Arka planda radyo çalan medya servisi (Media3). Bildirim, direksiyon medya tuşları ve
 * "sonraki/önceki/durdur" sesli komutları bu oturum üzerinden çalışır. Ses odağını ExoPlayer yönetir:
 * asistan dinlerken radyo durur, bitince kaldığı yerden devam eder.
 */
class RadyoServisi : MediaSessionService() {
    private var oturum: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("EgeaAsistan/${BuildConfig.VERSION_NAME} (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        val oynatici = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        oynatici.repeatMode = Player.REPEAT_MODE_ALL
        oynatici.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val ad = oynatici.currentMediaItem?.mediaMetadata?.title
                RadyoDurumu.hata = "$ad: ${error.errorCodeName}"
                Log.w(AsistanApp.ETIKET, "Radyo hatası ($ad): ${error.errorCodeName}", error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) RadyoDurumu.hata = null
            }
        })
        val ac = PendingIntent.getActivity(
            this, 1,
            Intent(this, AnaEkranActivity::class.java).putExtra(AnaEkranActivity.EXTRA_SAYFA, "radyo")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        oturum = MediaSession.Builder(this, oynatici)
            .setSessionActivity(ac)
            .setCallback(object : MediaSession.Callback {
                // Denetleyiciden gelen öğelerin adresini requestMetadata.mediaUri'den çöz.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val cozulmus = mediaItems.map { oge ->
                        val uri = oge.requestMetadata.mediaUri ?: oge.localConfiguration?.uri
                        if (uri == null) oge else oge.buildUpon().setUri(uri).setMimeType(Istasyon.mimeTuru(uri.toString())).build()
                    }.toMutableList()
                    return Futures.immediateFuture(cozulmus)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = oturum

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = oturum?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        oturum?.let {
            it.player.release()
            it.release()
        }
        oturum = null
        super.onDestroy()
    }
}

/** Radyo durumu (tanı ekranı ve arayüz için). */
object RadyoDurumu {
    @Volatile
    var hata: String? = null
}
