package com.egea.asistan.ui

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.egea.asistan.AsistanApp
import com.egea.asistan.BuildConfig
import com.egea.asistan.Izinler
import com.egea.asistan.R
import com.egea.asistan.core.Cevaplar
import com.egea.asistan.core.KomutCozucu
import com.egea.asistan.core.Niyet
import com.egea.asistan.core.Secim
import com.egea.asistan.core.SecimCozucu
import com.egea.asistan.uyandirma.UyandirmaKontrol
import com.egea.asistan.yurutme.Plan
import com.egea.asistan.yurutme.Rapor
import com.egea.asistan.yurutme.UygulamaYoneticisi
import com.egea.asistan.yurutme.Yurutucu
import com.egea.asistan.yurutme.Zamanlama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Yarı saydam dinleme ekranı. Mikrofon butonu, direksiyon tuşu (VOICE_COMMAND / ASSIST /
 * VOICE_SEARCH_HANDS_FREE) ya da başlatıcı ikonu bunu açar. Tanıma burada yapılır; iş bitince
 * finish() ile önceki uygulamaya (harita/müzik) dönülür.
 */
class ListenActivity : ComponentActivity() {
    private val app get() = AsistanApp.ornek

    private var tanima: SpeechRecognizer? = null
    private var tekrarHakki = 1
    private var hizliTekrarYapildi = false
    private var bekleyenSoru: Plan.Soru? = null
    private var bekleyenSoruOzeti = ""
    private var kisiBekleniyor = false
    private var bitiyor = false
    private var yedekAcik = false
    private var izinIsteniyor = false
    private var yedekKullan = false
    private var baslamaZamani = 0L
    private var sonMetin = ""
    private var uygulamaEtiketleri: List<String> = emptyList()

    private lateinit var durumYazi: TextView
    private lateinit var metinYazi: TextView
    private lateinit var seviye: View

    private val yedekBaslatici = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { sonuc ->
        yedekAcik = false
        val liste = sonuc.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (sonuc.resultCode == RESULT_OK && !liste.isNullOrEmpty()) {
            sonuclariIsle(liste)
        } else if (sonuc.resultCode == RESULT_OK) {
            anlasilmadi()
        } else {
            iptal(sessiz = true)
        }
    }

    private val mikrofonIzni = registerForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
        izinIsteniyor = false
        if (verildi) {
            dinle()
        } else {
            logla("-", "-", "mikrofon izni yok")
            bitir("Mikrofon izni gerekli. Ayarlardan izin verin.", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hey Egea motoru mikrofonu hemen bıraksın (konuşma tanıma kullanacak).
        UyandirmaKontrol.duraklat()
        if (BuildConfig.DEBUG && intent.getBooleanExtra(TEST_KURULUM, false)) app.ayarlar.kurulumTamam = true
        if (!app.ayarlar.kurulumTamam) {
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(arayuz())
        lifecycleScope.launch(Dispatchers.Default) {
            uygulamaEtiketleri = UygulamaYoneticisi.etiketler(applicationContext)
        }
        basla()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (bitiyor) return
        // Dinlerken mikrofon butonuna/tuşa tekrar basılırsa baştan dinle.
        tanimayiKapat()
        tekrarHakki = 1
        bekleyenSoru = null
        kisiBekleniyor = false
        app.konusmaci.sustur()
        val test = testMetni(intent)
        if (test != null) {
            sonuclariIsle(listOf(test))
            return
        }
        dinle()
    }

    override fun onStop() {
        super.onStop()
        // Başka bir ekran öne geldiyse (ana ekran tuşu vb.) dinlemeyi bırak.
        if (!isFinishing && !bitiyor && !yedekAcik && !izinIsteniyor && !isChangingConfigurations) {
            iptal(sessiz = true)
        }
    }

    override fun onDestroy() {
        tanimayiKapat()
        if (!bitiyor) app.odak.birak(false)
        UyandirmaKontrol.devamEt()
        super.onDestroy()
    }

    private fun arayuz(): View {
        val kok = FrameLayout(this)
        kok.setOnClickListener { iptal(sessiz = false) }

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = yuvarlak(Renk.SEFFAF_PANEL, dp(18).toFloat())
        panel.setPadding(dp(28), dp(18), dp(28), dp(22))
        panel.isClickable = true

        val ust = LinearLayout(this)
        ust.orientation = LinearLayout.HORIZONTAL
        ust.gravity = Gravity.CENTER_VERTICAL
        val ikon = ImageView(this)
        ikon.setImageResource(R.drawable.ic_mic)
        ikon.background = yuvarlak(Renk.VURGU, dp(28).toFloat())
        ikon.setPadding(dp(8), dp(8), dp(8), dp(8))
        ust.addView(ikon, LinearLayout.LayoutParams(dp(56), dp(56)))
        durumYazi = TextView(this).apply {
            text = "Dinliyorum…"
            textSize = 26f
            setTextColor(Renk.YAZI)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), 0, dp(12), 0)
        }
        ust.addView(durumYazi, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val iptalButon = Button(this).apply {
            text = "İptal"
            textSize = 20f
            isAllCaps = false
            setTextColor(Renk.YAZI)
            background = yuvarlak(Renk.PANEL2, dp(10).toFloat())
            stateListAnimator = null
            setPadding(dp(26), dp(6), dp(26), dp(6))
            setOnClickListener { iptal(sessiz = false) }
        }
        ust.addView(iptalButon, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)))
        panel.addView(ust)

