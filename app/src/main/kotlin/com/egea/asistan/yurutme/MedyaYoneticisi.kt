package com.egea.asistan.yurutme

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.egea.asistan.AsistanApp
import com.egea.asistan.Izinler
import com.egea.asistan.core.MedyaEylemi
import com.egea.asistan.core.Miktar
import com.egea.asistan.core.Niyet
import com.egea.asistan.servis.MedyaDinleyiciServisi
import kotlin.math.roundToInt

/**
 * Müzik kontrolü: bildirim erişimi varsa MediaSessionManager.getActiveSessions → TransportControls,
 * yoksa AudioManager.dispatchMediaKeyEvent. Ses seviyesi: AudioManager, STREAM_MUSIC.
 */
class MedyaYoneticisi(private val ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun oturumlar(): List<MediaController> {
        if (!Izinler.bildirimErisimi(ctx)) return emptyList()
        return try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(ComponentName(ctx, MedyaDinleyiciServisi::class.java))
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Medya oturumları alınamadı", e)
            emptyList()
        }
    }

    /** Çalan oturum, yoksa en son aktif olan. [paket] verilirse o uygulamanın oturumu. */
    fun aktifKontrolcu(paket: String? = null): MediaController? {
        val l = oturumlar().filter { paket == null || it.packageName == paket }
        return l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
    }

    /** Sonraki/önceki/durdur/devam. Kullanılan yöntemi döndürür. */
    fun eylem(e: MedyaEylemi): String {
        val c = aktifKontrolcu()
        if (c != null) {
            try {
                val tc = c.transportControls
                when (e) {
                    MedyaEylemi.SONRAKI -> tc.skipToNext()
                    MedyaEylemi.ONCEKI -> tc.skipToPrevious()
                    MedyaEylemi.DURDUR -> tc.pause()
                    MedyaEylemi.DEVAM -> tc.play()
                    else -> Unit
                }
                return "MediaSession(${c.packageName})"
            } catch (ex: Exception) {
                Log.w(AsistanApp.ETIKET, "TransportControls hatası, medya tuşuna geçiliyor", ex)
            }
        }
        val kod = when (e) {
            MedyaEylemi.SONRAKI -> KeyEvent.KEYCODE_MEDIA_NEXT
            MedyaEylemi.ONCEKI -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MedyaEylemi.DURDUR -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MedyaEylemi.DEVAM -> KeyEvent.KEYCODE_MEDIA_PLAY
            else -> return "yok"
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, kod))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, kod))
        val not = if (c == null && !Izinler.bildirimErisimi(ctx)) " [bildirim erişimi yok]" else ""
        return "MediaKey(${KeyEvent.keyCodeToString(kod)})$not"
    }

    data class SesSonucu(val seviye: Int, val maks: Int, val konusma: String)

    /** Ses seviyesini hesaplar ve uygular (SES_KAPAT hariç: o konuşmadan sonra [sesiKapat] ile). */
    fun sesAyarla(n: Niyet.Medya): SesSonucu {
        val maks = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val simdi = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val temelAdim = maxOf(1, (maks + 7) / 8)
        val adim = when (n.miktar) {
            Miktar.AZ -> maxOf(1, temelAdim / 2)
            Miktar.COK -> temelAdim * 2
            Miktar.NORMAL -> temelAdim
        }
        val d = n.deger ?: 0
        val yeni = when (n.eylem) {
            MedyaEylemi.SES_AC -> simdi + adim
            MedyaEylemi.SES_KIS -> simdi - adim
            MedyaEylemi.SES_MAKS -> maks
            MedyaEylemi.SES_KAPAT -> 0
            MedyaEylemi.SES_AYARLA -> when {
                n.yuzde -> (maks * d / 100.0).roundToInt()
                d <= maks -> d
                d <= 100 -> (maks * d / 100.0).roundToInt()
                else -> maks
            }
            else -> simdi
        }.coerceIn(0, maks)
        if (n.eylem == MedyaEylemi.SES_KAPAT) return SesSonucu(0, maks, "Ses kapatılıyor")
        try {
            if (Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC) && yeni > 0) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
            am.setStreamVolume(AudioManager.STREAM_MUSIC, yeni, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Ses seviyesi ayarlanamadı", e)
        }
        val konusma = when {
            n.eylem == MedyaEylemi.SES_MAKS -> "Ses en yüksek"
            n.eylem == MedyaEylemi.SES_AYARLA && n.yuzde -> "Ses yüzde $d"
            else -> "Ses $yeni"
        }
        return SesSonucu(yeni, maks, konusma)
    }

    fun sesiKapat(): String = try {
        if (Build.VERSION.SDK_INT >= 23) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        } else {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        }
        "AudioManager (sessiz)"
    } catch (e: Exception) {
        "hata: ${e.message}"
    }
}
