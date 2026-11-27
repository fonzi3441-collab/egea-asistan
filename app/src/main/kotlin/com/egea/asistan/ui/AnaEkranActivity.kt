package com.egea.asistan.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.egea.asistan.AsistanApp
import com.egea.asistan.R
import com.egea.asistan.medya.RadyoKatalogu
import com.egea.asistan.medya.TvKatalogu
import com.egea.asistan.servis.YuzenButonServisi
import com.egea.asistan.uyandirma.UyandirmaDeposu
import com.egea.asistan.uyandirma.UyandirmaKontrol
import com.egea.asistan.yurutme.MuzikYoneticisi
import com.egea.asistan.yurutme.MedyaYoneticisi
import com.egea.asistan.yurutme.NavigasyonYoneticisi
import com.egea.asistan.yurutme.UygulamaYoneticisi
import kotlinx.coroutines.launch

/**
 * Ana ekran ("Egea"): sol rayda Ana Sayfa / Radyo / TV / Asistan / Ayarlar, altta şimdi çalan radyo.
 * Sayfalar bir kez kurulup önbellekte tutulur; geçişler GPU katmanında kısa animasyonla yapılır.
 */
class AnaEkranActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SAYFA = "sayfa"
    }

    private lateinit var icerik: FrameLayout
    private val rayOgeleri = LinkedHashMap<String, LinearLayout>()
    private var seciliSayfa = ""
    private var anaSayfa: AnaSayfa? = null
    private var radyoSayfasi: RadyoSayfasi? = null
    private var tvSayfasi: TvSayfasi? = null
    private lateinit var mini: MiniOynatici
    private lateinit var fpsYazi: TextView
    private var fpsOlcer: FpsOlcer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enYuksekYenileme()
        window.setBackgroundDrawable(ColorDrawable(Tema.ARKA))
        setContentView(kur())
        sayfaGoster(intent.getStringExtra(EXTRA_SAYFA) ?: "ana")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SAYFA)?.let { sayfaGoster(it) }
    }

    override fun onStart() {
        super.onStart()
        mini.baglan()
    }

    override fun onStop() {
        mini.ayir()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Ön plandayken başlatılınca Hey Egea mikrofonu arka plan kısıtına takılmaz.
        YuzenButonServisi.uygunsaBaslat(this)
        anaSayfa?.yenile()
        if (AsistanApp.ornek.ayarlar.fpsGoster) fpsBaslat() else fpsDurdur()
    }

    override fun onPause() {
        fpsDurdur()
        super.onPause()
    }

    private fun fpsBaslat() {
        fpsYazi.visibility = View.VISIBLE
        val o = fpsOlcer ?: FpsOlcer { fps -> fpsYazi.text = "$fps FPS" }.also { fpsOlcer = it }
        o.baslat()
    }

    private fun fpsDurdur() {
        fpsOlcer?.durdur()
        if (::fpsYazi.isInitialized) fpsYazi.visibility = View.GONE
    }

    private fun kur(): View {
        val kok = FrameLayout(this)
        val yatay = LinearLayout(this)
        yatay.orientation = LinearLayout.HORIZONTAL

        val ray = LinearLayout(this)
        ray.orientation = LinearLayout.VERTICAL
        ray.gravity = Gravity.CENTER_HORIZONTAL
        ray.setBackgroundColor(Tema.RAY)
        ray.setPadding(dp(8), dp(18), dp(8), dp(14))
        ray.addView(TextView(this).apply {
            text = "EGEA"
            textSize = 19f
            letterSpacing = 0.25f
            setTextColor(Tema.VURGU2)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(18) })
        rayOgesi(ray, "ana", "Ana Sayfa", R.drawable.ic_ev)
        rayOgesi(ray, "radyo", "Radyo", R.drawable.ic_radyo)
        rayOgesi(ray, "tv", "TV", R.drawable.ic_tv)
        ray.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        rayOgesi(ray, "asistan", "Asistan", R.drawable.ic_mic) {
            startActivity(Intent(this, ListenActivity::class.java))
        }
        rayOgesi(ray, "ayarlar", "Ayarlar", R.drawable.ic_ayar) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        yatay.addView(ray, LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.MATCH_PARENT))

        val sag = LinearLayout(this)
        sag.orientation = LinearLayout.VERTICAL
        icerik = FrameLayout(this)
        sag.addView(icerik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mini = MiniOynatici(this) { sayfaGoster("radyo") }
        sag.addView(mini.gorunum, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        yatay.addView(sag, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        kok.addView(yatay)

        fpsYazi = TextView(this).apply {
            textSize = 13f
            setTextColor(Tema.YESIL)
            setBackgroundColor(Tema.SEFFAF_SIYAH)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
        }
        kok.addView(fpsYazi, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        return kok
    }

    private fun rayOgesi(ray: LinearLayout, kod: String, ad: String, ikon: Int, tikla: (() -> Unit)? = null) {
        val oge = LinearLayout(this)
        oge.orientation = LinearLayout.VERTICAL
        oge.gravity = Gravity.CENTER
        oge.setPadding(0, dp(10), 0, dp(10))
        oge.background = dalgali(Tema.RAY, dpF(16f))
        oge.isClickable = true
        val iv = ImageView(this)
        iv.setImageResource(ikon)
        iv.renklendir(Tema.SOLUK)
        oge.addView(iv, LinearLayout.LayoutParams(dp(30), dp(30)))
        val tv = TextView(this)
        tv.text = ad
        tv.textSize = 13f
        tv.setTextColor(Tema.SOLUK)
        tv.gravity = Gravity.CENTER
        oge.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        oge.setOnClickListener { if (tikla != null) tikla() else sayfaGoster(kod) }
        ray.addView(oge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
        if (tikla == null) rayOgeleri[kod] = oge
    }

    fun sayfaGoster(kod: String) {
        if (kod == seciliSayfa) return
        val v = when (kod) {
            "radyo" -> (radyoSayfasi ?: RadyoSayfasi(this).also { radyoSayfasi = it }).gorunum
            "tv" -> (tvSayfasi ?: TvSayfasi(this).also { tvSayfasi = it }).gorunum
            else -> (anaSayfa ?: AnaSayfa(this).also { anaSayfa = it }).gorunum
        }
        icerik.removeAllViews()
        (v.parent as? ViewGroup)?.removeView(v)
        icerik.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        v.alpha = 0f
        v.translationY = dpF(14f)
        v.animate().alpha(1f).translationY(0f).setDuration(170L).withLayer().start()
        seciliSayfa = if (kod in rayOgeleri) kod else "ana"
        for ((k, oge) in rayOgeleri) {
            val secili = k == seciliSayfa
            oge.background = dalgali(if (secili) 0x332E86F5 else Tema.RAY, dpF(16f))
            (oge.getChildAt(0) as ImageView).renklendir(if (secili) Tema.VURGU else Tema.SOLUK)
            (oge.getChildAt(1) as TextView).setTextColor(if (secili) Tema.YAZI else Tema.SOLUK)
        }
        when (seciliSayfa) {
            "radyo" -> radyoSayfasi?.goruldu()
            "tv" -> tvSayfasi?.goruldu()
            else -> anaSayfa?.yenile()
        }
    }
}

/** Ana sayfa: büyük saat, mikrofon kartı ve hızlı erişim kutuları. */
class AnaSayfa(private val akt: AnaEkranActivity) {
    val gorunum: View
    private lateinit var durumYazi: TextView
    private val altYazilar = HashMap<String, TextView>()

    init {
        gorunum = kur()
    }

    private fun kur(): View {
        val sv = ScrollView(akt)
        sv.isFillViewport = true
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        val kol = LinearLayout(akt)
        kol.orientation = LinearLayout.VERTICAL
        kol.setPadding(akt.dp(28), akt.dp(18), akt.dp(28), akt.dp(18))

        val ust = LinearLayout(akt)
        ust.orientation = LinearLayout.HORIZONTAL
        ust.gravity = Gravity.CENTER_VERTICAL
        val saat = TextClock(akt).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 88f
            includeFontPadding = false
            setTextColor(Tema.YAZI)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        ust.addView(saat)
        val sagKol = LinearLayout(akt)
        sagKol.orientation = LinearLayout.VERTICAL
        sagKol.setPadding(akt.dp(28), 0, 0, 0)
        sagKol.addView(TextClock(akt).apply {
            format24Hour = "d MMMM yyyy, EEEE"
            format12Hour = "d MMMM yyyy, EEEE"
            textSize = 22f
            setTextColor(Tema.SOLUK)
        })
        durumYazi = TextView(akt).apply {
            textSize = 16f
            setTextColor(Tema.YAZI)
            setPadding(akt.dp(14), akt.dp(6), akt.dp(14), akt.dp(6))
            background = dalgali(Tema.KART2, akt.dpF(14f))
            setOnClickListener { akt.startActivity(Intent(akt, UyandirmaOgretActivity::class.java)) }
        }
        sagKol.addView(durumYazi, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = akt.dp(10) })
        ust.addView(sagKol)
        kol.addView(ust)

        val mik = LinearLayout(akt)
        mik.orientation = LinearLayout.HORIZONTAL
        mik.gravity = Gravity.CENTER_VERTICAL
        mik.background = gecisli(Tema.VURGU, Tema.VURGU2, akt.dpF(26f))
        mik.setPadding(akt.dp(26), akt.dp(20), akt.dp(26), akt.dp(20))
        mik.isClickable = true
        mik.setOnClickListener { akt.startActivity(Intent(akt, ListenActivity::class.java)) }
        val mikIkon = ImageView(akt)
        mikIkon.setImageResource(R.drawable.ic_mic)
        mikIkon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x33FFFFFF)
        }
        mikIkon.setPadding(akt.dp(12), akt.dp(12), akt.dp(12), akt.dp(12))
        mik.addView(mikIkon, LinearLayout.LayoutParams(akt.dp(72), akt.dp(72)))
        val mikYazi = LinearLayout(akt)
        mikYazi.orientation = LinearLayout.VERTICAL
        mikYazi.setPadding(akt.dp(22), 0, 0, 0)
        mikYazi.addView(TextView(akt).apply {
            text = "Konuşmak için dokun"
            textSize = 26f
            setTextColor(Tema.YAZI)
            setTypeface(typeface, Typeface.BOLD)
        })
        mikYazi.addView(TextView(akt).apply {
            text = "ya da “Hey Egea” de  •  “Kral FM aç”, “TRT 1 aç”, “Ahmet'i ara”, “eve git”"
            textSize = 16f
            setTextColor(0xE6FFFFFF.toInt())
        })
        mik.addView(mikYazi)
        kol.addView(mik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = akt.dp(20) })

        val nav = NavigasyonYoneticisi(akt)
        val muzik = MuzikYoneticisi(akt, MedyaYoneticisi(akt))
        val kutular = listOf(
            Kutu("radyo", "Radyo", R.drawable.ic_radyo, Tema.KUTU_RENKLERI[0]) { akt.sayfaGoster("radyo") },
            Kutu("tv", "Televizyon", R.drawable.ic_tv, Tema.KUTU_RENKLERI[2]) { akt.sayfaGoster("tv") },
            Kutu("nav", "Navigasyon", R.drawable.ic_nav, Tema.KUTU_RENKLERI[1]) {
                val u = nav.secili()
                val i = u?.let { akt.packageManager.getLaunchIntentForPackage(it.paket) }
                if (i != null) akt.startActivity(i) else Toast.makeText(akt, "Navigasyon uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
            },
            Kutu("muzik", "Müzik", R.drawable.ic_muzik, Tema.KUTU_RENKLERI[3]) {
                val u = muzik.secili()
                val i = u?.let { akt.packageManager.getLaunchIntentForPackage(it.paket) }
                if (i != null) akt.startActivity(i) else Toast.makeText(akt, "Müzik uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
            },
            Kutu("tel", "Telefon", R.drawable.ic_telefon, Tema.KUTU_RENKLERI[4]) {
                val b = UygulamaYoneticisi(akt).bul("telefon")
                try {
                    if (b != null) akt.startActivity(b.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else akt.startActivity(Intent(Intent.ACTION_DIAL))
                } catch (e: Exception) {
                    Toast.makeText(akt, "Telefon uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                }
            },
            Kutu("ev", "Eve git", R.drawable.ic_ev_git, Tema.KUTU_RENKLERI[5]) {
                val ev = AsistanApp.ornek.ayarlar.evAdresi
                if (ev.isBlank()) {
                    Toast.makeText(akt, "Önce Ayarlar'dan ev adresini girin", Toast.LENGTH_LONG).show()
                } else {
                    akt.lifecycleScope.launch {
                        try {
                            akt.startActivity(nav.hazirla(ev, true).intent)
                        } catch (e: Exception) {
                            Toast.makeText(akt, "Navigasyon açılamadı", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        for (satirNo in 0 until 2) {
            val satir = LinearLayout(akt)
            satir.orientation = LinearLayout.HORIZONTAL
            for (k in kutular.subList(satirNo * 3, satirNo * 3 + 3)) {
                satir.addView(kutuYap(k), LinearLayout.LayoutParams(0, akt.dp(128), 1f).apply {
                    setMargins(akt.dp(6), akt.dp(6), akt.dp(6), akt.dp(6))
                })
            }
            kol.addView(satir, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (satirNo == 0) akt.dp(14) else 0
            })
        }
        sv.addView(kol)
        yenile()
        return sv
    }

    private class Kutu(val kod: String, val ad: String, val ikon: Int, val renk: Int, val tikla: () -> Unit)

    private fun kutuYap(k: Kutu): View {
        val v = LinearLayout(akt)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_VERTICAL
        v.background = dalgali(Tema.KART, akt.dpF(22f))
        v.setPadding(akt.dp(20), akt.dp(14), akt.dp(16), akt.dp(14))
        v.isClickable = true
        v.setOnClickListener { k.tikla() }
        val ikon = ImageView(akt)
        ikon.setImageResource(k.ikon)
        ikon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(k.renk)
        }
        ikon.setPadding(akt.dp(11), akt.dp(11), akt.dp(11), akt.dp(11))
        v.addView(ikon, LinearLayout.LayoutParams(akt.dp(50), akt.dp(50)))
        v.addView(TextView(akt).apply {
            text = k.ad
            textSize = 20f
            setTextColor(Tema.YAZI)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, akt.dp(8), 0, 0)
        })
        val alt = TextView(akt).apply {
            textSize = 14f
            setTextColor(Tema.SOLUK)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        v.addView(alt)
        altYazilar[k.kod] = alt
        return v
    }

    fun yenile() {
        if (!::durumYazi.isInitialized) return
        val a = AsistanApp.ornek.ayarlar
        durumYazi.text = when {
            !UyandirmaDeposu.varMi(akt) -> "○  Hey Egea öğretilmedi — dokunun"
            !a.uyandirma -> "○  Hey Egea kapalı — dokunun"
            UyandirmaKontrol.durum == "dinliyor" -> "●  Hey Egea dinliyor"
            else -> "◐  Hey Egea: ${UyandirmaKontrol.durum}"
        }
        durumYazi.setTextColor(if (UyandirmaKontrol.durum == "dinliyor" && a.uyandirma) Tema.YESIL else Tema.YAZI)
        altYazilar["radyo"]?.text = RadyoKatalogu.sonIstasyon()?.let { "Son: ${it.gorunenAd}" } ?: "Türkiye radyoları"
        val kanal = TvKatalogu.sonKanal(TvKatalogu.liste(akt))
        altYazilar["tv"]?.text = kanal?.let { "Son: ${it.ad}" } ?: "Açık yayın kanalları"
        altYazilar["nav"]?.text = NavigasyonYoneticisi(akt).secili()?.ad ?: "Uygulama yok"
        altYazilar["muzik"]?.text = MuzikYoneticisi(akt, MedyaYoneticisi(akt)).secili()?.ad ?: "Uygulama yok"
        altYazilar["tel"]?.text = "Bluetooth telefon"
        altYazilar["ev"]?.text = a.evAdresi.ifBlank { "Adres girilmedi" }
    }
}
