package com.egea.asistan.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Koyu tema renkleri (1280x720 yatay teyp ekranı için yüksek kontrast). */
object Renk {
    val ARKA = 0xFF0E1116.toInt()
    val PANEL = 0xFF1A1F27.toInt()
    val PANEL2 = 0xFF232A35.toInt()
    val VURGU = 0xFF2E86F5.toInt()
    val YESIL = 0xFF2EAD5B.toInt()
    val KIRMIZI = 0xFFD9534F.toInt()
    val TURUNCU = 0xFFE69A28.toInt()
    val YAZI = 0xFFF2F4F7.toInt()
    val SOLUK = 0xFF9AA4B2.toInt()
    val SEFFAF_PANEL = 0xEE161B22.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun yuvarlak(renk: Int, yaricap: Float, kenar: Int = 0, kenarRenk: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(renk)
        cornerRadius = yaricap
        if (kenar > 0) setStroke(kenar, kenarRenk)
    }

/** Programla arayüz kurmak için küçük yardımcı (XML yerine; büyük butonlu, koyu tema). */
class Kurucu(val ctx: Context, val kok: LinearLayout) {

    private fun ekle(v: View, ust: Int = 6, alt: Int = 6): View {
        val lp = LinearLayout.LayoutParams(
            if (kok.orientation == LinearLayout.VERTICAL) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = ctx.dp(ust)
        lp.bottomMargin = ctx.dp(alt)
        if (kok.orientation == LinearLayout.HORIZONTAL) lp.marginEnd = ctx.dp(10)
        kok.addView(v, lp)
        return v
    }

    fun baslik(metin: String): TextView = (ekle(TextView(ctx).apply {
        text = metin
        textSize = 30f
        setTextColor(Renk.YAZI)
        setTypeface(typeface, Typeface.BOLD)
    }, 4, 10) as TextView)

    fun bolum(metin: String): TextView = (ekle(TextView(ctx).apply {
        text = metin
        textSize = 22f
        setTextColor(Renk.VURGU)
        setTypeface(typeface, Typeface.BOLD)
    }, 22, 6) as TextView)

    fun yazi(metin: String, boyut: Float = 18f, renk: Int = Renk.YAZI): TextView = (ekle(TextView(ctx).apply {
        text = metin
        textSize = boyut
        setTextColor(renk)
        setLineSpacing(0f, 1.1f)
    }) as TextView)

    fun buton(metin: String, renk: Int = Renk.VURGU, tikla: () -> Unit): Button = (ekle(Button(ctx).apply {
        text = metin
        textSize = 20f
        isAllCaps = false
        setTextColor(Renk.YAZI)
        background = yuvarlak(renk, ctx.dp(10).toFloat())
        minHeight = ctx.dp(62)
        minimumHeight = ctx.dp(62)
        setPadding(ctx.dp(22), ctx.dp(8), ctx.dp(22), ctx.dp(8))
        stateListAnimator = null
        setOnClickListener { tikla() }
    }) as Button)

    fun satir(blok: Kurucu.() -> Unit): LinearLayout {
        val s = LinearLayout(ctx)
        s.orientation = LinearLayout.HORIZONTAL
        s.gravity = Gravity.CENTER_VERTICAL
        Kurucu(ctx, s).blok()
        ekle(s)
        return s
    }

    fun girdi(ipucu: String, deger: String, tip: Int = InputType.TYPE_CLASS_TEXT, degisince: (String) -> Unit): EditText =
        (ekle(EditText(ctx).apply {
            hint = ipucu
            setText(deger)
            textSize = 20f
            setTextColor(Renk.YAZI)
            setHintTextColor(Renk.SOLUK)
            inputType = tip
            setSingleLine(true)
            minHeight = ctx.dp(56)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    degisince(s?.toString() ?: "")
                }
            })
        }) as EditText)

    fun secenekler(liste: List<Pair<String, String>>, secili: String, sec: (String) -> Unit): RadioGroup {
        val g = RadioGroup(ctx)
        g.orientation = RadioGroup.VERTICAL
        for ((i, s) in liste.withIndex()) {
            val r = RadioButton(ctx)
            r.id = View.generateViewId()
            r.text = s.second
            r.textSize = 19f
            r.setTextColor(Renk.YAZI)
            r.minHeight = ctx.dp(48)
            r.isChecked = s.first == secili
            r.tag = s.first
            g.addView(r)
            if (i == 0 && liste.none { it.first == secili }) r.isChecked = true
        }
        g.setOnCheckedChangeListener { grup, id ->
            val r = grup.findViewById<RadioButton>(id)
            (r?.tag as? String)?.let(sec)
        }
        ekle(g)
        return g
    }

    @Suppress("DEPRECATION")
    fun anahtar(metin: String, deger: Boolean, degisince: (Boolean) -> Unit): Switch = (ekle(Switch(ctx).apply {
        text = metin
        textSize = 20f
        setTextColor(Renk.YAZI)
        isChecked = deger
        minHeight = ctx.dp(56)
        setOnCheckedChangeListener { _, b -> degisince(b) }
    }) as Switch)

    fun ayrac() {
        val v = View(ctx)
        v.setBackgroundColor(Renk.PANEL2)
        kok.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(1)).apply {
            topMargin = ctx.dp(14)
            bottomMargin = ctx.dp(6)
        })
    }

    fun dikey(blok: Kurucu.() -> Unit): LinearLayout {
        val d = LinearLayout(ctx)
        d.orientation = LinearLayout.VERTICAL
        Kurucu(ctx, d).blok()
        ekle(d)
        return d
    }
}

/** Kaydırılabilir, kenar boşluklu dikey sayfa. */
fun Activity.sayfa(blok: Kurucu.() -> Unit): ScrollView {
    val kok = LinearLayout(this)
    kok.orientation = LinearLayout.VERTICAL
    kok.setPadding(dp(32), dp(20), dp(32), dp(40))
    Kurucu(this, kok).blok()
    val kaydir = ScrollView(this)
    kaydir.setBackgroundColor(Renk.ARKA)
    kaydir.isFillViewport = true
    kaydir.addView(kok)
    return kaydir
}
