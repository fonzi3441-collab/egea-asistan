package com.egea.asistan.ses

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.egea.asistan.AsistanApp

/**
 * Dinlerken AUDIOFOCUS_GAIN_TRANSIENT alır (müzik durur), komut bitince bırakır (müzik devam eder).
 * "durdur" komutundan sonra müziğin kendiliğinden devam etmemesi için bırakmadan önce kalıcı odak
 * (AUDIOFOCUS_GAIN) alınır: müzik uygulaması AUDIOFOCUS_LOSS alır ve odak geri gelince çalmaz.
 */
class OdakYoneticisi(ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val dinleyici = AudioManager.OnAudioFocusChangeListener { degisim ->
        Log.d(AsistanApp.ETIKET, "Ses odağı değişti: $degisim")
    }
    private var istek: AudioFocusRequest? = null

    @Volatile
    var tutuluyor = false
        private set

    @Suppress("DEPRECATION")
    fun geciciAl() {
        if (tutuluyor) return
        tutuluyor = true
        try {
            val sonuc = if (Build.VERSION.SDK_INT >= 26) {
                val r = istekOlustur(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                istek = r
                am.requestAudioFocus(r)
            } else {
                am.requestAudioFocus(dinleyici, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            Log.i(AsistanApp.ETIKET, "Geçici ses odağı istendi, sonuç=$sonuc")
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Ses odağı alınamadı", e)
        }
    }

    @RequiresApi(26)
    private fun istekOlustur(tur: Int): AudioFocusRequest =
        AudioFocusRequest.Builder(tur)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(dinleyici)
            .build()

    /** Odağı bırakır. [muzikDursun] true ise müzik uygulaması kalıcı kayıp alır ve devam etmez. */
    @Suppress("DEPRECATION")
    fun birak(muzikDursun: Boolean = false) {
        if (!tutuluyor) return
        tutuluyor = false
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (muzikDursun) {
                    val kalici = istekOlustur(AudioManager.AUDIOFOCUS_GAIN)
                    am.requestAudioFocus(kalici)
                    am.abandonAudioFocusRequest(kalici)
                }
                istek?.let { am.abandonAudioFocusRequest(it) }
                istek = null
            } else {
                if (muzikDursun) am.requestAudioFocus(dinleyici, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                am.abandonAudioFocus(dinleyici)
            }
            Log.i(AsistanApp.ETIKET, "Ses odağı bırakıldı (müzik dursun=$muzikDursun)")
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Ses odağı bırakılamadı", e)
        }
    }
}