        metinYazi = TextView(this).apply {
            text = ""
            textSize = 30f
            setTextColor(Renk.YAZI)
            setPadding(0, dp(14), 0, dp(10))
            minHeight = dp(52)
        }
        panel.addView(metinYazi)

        seviye = View(this)
        seviye.background = yuvarlak(Renk.VURGU, dp(3).toFloat())
        val seviyeLp = LinearLayout.LayoutParams(dp(40), dp(6))
        panel.addView(seviye, seviyeLp)

        val panelLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        panelLp.setMargins(dp(24), 0, dp(24), dp(20))
        kok.addView(panel, panelLp)
        return kok
    }

    private fun durum(metin: String) {
        if (::durumYazi.isInitialized) durumYazi.text = metin
    }

    private fun metin(metin: String) {
        if (::metinYazi.isInitialized) metinYazi.text = metin
    }

    private fun seviyeGoster(rms: Float) {
        if (!::seviye.isInitialized) return
        val oran = ((rms + 2f) / 12f).coerceIn(0.05f, 1f)
        val genislik = (dp(40) + (resources.displayMetrics.widthPixels - dp(160)) * oran).toInt()
        val lp = seviye.layoutParams
        lp.width = genislik
        seviye.layoutParams = lp
    }

    // ---------------------------------------------------------------- dinleme

    private fun basla() {
        app.konusmaci.sustur()
        app.odak.geciciAl()
        val test = testMetni(intent)
        if (test != null) {
            sonuclariIsle(listOf(test))
            return
        }
        if (!Izinler.mikrofon(this)) {
            mikrofonIzniIste()
            return
        }
        if (intent.getBooleanExtra(EXTRA_UYANDIRMA, false) || app.ayarlar.dinliyorumDe) {
            // "Hey Egea" sonrası: önce "Dinliyorum" de, sonra komutu dinle.
            durum("Dinliyorum…")
            app.konusmaci.konus("Dinliyorum") { if (!bitiyor && !isFinishing) dinle() }
        } else {
            dinle()
        }
    }

    private fun mikrofonIzniIste() {
        izinIsteniyor = true
        mikrofonIzni.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun tanimaIntenti(yedek: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, !yedek)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        if (yedek) putExtra(RecognizerIntent.EXTRA_PROMPT, if (kisiBekleniyor) Cevaplar.KIMI_ARAYAYIM else "Dinliyorum…")
    }

    private fun tanimayiKapat() {
        try {
            tanima?.cancel()
            tanima?.destroy()
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "SpeechRecognizer kapatılamadı", e)
        }
        tanima = null
    }

    private fun dinle() {
        if (bitiyor) return
        durum(if (kisiBekleniyor) Cevaplar.KIMI_ARAYAYIM else "Dinliyorum…")
        if (yedekKullan || !SpeechRecognizer.isRecognitionAvailable(this)) {
            yedekDinle()
            return
        }
        try {
            tanimayiKapat()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            r.setRecognitionListener(dinleyici)
            tanima = r
            baslamaZamani = SystemClock.elapsedRealtime()
            r.startListening(tanimaIntenti(false))
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "SpeechRecognizer başlatılamadı, yedek kullanılacak", e)
            yedekKullan = true
            yedekDinle()
        }
    }

    private fun yedekVar(): Boolean = try {
        tanimaIntenti(true).resolveActivity(packageManager) != null
    } catch (e: Exception) {
        false
    }

    private fun yedekDinle() {
        if (!yedekVar()) {
            logla("-", "-", "ses tanıma hizmeti yok")
            bitir("Ses tanıma hizmeti bulunamadı. Tanı ekranına bakın.", false)
            return
        }
        try {
            yedekAcik = true
            yedekBaslatici.launch(tanimaIntenti(true))
        } catch (e: Exception) {
            yedekAcik = false
            logla("-", "RecognizerIntent", "hata: ${e.message}")
            bitir("Ses tanıma başlatılamadı.", false)
        }
    }

    private val dinleyici = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            durum(if (kisiBekleniyor) Cevaplar.KIMI_ARAYAYIM else "Dinliyorum…")
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) = seviyeGoster(rmsdB)

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            durum("Anlamaya çalışıyorum…")
        }

        override fun onError(error: Int) = tanimaHatasi(error)

        override fun onResults(results: Bundle?) {
            val liste = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (liste.isNullOrEmpty()) anlasilmadi() else sonuclariIsle(liste)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { metin(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun tanimaHatasi(kod: Int) {
        if (bitiyor) return
        val ad = hataAdi(kod)
        Log.i(AsistanApp.ETIKET, "SpeechRecognizer hatası: $ad")
        when (kod) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Bazı cihazlarda dinleme başlar başlamaz NO_MATCH gelir; bir kez sessizce yeniden dene.
                if (SystemClock.elapsedRealtime() - baslamaZamani < 800 && !hizliTekrarYapildi) {
                    hizliTekrarYapildi = true
                    dinle()
                } else {
                    anlasilmadi()
                }
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> mikrofonIzniIste()
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                if (!hizliTekrarYapildi) {
                    hizliTekrarYapildi = true
                    dinle()
                } else {
                    yedegeGec(ad)
                }
            }
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, 11 -> {
                if (!yedekKullan && yedekVar()) {
                    yedekKullan = true
                    yedekDinle()
                } else {
                    logla("-", "SpeechRecognizer", "hata: $ad")
                    bitir("İnternet bağlantısı yok gibi görünüyor.", false)
                }
            }
            else -> yedegeGec(ad)
        }
    }

    private fun yedegeGec(ad: String) {
        if (!yedekKullan && yedekVar()) {
            yedekKullan = true
            yedekDinle()
        } else {
            logla("-", "SpeechRecognizer", "hata: $ad")
            bitir("Ses tanıma hatası.", false)
        }
    }

    private fun hataAdi(kod: Int): String = when (kod) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "SERVER"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
        10 -> "TOO_MANY_REQUESTS"
        11 -> "SERVER_DISCONNECTED"
        12 -> "LANGUAGE_NOT_SUPPORTED"
        13 -> "LANGUAGE_UNAVAILABLE"
        else -> "kod $kod"
    }

    // ---------------------------------------------------------------- sonuçlar

    private fun sonuclariIsle(liste: List<String>) {
        if (bitiyor) return
        sonMetin = liste.first()
        metin(sonMetin)
        durum("…")
        val soru = bekleyenSoru
        if (soru != null) {
            var secim: Secim = Secim.Anlasilmadi
            for (m in liste) {
                secim = SecimCozucu.coz(m, soru.secenekler)
                if (secim !is Secim.Anlasilmadi) {
                    sonMetin = m
                    break
                }
            }
            when (val s = secim) {
                is Secim.Indeks -> {
                    bekleyenSoru = null
                    lifecycleScope.launch {
                        val plan = try {
                            soru.sec(s.i)
                        } catch (e: Exception) {
                            Plan.Eylem("Bir hata oluştu.", sonuc = "hata: ${e.message}")
                        }
                        planUygula(plan, bekleyenSoruOzeti + " → " + soru.secenekler[s.i])
                    }
                }
                Secim.Iptal -> {
                    logla(bekleyenSoruOzeti, "-", "seçim iptal")
                    bitir(Cevaplar.TAMAM, false)
                }
                Secim.Anlasilmadi -> {
                    if (tekrarHakki > 0) {
                        tekrarHakki--
                        soruSor(soru.konusma)
                    } else {
                        logla(bekleyenSoruOzeti, "-", "seçim anlaşılamadı")
                        bitir(Cevaplar.ANLAMADIM, false)
                    }
                }
            }
            return
        }
        val kisiSorusuydu = kisiBekleniyor
        kisiBekleniyor = false
        val niyet = if (kisiSorusuydu) {
            KomutCozucu.kisiCevabiCoz(sonMetin)
        } else {
            KomutCozucu.cozAdaylar(liste, uygulamaEtiketleri)
        }
        if (niyet is Niyet.Bilinmiyor) {
            kisiBekleniyor = kisiSorusuydu
            anlasilmadi()
            return
        }
        lifecycleScope.launch {
            val plan = try {
                Yurutucu(this@ListenActivity).planla(niyet)
            } catch (e: Exception) {
                Log.w(AsistanApp.ETIKET, "Plan hatası", e)
                Plan.Eylem("Bir hata oluştu.", sonuc = "hata: ${e.javaClass.simpleName}: ${e.message}")
            }
            planUygula(plan, niyet.ozet())
        }
    }

    private fun anlasilmadi() {
        if (bitiyor) return
        if (tekrarHakki > 0) {
            tekrarHakki--
            logla("BILINMIYOR", "-", "tekrar soruldu")
            soruSor(Cevaplar.ANLAMADIM_TEKRAR)
        } else {
            logla("BILINMIYOR", "-", "anlaşılamadı")
            bitir(Cevaplar.ANLAMADIM, false)
        }
    }

    /** Soruyu seslendirir, bitince yeniden dinler. Müzik bu sırada duraklatılmış kalır. */
    private fun soruSor(soru: String) {
        durum(soru)
        hizliTekrarYapildi = false
        app.konusmaci.konus(soru) {
            if (!bitiyor && !isFinishing) dinle()
        }
    }

    private suspend fun planUygula(plan: Plan, ozet: String) {
        when (plan) {
            is Plan.Soru -> {
                bekleyenSoru = plan
                bekleyenSoruOzeti = ozet
                tekrarHakki = 1
                logla(ozet, "soru", plan.konusma)
                soruSor(plan.konusma)
            }
            is Plan.KisiSor -> {
                kisiBekleniyor = true
                tekrarHakki = 1
                logla(ozet, "soru", plan.konusma)
                soruSor(plan.konusma)
            }
            is Plan.Eylem -> {
                durum(plan.konusma)
                if (plan.zamanlama == Zamanlama.ONCE) {
                    val rapor = eylemCalistir(plan)
                    logla(ozet, rapor.yontem, rapor.sonuc)
                    val soylenecek = rapor.ekKonusma?.let { "${plan.konusma}. $it" } ?: plan.konusma
                    bitir(soylenecek, plan.muzikDursun)
                } else {
                    tanimayiKapat()
                    app.konusmaci.konus(plan.konusma) {
                        lifecycleScope.launch {
                            app.odak.birak(plan.muzikDursun)
                            val rapor = eylemCalistir(plan)
                            logla(ozet, rapor.yontem, rapor.sonuc)
                            bitir(rapor.ekKonusma, false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun eylemCalistir(plan: Plan.Eylem): Rapor {
        val e = plan.eylem ?: return Rapor(plan.yontem, plan.sonuc)
        return try {
            e(this)
        } catch (ex: Exception) {
            Log.w(AsistanApp.ETIKET, "Eylem hatası", ex)
            Rapor(plan.yontem, "hata: ${ex.javaClass.simpleName}: ${ex.message}", "Bir hata oluştu.")
        }
    }

    private fun logla(niyet: String, yontem: String, sonuc: String) {
        val m = sonMetin
        app.kapsam.launch(Dispatchers.IO) { app.log.ekle(m, niyet, yontem, sonuc) }
    }

    /**
     * Konuşur ve ekranı hemen kapatır; konuşma uygulama seviyesinde sürer. Konuşma bitince ses odağı
     * bırakılır (müzik kaldığı yerden devam eder; [muzikDursun] ise devam etmez).
     */
    private fun bitir(soylenecek: String?, muzikDursun: Boolean) {
        if (bitiyor) return
        bitiyor = true
        tanimayiKapat()
        val odak = app.odak
        if (soylenecek.isNullOrBlank()) {
            odak.birak(muzikDursun)
        } else {
            durum(soylenecek)
            app.konusmaci.konus(soylenecek) { odak.birak(muzikDursun) }
        }
        finish()
        animasyonsuz()
    }

    private fun iptal(sessiz: Boolean) {
        if (bitiyor) return
        bitiyor = true
        tanimayiKapat()
        app.konusmaci.sustur()
        app.odak.birak(false)
        if (!sessiz) logla("IPTAL", "-", "kullanıcı kapattı")
        finish()
        animasyonsuz()
    }

    @Suppress("DEPRECATION")
    private fun animasyonsuz() = overridePendingTransition(0, 0)

    /** Sadece debug derlemede: emülatör duman testi için konuşma yerine metin (adb ile). */
    private fun testMetni(i: Intent?): String? =
        if (BuildConfig.DEBUG) i?.getStringExtra(TEST_METIN)?.takeIf { it.isNotBlank() } else null

    companion object {
        const val EXTRA_UYANDIRMA = "uyandirma"
        const val TEST_METIN = "test_metin"
        const val TEST_KURULUM = "test_kurulum_tamam"
    }
}
