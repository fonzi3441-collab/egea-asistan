package com.egea.asistan.uyandirma

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.Izinler
import com.egea.asistan.core.UyandirmaDedektoru

/**
 * Arka planda mikrofonu dinler (16 kHz, internetsiz) ve öğretilen "Hey Egea" örneğine benzeyen bir söz
 * duyunca [tetiklendi]'yi çağırır. Telefon görüşmesi sırasında ve dinleme ekranı açıkken mikrofonu bırakır.
 */
class UyandirmaMotoru(private val ctx: Context, private val tetiklendi: () -> Unit) {
    @Volatile
    private var calis = false

    @Volatile
    private var duraklatildi = false

    /** Mikrofon şu an bu motorda açık mı (dinleme ekranı, konuşma tanımayı bunun bırakılmasını bekler). */
    @Volatile
    var mikrofonAcik = false
        private set

    private var iplik: Thread? = null
    private val ana = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun baslat() {
        if (calis) return
        calis = true
        duraklatildi = false
        iplik = Thread({ dongu() }, "egea-uyandirma").also { it.start() }
    }

    fun durdur() {
        calis = false
        iplik = null
    }

    fun duraklat() {
        duraklatildi = true
    }

    fun devamEt() {
        duraklatildi = false
    }

    private fun durum(s: String) {
        UyandirmaKontrol.durum = s
    }

    private fun aramada(): Boolean {
        val m = am.mode
        return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE
    }

    private fun uyu(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            // çık
        }
    }

    @SuppressLint("MissingPermission")
    private fun kayitAc(): AudioRecord? {
        if (!Izinler.mikrofon(ctx)) return null
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val kaynak = when (AsistanApp.ornek.ayarlar.uyandirmaKaynak) {
            "iletisim" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "mikrofon" -> MediaRecorder.AudioSource.MIC
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val r = try {
            AudioRecord(kaynak, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 32000))
        } catch (e: Exception) {
            return null
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return null
        }
        return r
    }

    private fun dongu() {
        val veri = UyandirmaDeposu.yukle(ctx)
        if (veri == null) {
            durum("öğretilmemiş")
            calis = false
            return
        }
        val esik = UyandirmaDeposu.esik(veri, AsistanApp.ornek.ayarlar.uyandirmaCarpan)
        UyandirmaKontrol.esik = esik
        val dedektor = UyandirmaDedektoru(veri.sablonlar, esik)
        val tampon = ShortArray(1600)
        while (calis) {
            if (duraklatildi) {
                durum("duraklatıldı")
                uyu(250)
                continue
            }
            if (aramada()) {
                durum("telefon görüşmesi — bekliyor")
                uyu(1000)
                continue
            }
            val kayit = kayitAc()
            if (kayit == null) {
                durum("mikrofon açılamadı")
                uyu(3000)
                continue
            }
            try {
                kayit.startRecording()
                if (kayit.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    durum("kayıt başlatılamadı (mikrofon başka uygulamada olabilir)")
                    uyu(3000)
                } else {
                    mikrofonAcik = true
                    durum("dinliyor")
                    var sifir = 0
                    var sayac = 0
                    while (calis && !duraklatildi) {
                        val n = kayit.read(tampon, 0, tampon.size)
                        if (n < 0) {
                            durum("okuma hatası ($n)")
                            uyu(1000)
                            break
                        }
                        if (n == 0) continue
                        var hepsiSifir = true
                        for (i in 0 until n) {
                            if (tampon[i].toInt() != 0) {
                                hepsiSifir = false
                                break
                            }
                        }
                        if (hepsiSifir) {
                            sifir++
                            if (sifir == 30) durum("mikrofon sessiz (arka plan kısıtı olabilir; Egea'yı bir kez açın)")
                        } else {
                            if (sifir >= 30) durum("dinliyor")
                            sifir = 0
                        }
                        val bulundu = dedektor.isle(tampon, n)
                        UyandirmaKontrol.sonMesafe = dedektor.sonMesafe
                        UyandirmaKontrol.seviyeDb = dedektor.sonSeviyeDb
                        if (bulundu) {
                            UyandirmaKontrol.sonTetik = System.currentTimeMillis()
                            UyandirmaKontrol.tetikSayisi++
                            Log.i(AsistanApp.ETIKET, "Hey Egea algılandı (mesafe ${dedektor.sonMesafe}, eşik $esik)")
                            duraklatildi = true
                            ana.post(tetiklendi)
                            break
                        }
                        if (++sayac % 10 == 0 && aramada()) break
                    }
                }
            } catch (e: Exception) {
                durum("hata: ${e.message}")
                uyu(2000)
            } finally {
                try {
                    kayit.stop()
                } catch (e: Exception) {
                    // yok say
                }
                kayit.release()
                mikrofonAcik = false
            }
        }
        mikrofonAcik = false
        durum("kapalı")
    }
}
