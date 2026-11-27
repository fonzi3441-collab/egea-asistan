package com.egea.asistan.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Build
import android.view.Choreographer
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import com.egea.asistan.core.TurkceMetin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Yeni arayüzün renkleri (koyu, yüksek kontrast, araç içi). */
object Tema {
    val ARKA = 0xFF0B0E13.toInt()
    val RAY = 0xFF10151C.toInt()
    val KART = 0xFF161C25.toInt()
    val KART2 = 0xFF1E2630.toInt()
    val CIZGI = 0xFF263040.toInt()
    val VURGU = 0xFF2E86F5.toInt()
    val VURGU2 = 0xFF00BFA5.toInt()
    val YAZI = 0xFFF2F5F9.toInt()
    val SOLUK = 0xFF8D98A8.toInt()
    val TURUNCU = 0xFFFFA726.toInt()
    val KIRMIZI = 0xFFEF5350.toInt()
    val YESIL = 0xFF43C66E.toInt()
    val SEFFAF_SIYAH = 0xCC000000.toInt()

    val KUTU_RENKLERI = intArrayOf(
        0xFF2E86F5.toInt(), 0xFF00BFA5.toInt(), 0xFFAB47BC.toInt(), 0xFFFF7043.toInt(),
        0xFF26A69A.toInt(), 0xFF5C6BC0.toInt(), 0xFFEC407A.toInt(), 0xFF7E57C2.toInt()
    )
}

fun Context.dpF(v: Float): Float = v * resources.displayMetrics.density

/** Dokununca dalga efekti veren yuvarlak köşeli arka plan (donanım hızlandırmalı). */
fun dalgali(renk: Int, yaricap: Float, kenar: Int = 0, kenarRenk: Int = 0): Drawable {
    val icerik = GradientDrawable().apply {
        setColor(renk)
        cornerRadius = yaricap
        if (kenar > 0) setStroke(kenar, kenarRenk)
    }
    val maske = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = yaricap
    }
    return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), icerik, maske)
}

fun gecisli(renk1: Int, renk2: Int, yaricap: Float): Drawable {
    val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(renk1, renk2))
    g.cornerRadius = yaricap
    val maske = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = yaricap
    }
    return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), g, maske)
}

fun ImageView.renklendir(renk: Int) {
    imageTintList = ColorStateList.valueOf(renk)
}

/** Görünümü yuvarlak köşeli kırp (logolar için; GPU'da yapılır). */
fun View.yuvarlakKirp(yaricap: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, yaricap)
        }
    }
    clipToOutline = true
}

/** Logo yoksa: adın baş harfleri renkli zemin üstünde. */
class BasHarfCizimi(ad: String, private val boyutPx: Int) : ShapeDrawable(OvalShape()) {
    private val harfler: String
    private val yaziBoya = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val kelimeler = ad.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        harfler = kelimeler.take(2).joinToString("") { TurkceMetin.basHarfBuyuk(it.take(1)) }.ifEmpty { "?" }
        paint.color = Tema.KUTU_RENKLERI[abs(ad.hashCode()) % Tema.KUTU_RENKLERI.size]
        yaziBoya.color = Color.WHITE
        yaziBoya.typeface = Typeface.DEFAULT_BOLD
        yaziBoya.textAlign = Paint.Align.CENTER
        intrinsicWidth = boyutPx
        intrinsicHeight = boyutPx
    }

    override fun draw(canvas: android.graphics.Canvas) {
        super.draw(canvas)
        val b = bounds
        yaziBoya.textSize = b.height() * 0.38f
        val y = b.exactCenterY() - (yaziBoya.descent() + yaziBoya.ascent()) / 2
        canvas.drawText(harfler, b.exactCenterX(), y, yaziBoya)
    }
}

/** Ekranın desteklediği en yüksek yenileme hızını ister (60 Hz üstü destekleyen ekranlarda daha akıcı). */
@Suppress("DEPRECATION")
fun Activity.enYuksekYenileme() {
    try {
        val ekran = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        val simdiki = ekran?.mode ?: return
        val enIyi = ekran.supportedModes
            .filter { it.physicalWidth == simdiki.physicalWidth && it.physicalHeight == simdiki.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val lp = window.attributes
        lp.preferredDisplayModeId = enIyi.modeId
        window.attributes = lp
    } catch (e: Exception) {
        // bazı teyplerde desteklenmeyebilir
    }
}

/** Ekranın anlık yenileme hızı ve desteklenen modlar (tanı ekranı için). */
@Suppress("DEPRECATION")
fun Activity.yenilemeBilgisi(): String = try {
    val ekran = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
    val modlar = ekran?.supportedModes?.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${"%.0f".format(it.refreshRate)}" } ?: "?"
    "anlık ${"%.1f".format(ekran?.refreshRate ?: 0f)} Hz • modlar: $modlar"
} catch (e: Exception) {
    "?"
}

/** Choreographer ile saniyedeki kare sayısını ölçer. */
class FpsOlcer(private val guncelle: (Int) -> Unit) : Choreographer.FrameCallback {
    private var calisiyor = false
    private var kare = 0
    private var baslangic = 0L

    fun baslat() {
        if (calisiyor) return
        calisiyor = true
        kare = 0
        baslangic = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun durdur() {
        calisiyor = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!calisiyor) return
        if (baslangic == 0L) baslangic = frameTimeNanos
        kare++
        val gecen = frameTimeNanos - baslangic
        if (gecen >= 1_000_000_000L) {
            guncelle((kare * 1_000_000_000L / gecen).toInt())
            kare = 0
            baslangic = frameTimeNanos
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}

/** Kalın başlık. */
fun TextView.kalin(): TextView {
    setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
    return this
}

/**
 * Sistem dili ne olursa olsun Türkçe tarih ("27 Kasım 2026, Cuma"). Çin malı teyplerin çoğu İngilizce/Çince
 * dille geldiği için TextClock (sistem dilini kullanır) yerine bu kullanılır. Pencereye bağlıyken her dakika
 * başında kendini yeniler; saat dilimi değişirse bir sonraki yenilemede düzelir.
 */
class TurkceTarih(ctx: Context) : TextView(ctx) {
    private val bicim = SimpleDateFormat("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR"))
    private val yenile = object : Runnable {
        override fun run() {
            bicim.timeZone = TimeZone.getDefault()
            text = bicim.format(Date())
            val simdi = System.currentTimeMillis()
            postDelayed(this, 60_000L - simdi % 60_000L + 50L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(yenile)
        yenile.run()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(yenile)
        super.onDetachedFromWindow()
    }
}
