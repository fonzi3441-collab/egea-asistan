package com.egea.asistan.ses

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.egea.asistan.AsistanApp
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Türkçe sesli cevap (TextToSpeech). Uygulama seviyesinde tek örnek; aktivite kapansa da konuşma sürer.
 * Konuşma bitince (ya da tahmini süre dolunca) geri çağırma ana iş parçacığında çalışır.
 */
class Konusmaci(private val ctx: Context) : TextToSpeech.OnInitListener {
    enum class Durum { BASLATILIYOR, HAZIR, TURKCE_YOK, HATA }

    @Volatile
    var durum = Durum.BASLATILIYOR
        private set

    /** setLanguage(tr-TR) sonucu (TextToSpeech.LANG_*); başlatılmadıysa null. */
    var dilSonucu: Int? = null
        private set

    var motor: String? = null
        private set

    private var tts: TextToSpeech? = null
    private val ana = Handler(Looper.getMainLooper())
    private val bekleyenler = ArrayList<Pair<String, (() -> Unit)?>>()
    private val geriCagirmalar = ConcurrentHashMap<String, () -> Unit>()
    private var sayac = 0

    fun baslat() {
        if (tts != null) return
        durum = Durum.BASLATILIYOR
        tts = try {
            TextToSpeech(ctx.applicationContext, this)
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "TTS oluşturulamadı", e)
            durum = Durum.HATA
            null
        }
        // Motor hiç cevap vermezse bekleyen konuşmalar takılı kalmasın.
        ana.postDelayed({
            if (durum == Durum.BASLATILIYOR) {
                Log.w(AsistanApp.ETIKET, "TTS 8 saniyede başlamadı")
                durum = Durum.HATA
                bekleyenleriBosalt()
            }
        }, 8000L)
    }

    override fun onInit(status: Int) {
        ana.post {
            val t = tts
            if (status != TextToSpeech.SUCCESS || t == null) {
                durum = Durum.HATA
                Log.w(AsistanApp.ETIKET, "TTS başlatılamadı: $status")
            } else {
                motor = try {
                    t.defaultEngine
                } catch (e: Exception) {
                    null
                }
                val sonuc = try {
                    t.setLanguage(TURKCE)
                } catch (e: Exception) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                dilSonucu = sonuc
                durum = if (sonuc == TextToSpeech.LANG_MISSING_DATA || sonuc == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Durum.TURKCE_YOK
                } else {
                    Durum.HAZIR
                }
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = bitti(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = bitti(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = bitti(utteranceId)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = bitti(utteranceId)
                })
                Log.i(AsistanApp.ETIKET, "TTS hazır: motor=$motor, tr-TR sonucu=$sonuc")
            }
            bekleyenleriBosalt()
        }
    }

    private fun bekleyenleriBosalt() {
        val liste = ArrayList(bekleyenler)
        bekleyenler.clear()
        for ((metin, cb) in liste) konus(metin, cb)
    }

    /** Metni seslendirir; önceki konuşmayı keser. [bitince] konuşma bitince (ana iş parçacığında) çağrılır. */
    fun konus(metin: String, bitince: (() -> Unit)? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ana.post { konus(metin, bitince) }
            return
        }
        if (durum == Durum.BASLATILIYOR) {
            bekleyenler += metin to bitince
            return
        }
        val t = tts
        if (t == null || durum == Durum.HATA || metin.isBlank()) {
            bitince?.let { ana.post(it) }
            return
        }
        val id = "egea-" + (++sayac)
        if (bitince != null) {
            geriCagirmalar[id] = bitince
            ana.postDelayed({ bitti(id) }, tahminiSure(metin))
        }
        val sonuc = try {
            t.speak(metin, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        } catch (e: Exception) {
            TextToSpeech.ERROR
        }
        if (sonuc != TextToSpeech.SUCCESS) {
            Log.w(AsistanApp.ETIKET, "TTS speak başarısız: $sonuc")
            bitti(id)
        }
    }

    private fun bitti(id: String?) {
        if (id == null) return
        val cb = geriCagirmalar.remove(id) ?: return
        ana.post(cb)
    }

    private fun tahminiSure(metin: String): Long = (2500L + metin.length * 110L).coerceAtMost(20000L)

    fun sustur() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "TTS durdurulamadı", e)
        }
    }

    /** isLanguageAvailable(tr-TR) sonucu; motor yoksa null. */
    fun turkceDurumu(): Int? = try {
        tts?.isLanguageAvailable(TURKCE)
    } catch (e: Exception) {
        null
    }

    fun motorlar(): List<String> = try {
        tts?.engines?.map { "${it.label} (${it.name})" } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        val TURKCE: Locale = Locale.forLanguageTag("tr-TR")

        fun dilSonucuAdi(sonuc: Int?): String = when (sonuc) {
            null -> "bilinmiyor"
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "VAR (tr-TR, varyant)"
            TextToSpeech.LANG_COUNTRY_AVAILABLE -> "VAR (tr-TR)"
            TextToSpeech.LANG_AVAILABLE -> "VAR (tr)"
            TextToSpeech.LANG_MISSING_DATA -> "EKSİK VERİ (Türkçe ses indirilmemiş)"
            TextToSpeech.LANG_NOT_SUPPORTED -> "DESTEKLENMİYOR"
            else -> "kod $sonuc"
        }
    }
}
