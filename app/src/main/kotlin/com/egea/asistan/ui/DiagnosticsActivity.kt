package com.egea.asistan.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.egea.asistan.servis.TusOlaylari
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tanı ekranı: gerçek Android sürümü/API, ABI, RAM, ses tanıma ve Türkçe TTS durumu, yüklü
 * navigasyon/müzik/Bluetooth uygulamaları, tel: çözücüleri, PhoneAccount'lar, izinler, basılan tuş
 * kodları ve son 50 komut. Tek ekranda üç sütun; fotoğrafı çekilip gönderilebilir.
 */
class DiagnosticsActivity : ComponentActivity() {
    private lateinit var sutun1: TextView
    private lateinit var sutun2: TextView
    private lateinit var sutun3: TextView
    private val tuslar = ArrayList<TusOlaylari.Olay>()
    private var turkceTanima = "sorgulanıyor…"

    private val tusDinleyici = TusOlaylari.Dinleyici { o -> tusEkle(o) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(arayuz())
        turkceTanimaSorgula()
        yenile()
    }

    override fun onResume() {
        super.onResume()
        TusOlaylari.ekle(tusDinleyici)
    }

    override fun onPause() {
        TusOlaylari.cikar(tusDinleyici)
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.repeatCount == 0) tusEkle(TusOlaylari.olayYap(event, "ekran"))
        return super.dispatchKeyEvent(event)
    }

    private fun tusEkle(o: TusOlaylari.Olay) {
        tuslar.add(0, o)
        while (tuslar.size > 30) tuslar.removeAt(tuslar.lastIndex)
        yenile(sadeceUygulamalar = true)
    }

    private fun sutun(): Pair<ScrollView, TextView> {
        val t = TextView(this)
        t.typeface = Typeface.MONOSPACE
        t.textSize = 12f
        t.setTextColor(Renk.YAZI)
        t.setLineSpacing(0f, 1.05f)
        t.setPadding(dp(8), dp(4), dp(8), dp(16))
        t.setTextIsSelectable(true)
        val s = ScrollView(this)
        s.background = yuvarlak(Renk.PANEL, dp(8).toFloat())
        s.addView(t)
        return s to t
    }

    private fun arayuz(): LinearLayout {
        val kok = LinearLayout(this)
        kok.orientation = LinearLayout.VERTICAL
        kok.setBackgroundColor(Renk.ARKA)
        kok.setPadding(dp(8), dp(6), dp(8), dp(6))

        val ust = LinearLayout(this)
        ust.orientation = LinearLayout.HORIZONTAL
        ust.gravity = Gravity.CENTER_VERTICAL
        val baslik = TextView(this).apply {
            text = "Egea Asistan • Tanı"
            textSize = 20f
            setTextColor(Renk.YAZI)
            setTypeface(typeface, Typeface.BOLD)
        }
        ust.addView(baslik, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun dugme(metin: String, renk: Int, tikla: () -> Unit) {
            val b = Button(this).apply {
                text = metin
                textSize = 15f
                isAllCaps = false
                setTextColor(Renk.YAZI)
                background = yuvarlak(renk, dp(8).toFloat())
                stateListAnimator = null
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { tikla() }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44))
            lp.marginStart = dp(6)
            ust.addView(b, lp)
        }
        dugme("Yenile", Renk.VURGU) { yenile() }
        dugme("Kopyala", Renk.PANEL2) { kopyala() }
        dugme("Paylaş", Renk.PANEL2) { paylas() }
        dugme("TTS ayarları", Renk.PANEL2) { ttsAyarlari() }
        dugme("Google Konuşma Hizmetleri", Renk.TURUNCU) { googleTts() }
        dugme("Kapat", Renk.PANEL2) { finish() }
        kok.addView(ust, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sutunlar = LinearLayout(this)
        sutunlar.orientation = LinearLayout.HORIZONTAL
        val (s1, t1) = sutun()
        val (s2, t2) = sutun()
        val (s3, t3) = sutun()
        sutun1 = t1
        sutun2 = t2
        sutun3 = t3
        for ((i, s) in listOf(s1, s2, s3).withIndex()) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) lp.marginStart = dp(6)
            sutunlar.addView(s, lp)
        }
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        slp.topMargin = dp(6)
        kok.addView(sutunlar, slp)
        return kok
    }

    private fun yenile(sadeceUygulamalar: Boolean = false) {
        val tusKopya = ArrayList(tuslar)
        val tt = turkceTanima
        lifecycleScope.launch {
            val rapor = TaniRaporu(applicationContext)
            if (sadeceUygulamalar) {
                val m2 = withContext(Dispatchers.IO) { rapor.uygulamalar(tusKopya) }
                sutun2.text = m2
                return@launch
            }
            val (m1, m2, m3) = withContext(Dispatchers.IO) {
                Triple(rapor.sistem(tt), rapor.uygulamalar(tusKopya), rapor.komutlar())
            }
            sutun1.text = m1
            sutun2.text = m2
            sutun3.text = m3
        }
    }

    /** Varsayılan tanıyıcıya desteklenen dilleri sorar (RecognizerIntent.getVoiceDetailsIntent). */
    private fun turkceTanimaSorgula() {
        val detay = try {
            RecognizerIntent.getVoiceDetailsIntent(this)
        } catch (e: Exception) {
            null
        }
        if (detay == null) {
            turkceTanima = "sorgulanamıyor (tanıyıcı dil listesi vermiyor)"
            return
        }
        try {
            sendOrderedBroadcast(detay, null, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val ekstra = getResultExtras(true)
                    val diller = ekstra.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                    val tercih = ekstra.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE)
                    val durum = when {
                        diller == null -> "bilinmiyor (liste gelmedi)"
                        diller.any { it.lowercase().startsWith("tr") } -> "VAR"
                        else -> "YOK (${diller.size} dil destekleniyor)"
                    }
                    val tercihMetni = if (tercih != null) " • tercih: $tercih" else ""
                    turkceTanima = durum + tercihMetni
                    yenile()
                }
            }, null, Activity.RESULT_OK, null, null)
        } catch (e: Exception) {
            turkceTanima = "sorgulanamadı: ${e.message}"
        }
    }

    private fun tumMetin(): String =
        "${sutun1.text}\n${sutun2.text}\n${sutun3.text}"

    private fun kopyala() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Egea Asistan tanı", tumMetin()))
            Toast.makeText(this, "Panoya kopyalandı", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Kopyalanamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun paylas() {
        try {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tumMetin())
            startActivity(Intent.createChooser(i, "Tanı bilgisini paylaş"))
        } catch (e: Exception) {
            Toast.makeText(this, "Paylaşacak uygulama yok", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ttsAyarlari() {
        val secenekler = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (i in secenekler) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // sonrakini dene
            }
        }
        Toast.makeText(this, "TTS ayarları açılamadı", Toast.LENGTH_LONG).show()
    }

    private fun googleTts() {
        val paket = "com.google.android.tts"
        val secenekler = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$paket")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$paket"))
        )
        for (i in secenekler) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // sonrakini dene
            }
        }
        Toast.makeText(this, "Play Store yok: \"Google Konuşma Hizmetleri\" APK'sını tarayıcıdan indirin", Toast.LENGTH_LONG).show()
    }
}
