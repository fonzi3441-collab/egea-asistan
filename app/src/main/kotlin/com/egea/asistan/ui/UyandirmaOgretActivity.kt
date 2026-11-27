package com.egea.asistan.ui

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.egea.asistan.AsistanApp
import com.egea.asistan.Izinler
import com.egea.asistan.core.UyandirmaDedektoru
import com.egea.asistan.servis.YuzenButonServisi
import com.egea.asistan.uyandirma.UyandirmaDeposu
import com.egea.asistan.uyandirma.UyandirmaKontrol
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * "Hey Egea" öğretme ekranı: kullanıcı 4 kez söyler (2 × "Hey Egea", 2 × "Hey Ege"); her kayıttan MFCC
 * şablonu çıkarılır. Duyarlılık ayarlanabilir ve "Dene" ile canlı test edilir.
 */
class UyandirmaOgretActivity : ComponentActivity() {
    private val ifadeler = listOf("Hey Egea", "Hey Egea", "Hey Ege", "Hey Ege")
    private val sablonlar = arrayOfNulls<List<FloatArray>>(4)
    private val slotYazilari = arrayOfNulls<TextView>(4)
    private lateinit var durum: TextView
    private lateinit var seviye: TextView
    private lateinit var duyarlilikYazi: TextView
    private val ana = Handler(Looper.getMainLooper())

    @Volatile
    private var kayitta = false

    @Volatile
    private var testte = false

    private val izin = registerForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
        if (!verildi) durum.text = "Mikrofon izni gerekli."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enYuksekYenileme()
        UyandirmaKontrol.duraklat()
        val mevcut = UyandirmaDeposu.yukle(this)
        setContentView(sayfa {
            baslik("“Hey Egea”yı öğret")
            yazi(
                "Uygulama, uyandırma kelimesini internetsiz ve sadece bu cihazda tanır. Arabada, normal sesinle 4 kez söyle: " +
                    "2 kez “Hey Egea”, 2 kez “Hey Ege”. Kayıt sırasında müzik kapalı olsun. Sonra “Hey Egea” deyince " +
                    "asistan “Dinliyorum” der ve komutunu dinler.",
                18f, Renk.SOLUK
            )
            if (mevcut != null) yazi("Kayıtlı ${mevcut.sablonlar.size} örnek var. Yeniden kaydedersen eskiler değişir.", 17f, Renk.YESIL)
            for (i in 0 until 4) {
                satir {
                    buton("${i + 1}. kaydı al", Renk.VURGU) { kaydet(i) }
                    slotYazilari[i] = yazi("“${ifadeler[i]}” — bekliyor", 19f)
                }
            }
            durum = yazi("Bir kayda basın, “ŞİMDİ SÖYLE” yazınca söyleyin.", 19f, Renk.TURUNCU)
            seviye = yazi("Ses seviyesi: -", 16f, Renk.SOLUK)
            bolum("Duyarlılık")
            duyarlilikYazi = yazi(duyarlilikMetni(AsistanApp.ornek.ayarlar.uyandirmaCarpan), 17f)
            val cubuk = SeekBar(this@UyandirmaOgretActivity)
            cubuk.max = 100
            cubuk.progress = (((AsistanApp.ornek.ayarlar.uyandirmaCarpan - 0.9f) / 0.8f) * 100).toInt().coerceIn(0, 100)
            cubuk.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val c = 0.9f + progress / 100f * 0.8f
                    AsistanApp.ornek.ayarlar.uyandirmaCarpan = c
                    duyarlilikYazi.text = duyarlilikMetni(c)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            kok.addView(cubuk)
            satir {
                buton("Kaydet ve aç", Renk.YESIL) { kaydetVeAc() }
                buton("Dene (15 sn)", Renk.VURGU) { dene() }
                buton("Sil", Renk.KIRMIZI) {
                    UyandirmaDeposu.sil(this@UyandirmaOgretActivity)
                    AsistanApp.ornek.ayarlar.uyandirma = false
                    YuzenButonServisi.uygunsaBaslat(this@UyandirmaOgretActivity, yenidenYukle = true)
                    durum.text = "Örnekler silindi, Hey Egea kapatıldı."
                }
            }
            yazi(
                "İpucu: Sık yanlış tetikleniyorsa duyarlılığı sola, zor algılıyorsa sağa çek. Müzik yüksekken algılama zorlaşır; " +
                    "o zaman direksiyon tuşunu ya da ekrandaki mikrofonu kullanın.",
                15f, Renk.SOLUK
            )
        })
        if (!Izinler.mikrofon(this)) izin.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onDestroy() {
        kayitta = false
        testte = false
        UyandirmaKontrol.devamEt(300)
        super.onDestroy()
    }

    private fun duyarlilikMetni(c: Float): String = when {
        c < 1.1f -> "Seçici (az yanlış alarm) • ${"%.2f".format(c)}"
        c < 1.4f -> "Dengeli • ${"%.2f".format(c)}"
        else -> "Kolay tetiklenir • ${"%.2f".format(c)}"
    }

