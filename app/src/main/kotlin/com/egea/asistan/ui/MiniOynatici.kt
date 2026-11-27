package com.egea.asistan.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.egea.asistan.R
import com.egea.asistan.medya.GorselYukleyici
import com.egea.asistan.medya.RadyoDurumu
import com.egea.asistan.medya.RadyoKontrol
import kotlinx.coroutines.launch

/** Ana ekranın altındaki şimdi çalan radyo çubuğu (önceki / oynat-duraklat / sonraki). */
class MiniOynatici(private val akt: ComponentActivity, private val tikla: () -> Unit) : Player.Listener {
    val gorunum: LinearLayout = LinearLayout(akt)
    private val logo = ImageView(akt)
    private val baslik = TextView(akt)
    private val alt = TextView(akt)
    private val oynat = ImageView(akt)
    private var kontrolcu: MediaController? = null
    private val logoPx = akt.dp(56)

    init {
        gorunum.orientation = LinearLayout.HORIZONTAL
        gorunum.gravity = Gravity.CENTER_VERTICAL
        gorunum.setBackgroundColor(Tema.KART)
        gorunum.setPadding(akt.dp(16), akt.dp(10), akt.dp(16), akt.dp(10))
        gorunum.visibility = View.GONE
        gorunum.isClickable = true
        gorunum.setOnClickListener { tikla() }

        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        logo.setBackgroundColor(Tema.KART2)
        logo.yuvarlakKirp(akt.dpF(12f))
        gorunum.addView(logo, LinearLayout.LayoutParams(logoPx, logoPx))

        val yazilar = LinearLayout(akt)
        yazilar.orientation = LinearLayout.VERTICAL
        yazilar.setPadding(akt.dp(16), 0, akt.dp(8), 0)
        baslik.textSize = 19f
        baslik.setTextColor(Tema.YAZI)
        baslik.setTypeface(baslik.typeface, Typeface.BOLD)
        baslik.maxLines = 1
        baslik.ellipsize = TextUtils.TruncateAt.END
        alt.textSize = 15f
        alt.setTextColor(Tema.SOLUK)
        alt.maxLines = 1
        alt.ellipsize = TextUtils.TruncateAt.MARQUEE
        alt.isSelected = true
        yazilar.addView(baslik)
        yazilar.addView(alt)
        gorunum.addView(yazilar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        gorunum.addView(dugme(R.drawable.ic_onceki, 56) { kontrolcu?.seekToPreviousMediaItem() })
        oynat.setImageResource(R.drawable.ic_oynat)
        oynat.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Tema.VURGU)
        }
        oynat.setPadding(akt.dp(14), akt.dp(14), akt.dp(14), akt.dp(14))
        oynat.setOnClickListener {
            val k = kontrolcu ?: return@setOnClickListener
            if (k.isPlaying || k.playWhenReady) k.pause() else {
                if (k.playbackState == Player.STATE_IDLE) k.prepare()
                k.play()
            }
        }
        gorunum.addView(oynat, LinearLayout.LayoutParams(akt.dp(62), akt.dp(62)).apply {
            marginStart = akt.dp(8)
            marginEnd = akt.dp(8)
        })
        gorunum.addView(dugme(R.drawable.ic_sonraki, 56) { kontrolcu?.seekToNextMediaItem() })
    }

    private fun dugme(ikon: Int, boyutDp: Int, tikla: () -> Unit): ImageView {
        val iv = ImageView(akt)
        iv.setImageResource(ikon)
        iv.renklendir(Tema.YAZI)
        iv.background = dalgali(Tema.KART, akt.dpF(boyutDp / 2f))
        iv.setPadding(akt.dp(14), akt.dp(14), akt.dp(14), akt.dp(14))
        iv.layoutParams = LinearLayout.LayoutParams(akt.dp(boyutDp), akt.dp(boyutDp))
        iv.setOnClickListener { tikla() }
        return iv
    }

    fun baglan() {
        akt.lifecycleScope.launch {
            try {
                val k = RadyoKontrol.kontrolcu(akt)
                kontrolcu = k
                k.addListener(this@MiniOynatici)
                guncelle()
            } catch (e: Exception) {
                gorunum.visibility = View.GONE
            }
        }
    }

    fun ayir() {
        kontrolcu?.removeListener(this)
        kontrolcu = null
    }

    override fun onEvents(player: Player, events: Player.Events) {
        guncelle()
    }

    private fun guncelle() {
        val k = kontrolcu
        if (k == null || k.mediaItemCount == 0) {
            gorunum.visibility = View.GONE
            return
        }
        gorunum.visibility = View.VISIBLE
        val oge = k.currentMediaItem
        val istasyon = (oge?.mediaMetadata?.station ?: oge?.mediaMetadata?.title ?: "Radyo").toString()
        val parca = k.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() && it != istasyon }
        baslik.text = istasyon
        alt.text = when {
            parca != null -> parca
            k.isPlaying -> "Canlı yayın"
            k.playbackState == Player.STATE_BUFFERING -> "Bağlanıyor…"
            RadyoDurumu.hata != null && !k.isPlaying -> "Yayına bağlanılamadı — sonraki istasyonu deneyin"
            else -> "Duraklatıldı"
        }
        oynat.setImageResource(if (k.playWhenReady) R.drawable.ic_duraklat else R.drawable.ic_oynat)
        GorselYukleyici.yukle(logo, oge?.mediaMetadata?.artworkUri?.toString(), logoPx, BasHarfCizimi(istasyon, logoPx))
    }
}
