package com.egea.asistan.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.egea.asistan.R
import com.egea.asistan.core.M3uOkuyucu
import com.egea.asistan.core.TurkceMetin
import com.egea.asistan.medya.GorselYukleyici
import com.egea.asistan.medya.Istasyon
import com.egea.asistan.medya.RadyoKatalogu
import com.egea.asistan.medya.RadyoKontrol
import com.egea.asistan.medya.TvKatalogu
import kotlinx.coroutines.launch

/** Radyo ve TV sayfalarının ortak iskeleti: başlık + arama + filtre çipleri + kart ızgarası. */
abstract class IzgaraSayfasi(protected val akt: AnaEkranActivity, baslik: String, hucreGenisligiDp: Int) {
    val gorunum: View
    protected lateinit var liste: RecyclerView
    protected lateinit var durum: TextView
    protected var sorgu = ""
    protected var filtre = ""
    private lateinit var cipKutusu: LinearLayout
    private val cipler = LinkedHashMap<String, TextView>()

    init {
        // Not: burada alt sınıfın alanları henüz hazır değil; sadece yapıcı parametreleri kullanılır.
        gorunum = kur(baslik, hucreGenisligiDp)
    }

    protected abstract fun uygula()
    protected abstract fun yenileTiklandi()

    /** Alt sınıf kendi alanları hazır olunca (init bloğunda) bağlar. */
    protected fun adaptorBagla(a: RecyclerView.Adapter<*>) {
        liste.adapter = a
    }

    private fun kur(baslik: String, hucreGenisligiDp: Int): View {
        val kol = LinearLayout(akt)
        kol.orientation = LinearLayout.VERTICAL
        kol.setPadding(akt.dp(20), akt.dp(14), akt.dp(20), 0)

        val ust = LinearLayout(akt)
        ust.orientation = LinearLayout.HORIZONTAL
        ust.gravity = Gravity.CENTER_VERTICAL
        ust.addView(TextView(akt).apply {
            text = baslik
            textSize = 30f
            setTextColor(Tema.YAZI)
            setTypeface(typeface, Typeface.BOLD)
        })
        ust.addView(View(akt), LinearLayout.LayoutParams(0, 1, 1f))
        val ara = EditText(akt).apply {
            hint = "Ara…"
            textSize = 19f
            setTextColor(Tema.YAZI)
            setHintTextColor(Tema.SOLUK)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = dalgali(Tema.KART, akt.dpF(14f))
            setPadding(akt.dp(16), akt.dp(10), akt.dp(16), akt.dp(10))
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_ara, 0, 0, 0)
            compoundDrawablePadding = akt.dp(10)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    sorgu = s?.toString()?.trim() ?: ""
                    uygula()
                }
            })
        }
        ust.addView(ara, LinearLayout.LayoutParams(akt.dp(340), akt.dp(54)))
        val yenile = ImageView(akt).apply {
            setImageResource(R.drawable.ic_yenile)
            renklendir(Tema.YAZI)
            background = dalgali(Tema.KART, akt.dpF(14f))
            setPadding(akt.dp(13), akt.dp(13), akt.dp(13), akt.dp(13))
            contentDescription = "Listeyi yenile"
            setOnClickListener { yenileTiklandi() }
        }
        ust.addView(yenile, LinearLayout.LayoutParams(akt.dp(54), akt.dp(54)).apply { marginStart = akt.dp(10) })
        kol.addView(ust)

        val kaydir = HorizontalScrollView(akt)
        kaydir.isHorizontalScrollBarEnabled = false
        cipKutusu = LinearLayout(akt)
        cipKutusu.orientation = LinearLayout.HORIZONTAL
        kaydir.addView(cipKutusu)
        kol.addView(kaydir, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = akt.dp(12) })

        durum = TextView(akt).apply {
            textSize = 15f
            setTextColor(Tema.SOLUK)
            setPadding(akt.dp(4), akt.dp(8), 0, akt.dp(4))
        }
        kol.addView(durum)

        liste = RecyclerView(akt)
        val ekranDp = akt.resources.displayMetrics.widthPixels / akt.resources.displayMetrics.density - 104 - 40
        val sutun = maxOf(2, (ekranDp / hucreGenisligiDp).toInt())
        liste.layoutManager = GridLayoutManager(akt, sutun)
        liste.setHasFixedSize(true)
        liste.setItemViewCacheSize(24)
        liste.clipToPadding = false
        liste.setPadding(0, 0, 0, akt.dp(16))
        (liste.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        kol.addView(liste, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return kol
    }

    protected fun cipleriKur(ogeler: List<Pair<String, String>>) {
        cipKutusu.removeAllViews()
        cipler.clear()
        for ((kod, ad) in ogeler) {
            val c = TextView(akt).apply {
                text = ad
                textSize = 17f
                setTextColor(Tema.YAZI)
                gravity = Gravity.CENTER
                setPadding(akt.dp(18), akt.dp(9), akt.dp(18), akt.dp(9))
                setOnClickListener {
                    filtre = kod
                    cipleriBoya()
                    uygula()
                }
            }
            cipKutusu.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = akt.dp(8) })
            cipler[kod] = c
        }
        if (filtre !in cipler) filtre = ogeler.firstOrNull()?.first ?: ""
        cipleriBoya()
    }

    private fun cipleriBoya() {
        for ((kod, c) in cipler) {
            val secili = kod == filtre
            c.background = dalgali(if (secili) Tema.VURGU else Tema.KART, akt.dpF(20f))
            c.setTextColor(if (secili) Tema.YAZI else Tema.SOLUK)
        }
    }
}