    @SuppressLint("MissingPermission")
    private fun kayitAc(): AudioRecord? {
        if (!Izinler.mikrofon(this)) return null
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

    private fun seviyeDb(t: ShortArray, n: Int): Float {
        var top = 0.0
        for (i in 0 until n) top += t[i].toDouble() * t[i]
        return (20 * log10(sqrt(top / maxOf(1, n)) / 32768.0 + 1e-9)).toFloat()
    }

    private fun kaydet(i: Int) {
        if (kayitta || testte) return
        val r = kayitAc()
        if (r == null) {
            durum.text = "Mikrofon açılamadı. İzin verildi mi? Başka uygulama kullanıyor olabilir."
            return
        }
        kayitta = true
        durum.text = "Hazır ol…"
        Thread {
            val toplam = ShortArray(16000 * 3)
            var dolu = 0
            val tampon = ShortArray(1600)
            var soyle = false
            try {
                r.startRecording()
                while (kayitta && dolu < toplam.size) {
                    val n = r.read(tampon, 0, minOf(tampon.size, toplam.size - dolu))
                    if (n <= 0) break
                    System.arraycopy(tampon, 0, toplam, dolu, n)
                    dolu += n
                    // İlk 0,4 sn sessiz kalsın ki gürültü seviyesi ölçülsün.
                    if (!soyle && dolu >= 6400) {
                        soyle = true
                        ana.post { durum.text = "ŞİMDİ SÖYLE: “${ifadeler[i]}”" }
                    }
                    val db = seviyeDb(tampon, n)
                    ana.post { seviye.text = "Ses seviyesi: ${"%.0f".format(db)} dB" }
                }
            } catch (e: Exception) {
                // aşağıda değerlendirilir
            } finally {
                try {
                    r.stop()
                } catch (e: Exception) {
                    // yok say
                }
                r.release()
            }
            val sablon = UyandirmaDedektoru.sablonCikar(toplam.copyOf(dolu))
            ana.post {
                kayitta = false
                if (sablon == null) {
                    durum.text = "Ses algılanamadı. Biraz daha yüksek sesle tekrar deneyin."
                    slotYazilari[i]?.text = "“${ifadeler[i]}” — tekrar"
                } else {
                    sablonlar[i] = sablon
                    slotYazilari[i]?.text = "“${ifadeler[i]}” — ✓ ${"%.2f".format(sablon.size / 100f)} sn"
                    val sayi = sablonlar.count { it != null }
                    durum.text = if (sayi < 4) "Güzel! Sıradaki kaydı alın ($sayi/4)." else "Tamam! “Kaydet ve aç”a basın."
                }
            }
        }.start()
    }

    private fun kaydetVeAc() {
        val l = sablonlar.filterNotNull()
        if (l.size < 2) {
            Toast.makeText(this, "En az 2 kayıt gerekli (4 önerilir)", Toast.LENGTH_LONG).show()
            return
        }
        val v = UyandirmaDeposu.kaydet(this, l)
        AsistanApp.ornek.ayarlar.uyandirma = true
        YuzenButonServisi.uygunsaBaslat(this, yenidenYukle = true)
        durum.text = "Kaydedildi (${l.size} örnek, taban ${"%.1f".format(v.tabanMesafe)}). Artık “Hey Egea” diyebilirsin."
    }

    private fun dene() {
        if (kayitta || testte) return
        val kayitli = sablonlar.filterNotNull().ifEmpty { UyandirmaDeposu.yukle(this)?.sablonlar ?: emptyList() }
        if (kayitli.size < 2) {
            durum.text = "Önce en az 2 kayıt alın."
            return
        }
        val esik = maxOf(UyandirmaDedektoru.tabanMesafe(kayitli) * AsistanApp.ornek.ayarlar.uyandirmaCarpan, 1f)
        val r = kayitAc() ?: run {
            durum.text = "Mikrofon açılamadı."
            return
        }
        testte = true
        durum.text = "Deneme: “Hey Egea” deyin… (eşik ${"%.1f".format(esik)})"
        Thread {
            val dedektor = UyandirmaDedektoru(kayitli, esik)
            val tampon = ShortArray(1600)
            val bitis = System.currentTimeMillis() + 15_000
            var algilama = 0
            var sonMesafe = Float.NaN
            try {
                r.startRecording()
                while (testte && System.currentTimeMillis() < bitis) {
                    val n = r.read(tampon, 0, tampon.size)
                    if (n <= 0) break
                    val bulundu = dedektor.isle(tampon, n)
                    if (dedektor.sonMesafe != sonMesafe) {
                        sonMesafe = dedektor.sonMesafe
                        val m = sonMesafe
                        ana.post { seviye.text = "Son söz mesafesi: ${"%.1f".format(m)} (eşik ${"%.1f".format(esik)})" }
                    }
                    if (bulundu) {
                        algilama++
                        val a = algilama
                        ana.post { durum.text = "ALGILANDI ✓ ($a)" }
                    }
                }
            } catch (e: Exception) {
                // yok say
            } finally {
                try {
                    r.stop()
                } catch (e: Exception) {
                    // yok say
                }
                r.release()
            }
            val a = algilama
            ana.post {
                testte = false
                durum.text = "Deneme bitti: $a kez algılandı. Gerekirse duyarlılığı ayarlayın."
            }
        }.start()
    }
}
