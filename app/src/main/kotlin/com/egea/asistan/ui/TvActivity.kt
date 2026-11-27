package com.egea.asistan.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.egea.asistan.AsistanApp
import com.egea.asistan.R
import com.egea.asistan.core.M3uOkuyucu
import com.egea.asistan.medya.GorselYukleyici
import com.egea.asistan.medya.HizIzleyici
import com.egea.asistan.medya.RadyoKontrol
import com.egea.asistan.medya.TvKatalogu
import kotlinx.coroutines.launch

/**
 * Tam ekran canlı TV. Dokununca üst çubuk ve kanal listesi açılır; önceki/sonraki kanal; direksiyon
 * medya tuşları ve "sonraki kanal" sesli komutu çalışır. Sürüş kilidi açıkken araç hareket ediyorsa
 * görüntü gizlenir, ses devam eder.
 */
class TvActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_URL = "url"
        fun intent(ctx: Context, url: String): Intent =
            Intent(ctx, TvActivity::class.java).putExtra(EXTRA_URL, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private var oynatici: ExoPlayer? = null
    private var oturum: MediaSession? = null
    private lateinit var oynaticiGorunumu: PlayerView
    private lateinit var arayuz: FrameLayout
    private lateinit var ustCubuk: LinearLayout
    private lateinit var kanalAdi: TextView
    private lateinit var bilgi: TextView
    private lateinit var listePaneli: LinearLayout
    private lateinit var kanalListesi: RecyclerView
    private lateinit var kilitPerdesi: LinearLayout
    private var kanallar: List<M3uOkuyucu.Kanal> = emptyList()
    private var indeks = 0
    private var hiz: HizIzleyici? = null
    private val ana = Handler(Looper.getMainLooper())
    private val gizle = Runnable { arayuzGoster(false) }
    private val listeAdaptoru = KanalListesiAdaptoru()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enYuksekYenileme()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(kur())
        RadyoKontrol.duraklat()
        oynaticiKur()
        val url = intent.getStringExtra(EXTRA_URL)
        lifecycleScope.launch {
            kanallar = TvKatalogu.liste(this@TvActivity).ifEmpty { TvKatalogu.yukle(this@TvActivity) }
            listeAdaptoru.notifyDataSetChanged()
            indeks = kanallar.indexOfFirst { it.url == url }.let { if (it < 0) 0 else it }
            if (kanallar.isEmpty()) {
                bilgi.text = "Kanal listesi alınamadı."
                arayuzGoster(true)
            } else {
                oynat(indeks)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val i = kanallar.indexOfFirst { it.url == url }
        if (i >= 0) oynat(i)
    }

    override fun onStart() {
        super.onStart()
        if (AsistanApp.ornek.ayarlar.surusKilidi) {
            hiz = HizIzleyici(this) { hareketli -> kilitPerdesi.visibility = if (hareketli) View.VISIBLE else View.GONE }
                .also { it.baslat() }
        }
        oynatici?.play()
    }

    override fun onStop() {
        hiz?.durdur()
        hiz = null
        oynatici?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        ana.removeCallbacksAndMessages(null)
        oturum?.release()
        oturum = null
        oynatici?.release()
        oynatici = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- oynatıcı
    private inner class KanalOynatici(p: Player) : ForwardingPlayer(p) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

        override fun isCommandAvailable(command: Int): Boolean = when (command) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }

        override fun seekToNext() = kanalDegistir(1)
        override fun seekToNextMediaItem() = kanalDegistir(1)
        override fun seekToPrevious() = kanalDegistir(-1)
        override fun seekToPreviousMediaItem() = kanalDegistir(-1)
        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true
    }

    private fun oynaticiKur() {
        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val ad = kanallar.getOrNull(indeks)?.ad ?: ""
                bilgi.text = "$ad açılamadı (${error.errorCodeName}). Sonraki kanal için ▶ ya da listeden seçin."
                arayuzGoster(true, otomatikGizle = false)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) bilgi.text = ""
            }
        })
        oynatici = p
        oynaticiGorunumu.player = p
        oturum = try {
            MediaSession.Builder(this, KanalOynatici(p)).setId("egea_tv").build()
        } catch (e: Exception) {
            null
        }
    }

    private fun oynat(i: Int) {
        if (kanallar.isEmpty()) return
        indeks = ((i % kanallar.size) + kanallar.size) % kanallar.size
        val k = kanallar[indeks]
        val p = oynatici ?: return
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent(k.basliklar["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36")
        val digerBasliklar = k.basliklar.filterKeys { it != "User-Agent" }
        if (digerBasliklar.isNotEmpty()) http.setDefaultRequestProperties(digerBasliklar)
        val oge = MediaItem.Builder()
            .setUri(k.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(k.ad).setStation(k.ad).build())
            .build()
        val kaynak = if (k.url.contains(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(http).createMediaSource(oge)
        } else {
            DefaultMediaSourceFactory(http).createMediaSource(oge)
        }
        p.setMediaSource(kaynak)
        p.prepare()
        p.playWhenReady = true
        kanalAdi.text = k.ad
        bilgi.text = "Bağlanıyor…"
        AsistanApp.ornek.ayarlar.sonKanal = k.url
        listeAdaptoru.notifyDataSetChanged()
        kanalListesi.scrollToPosition(indeks)
        arayuzGoster(true)
    }

    private fun kanalDegistir(fark: Int) {
        ana.post { oynat(indeks + fark) }
    }

    // ---------------------------------------------------------------- arayüz
    private fun kur(): View {
        arayuz = FrameLayout(this)
        arayuz.setBackgroundColor(Color.BLACK)
        oynaticiGorunumu = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            setKeepContentOnPlayerReset(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }
        arayuz.addView(oynaticiGorunumu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val dokunma = View(this)
        dokunma.setOnClickListener { arayuzGoster(ustCubuk.visibility != View.VISIBLE) }
        arayuz.addView(dokunma, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        ustCubuk = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Tema.SEFFAF_SIYAH)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        ustCubuk.addView(simge(R.drawable.ic_kapat) { finish() })
        val yazilar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        kanalAdi = TextView(this).apply {
            textSize = 24f
            setTextColor(Tema.YAZI)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        bilgi = TextView(this).apply {
            textSize = 15f
            setTextColor(Tema.SOLUK)
            maxLines = 2
        }
        yazilar.addView(kanalAdi)
        yazilar.addView(bilgi)
        ustCubuk.addView(yazilar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ustCubuk.addView(TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 22f
            setTextColor(Tema.YAZI)
            setPadding(0, 0, dp(14), 0)
        })
        ustCubuk.addView(simge(R.drawable.ic_onceki) { oynat(indeks - 1) })
        ustCubuk.addView(simge(R.drawable.ic_sonraki) { oynat(indeks + 1) })
        ustCubuk.addView(simge(R.drawable.ic_liste) {
            listePaneli.visibility = if (listePaneli.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            arayuzGoster(true)
        })
        arayuz.addView(ustCubuk, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        listePaneli = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6101419.toInt())
            visibility = View.GONE
        }
        kanalListesi = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@TvActivity)
            setHasFixedSize(true)
            adapter = listeAdaptoru
        }
        listePaneli.addView(kanalListesi, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        arayuz.addView(listePaneli, FrameLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END).apply { topMargin = dp(84) })

        kilitPerdesi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isClickable = true
        }
        kilitPerdesi.addView(TextView(this).apply {
            text = "Sürüş sırasında görüntü kapalı"
            textSize = 30f
            setTextColor(Tema.YAZI)
            gravity = Gravity.CENTER
        })
        kilitPerdesi.addView(TextView(this).apply {
            text = "Ses devam ediyor. Araç durunca görüntü geri gelir.\n(Ayarlar > TV > Sürüş kilidi)"
            textSize = 18f
            setTextColor(Tema.SOLUK)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
        kilitPerdesi.setOnClickListener { finish() }
        arayuz.addView(kilitPerdesi, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return arayuz
    }

    private fun simge(ikon: Int, tikla: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(ikon)
        renklendir(Tema.YAZI)
        background = dalgali(0x33FFFFFF, dpF(26f))
        setPadding(dp(13), dp(13), dp(13), dp(13))
        layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(6) }
        setOnClickListener { tikla() }
    }

    private fun arayuzGoster(goster: Boolean, otomatikGizle: Boolean = true) {
        ana.removeCallbacks(gizle)
        ustCubuk.visibility = if (goster) View.VISIBLE else View.GONE
        if (!goster) listePaneli.visibility = View.GONE
        if (goster && otomatikGizle) ana.postDelayed(gizle, 5000L)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
            oynat(indeks + 1)
            true
        }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
            oynat(indeks - 1)
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private inner class KanalListesiAdaptoru : RecyclerView.Adapter<KanalListesiAdaptoru.Satir>() {
        inner class Satir(val kok: LinearLayout, val logo: ImageView, val ad: TextView) : RecyclerView.ViewHolder(kok)

        override fun getItemCount(): Int = kanallar.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Satir {
            val kok = LinearLayout(this@TvActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
                isClickable = true
            }
            val logo = ImageView(this@TvActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                yuvarlakKirp(dpF(8f))
            }
            kok.addView(logo, LinearLayout.LayoutParams(dp(72), dp(44)))
            val ad = TextView(this@TvActivity).apply {
                textSize = 18f
                setTextColor(Tema.YAZI)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(14), 0, 0, 0)
            }
            kok.addView(ad, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return Satir(kok, logo, ad)
        }

        override fun onBindViewHolder(h: Satir, position: Int) {
            val k = kanallar[position]
            h.ad.text = k.ad
            h.ad.setTextColor(if (position == indeks) Tema.VURGU2 else Tema.YAZI)
            h.kok.background = dalgali(if (position == indeks) 0x3300BFA5 else 0x00000000, dpF(10f))
            h.kok.setOnClickListener { oynat(h.bindingAdapterPosition) }
            GorselYukleyici.yukle(h.logo, k.logo, dp(72), BasHarfCizimi(k.ad, dp(44)))
        }
    }
}