/** Kart görünümü: logo + ad + alt yazı + (opsiyonel) yıldız ve "çalıyor" işareti. */
class KartTutucu(
    val kok: FrameLayout,
    val logo: ImageView,
    val ad: TextView,
    val alt: TextView,
    val yildiz: ImageView,
    val etiket: TextView
) : RecyclerView.ViewHolder(kok) {
    companion object {
        fun olustur(ctx: Context, yukseklikDp: Int, logoGenislikDp: Int, logoYukseklikDp: Int): KartTutucu {
            val kok = FrameLayout(ctx)
            kok.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(yukseklikDp)).apply {
                setMargins(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
            }
            kok.isClickable = true
            val kol = LinearLayout(ctx)
            kol.orientation = LinearLayout.VERTICAL
            kol.gravity = Gravity.CENTER
            kol.setPadding(ctx.dp(10), ctx.dp(12), ctx.dp(10), ctx.dp(10))
            val logo = ImageView(ctx)
            logo.scaleType = ImageView.ScaleType.FIT_CENTER
            logo.setBackgroundColor(Tema.KART2)
            logo.yuvarlakKirp(ctx.dpF(14f))
            kol.addView(logo, LinearLayout.LayoutParams(ctx.dp(logoGenislikDp), ctx.dp(logoYukseklikDp)))
            val ad = TextView(ctx).apply {
                textSize = 17f
                setTextColor(Tema.YAZI)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTypeface(typeface, Typeface.BOLD)
            }
            kol.addView(ad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(8) })
            val alt = TextView(ctx).apply {
                textSize = 13f
                setTextColor(Tema.SOLUK)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            kol.addView(alt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            kok.addView(kol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val yildiz = ImageView(ctx)
            yildiz.setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
            kok.addView(yildiz, FrameLayout.LayoutParams(ctx.dp(42), ctx.dp(42), Gravity.TOP or Gravity.END))
            val etiket = TextView(ctx).apply {
                text = "▶ ÇALIYOR"
                textSize = 11f
                setTextColor(Tema.VURGU2)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(ctx.dp(10), ctx.dp(8), 0, 0)
                visibility = View.GONE
            }
            kok.addView(etiket, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
            return KartTutucu(kok, logo, ad, alt, yildiz, etiket)
        }
    }
}

/** Radyo istasyonları ızgarası. */
class IstasyonAdaptoru(
    private val ctx: Context,
    private val tikla: (Istasyon) -> Unit,
    private val favori: (Istasyon) -> Unit
) : RecyclerView.Adapter<KartTutucu>() {
    private var ogeler: List<Istasyon> = emptyList()
    var calanKimlik: String? = null
        private set
    private val logoPx = ctx.dp(72)

    init {
        setHasStableIds(true)
    }

    fun guncelle(l: List<Istasyon>) {
        ogeler = l
        notifyDataSetChanged()
    }

    fun ogeler(): List<Istasyon> = ogeler

    fun calanAyarla(kimlik: String?) {
        if (kimlik == calanKimlik) return
        val eski = calanKimlik
        calanKimlik = kimlik
        ogeler.indexOfFirst { it.kimlik == eski }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        ogeler.indexOfFirst { it.kimlik == kimlik }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    fun yenidenCiz(i: Istasyon) {
        ogeler.indexOf(i).takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun getItemId(position: Int): Long = ogeler[position].kimlik.hashCode().toLong()

    override fun getItemCount(): Int = ogeler.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KartTutucu =
        KartTutucu.olustur(ctx, 186, 76, 76)

    override fun onBindViewHolder(h: KartTutucu, position: Int) {
        val i = ogeler[position]
        val calan = i.kimlik == calanKimlik
        h.kok.background = dalgali(Tema.KART, ctx.dpF(18f), if (calan) ctx.dp(2) else 0, Tema.VURGU2)
        h.ad.text = i.gorunenAd
        h.alt.text = i.etiketListesi.take(2).joinToString(" • ").ifBlank { listOf(i.codec, if (i.bitHizi > 0) "${i.bitHizi} kbps" else "").filter { it.isNotBlank() }.joinToString(" ") }
        h.etiket.visibility = if (calan) View.VISIBLE else View.GONE
        val fav = RadyoKatalogu.favoriMi(i)
        h.yildiz.setImageResource(if (fav) R.drawable.ic_yildiz else R.drawable.ic_yildiz_bos)
        h.yildiz.renklendir(if (fav) Tema.TURUNCU else Tema.SOLUK)
        h.yildiz.setOnClickListener { favori(i) }
        h.kok.setOnClickListener { tikla(i) }
        GorselYukleyici.yukle(h.logo, i.logo, logoPx, BasHarfCizimi(i.gorunenAd, logoPx))
    }
}

/** Radyo sayfası. */
class RadyoSayfasi(akt: AnaEkranActivity) : IzgaraSayfasi(akt, "Radyo", 190) {
    private var tumu: List<Istasyon> = emptyList()
    private var yuklendi = false
    private val istasyonAdaptoru = IstasyonAdaptoru(akt, { cal(it) }, { fav(it) })

    private val FILTRELER = listOf(
        "populer" to "Popüler", "favori" to "★ Favoriler", "pop" to "Pop", "turk" to "Türkçe", "rock" to "Rock",
        "arabesk" to "Arabesk", "turku" to "Türkü", "slow" to "Slow", "haber" to "Haber", "klasik" to "Klasik",
        "caz" to "Caz", "spor" to "Spor", "dini" to "Dini"
    )
    private val ETIKET_KELIMELERI = mapOf(
        "pop" to listOf("pop"), "turk" to listOf("turk", "turkce", "turkish"), "rock" to listOf("rock"),
        "arabesk" to listOf("arabesk", "fantezi"), "turku" to listOf("turku", "halk", "folk"),
        "slow" to listOf("slow", "romantik", "soft", "love"), "haber" to listOf("news", "haber", "talk"),
        "klasik" to listOf("classical", "klasik"), "caz" to listOf("jazz", "caz"), "spor" to listOf("sport", "spor"),
        "dini" to listOf("religious", "islam", "ilahi", "dini", "kuran", "quran")
    )

    init {
        cipleriKur(FILTRELER)
        adaptorBagla(istasyonAdaptoru)
    }

    fun goruldu() {
        if (!yuklendi) yukle(false)
        istasyonAdaptoru.calanAyarla(RadyoKontrol.anlik()?.currentMediaItem?.mediaId)
    }

    override fun yenileTiklandi() = yukle(true)

    private fun yukle(zorla: Boolean) {
        durum.text = "Radyo listesi yükleniyor…"
        akt.lifecycleScope.launch {
            tumu = RadyoKatalogu.yukle(akt, zorla)
            yuklendi = true
            uygula()
            if (tumu.isEmpty()) {
                durum.text = "Radyo listesi alınamadı. İnternet bağlantısını kontrol edip yenileyin." +
                    (RadyoKatalogu.sonHata?.let { " ($it)" } ?: "")
            }
        }
    }

    override fun uygula() {
        val q = TurkceMetin.katla(sorgu)
        val favoriler = com.egea.asistan.AsistanApp.ornek.ayarlar.favoriIstasyonlar
        val sonuc = tumu.filter { i ->
            val adUygun = q.isEmpty() || TurkceMetin.katla(i.ad).contains(q)
            val filtreUygun = when (filtre) {
                "", "populer" -> true
                "favori" -> i.kimlik in favoriler
                else -> {
                    val etiketler = TurkceMetin.katla(i.etiketler + " " + i.ad)
                    ETIKET_KELIMELERI[filtre]?.any { etiketler.contains(it) } ?: true
                }
            }
            adUygun && filtreUygun
        }
        istasyonAdaptoru.guncelle(sonuc)
        if (tumu.isNotEmpty()) {
            durum.text = if (sonuc.isEmpty()) {
                if (filtre == "favori") "Henüz favori yok. Kartlardaki ☆ ile ekleyin." else "Sonuç yok."
            } else {
                "${sonuc.size} istasyon • dokunun, çalsın • reklam eklenmez"
            }
        }
    }

    private fun cal(i: Istasyon) {
        val gorunen = istasyonAdaptoru.ogeler()
        val indeks = gorunen.indexOf(i).coerceAtLeast(0)
        istasyonAdaptoru.calanAyarla(i.kimlik)
        akt.lifecycleScope.launch {
            try {
                RadyoKontrol.cal(akt, if (gorunen.isEmpty()) listOf(i) else gorunen, if (gorunen.isEmpty()) 0 else indeks)
            } catch (e: Exception) {
                durum.text = "Radyo başlatılamadı: ${e.message}"
            }
        }
    }

    private fun fav(i: Istasyon) {
        RadyoKatalogu.favoriDegistir(i)
        if (filtre == "favori") uygula() else istasyonAdaptoru.yenidenCiz(i)
    }
}

/** TV kanalları ızgarası. */
class KanalAdaptoru(private val ctx: Context, private val tikla: (M3uOkuyucu.Kanal) -> Unit) : RecyclerView.Adapter<KartTutucu>() {
    private var ogeler: List<M3uOkuyucu.Kanal> = emptyList()
    private val logoPx = ctx.dp(120)

    init {
        setHasStableIds(true)
    }

    fun guncelle(l: List<M3uOkuyucu.Kanal>) {
        ogeler = l
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = ogeler[position].url.hashCode().toLong()

    override fun getItemCount(): Int = ogeler.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KartTutucu =
        KartTutucu.olustur(ctx, 176, 128, 72)

    override fun onBindViewHolder(h: KartTutucu, position: Int) {
        val k = ogeler[position]
        h.kok.background = dalgali(Tema.KART, ctx.dpF(18f))
        h.ad.text = k.ad
        val ek = k.etiketler.filter { it.contains("Geo", true) || it.contains("24/7", true) }
        h.alt.text = (listOf(TvKatalogu.grupAdi(k.grup)) + ek.map { if (it.contains("Geo", true)) "Türkiye'den" else "Her an değil" }).joinToString(" • ")
        h.yildiz.setImageDrawable(null)
        h.etiket.visibility = View.GONE
        h.kok.setOnClickListener { tikla(k) }
        GorselYukleyici.yukle(h.logo, k.logo, logoPx, BasHarfCizimi(k.ad, ctx.dp(72)))
    }
}

/** TV sayfası. */
class TvSayfasi(akt: AnaEkranActivity) : IzgaraSayfasi(akt, "Televizyon", 200) {
    private var tumu: List<M3uOkuyucu.Kanal> = emptyList()
    private var yuklendi = false
    private val kanalAdaptoru = KanalAdaptoru(akt) { ac(it) }

    init {
        cipleriKur(listOf("" to "Tümü"))
        adaptorBagla(kanalAdaptoru)
    }

    fun goruldu() {
        if (!yuklendi) yukle(false)
    }

    override fun yenileTiklandi() = yukle(true)

    private fun yukle(zorla: Boolean) {
        durum.text = "Kanal listesi yükleniyor…"
        akt.lifecycleScope.launch {
            tumu = TvKatalogu.yukle(akt, zorla)
            yuklendi = true
            val gruplar = tumu.groupingBy { TvKatalogu.grupAdi(it.grup) }.eachCount().entries
                .sortedByDescending { it.value }.map { it.key to it.key }
            cipleriKur(listOf("" to "Tümü") + gruplar)
            uygula()
            if (tumu.isEmpty()) {
                durum.text = "Kanal listesi alınamadı. İnternet bağlantısını kontrol edip yenileyin." +
                    (TvKatalogu.sonHata?.let { " ($it)" } ?: "")
            }
        }
    }

    override fun uygula() {
        val q = TurkceMetin.katla(sorgu)
        val sonuc = tumu.filter { k ->
            (q.isEmpty() || TurkceMetin.katla(k.ad).contains(q)) && (filtre.isEmpty() || TvKatalogu.grupAdi(k.grup) == filtre)
        }
        kanalAdaptoru.guncelle(sonuc)
        if (tumu.isNotEmpty()) {
            durum.text = if (sonuc.isEmpty()) "Sonuç yok." else "${sonuc.size} kanal • ücretsiz açık yayınlar • reklam eklenmez"
        }
    }

    private fun ac(k: M3uOkuyucu.Kanal) {
        akt.startActivity(TvActivity.intent(akt, k.url))
    }
}
