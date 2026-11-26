package com.egea.asistan.servis

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList

/** Tuş olaylarını (erişilebilirlik servisinden ve ekranlardan) tanı/ayar ekranlarına iletir. */
object TusOlaylari {
    data class Olay(val zaman: Long, val kod: Int, val eylem: Int, val tarama: Int, val kaynak: String, val cihaz: Int)

    fun interface Dinleyici {
        fun olay(o: Olay)
    }

    private val dinleyiciler = CopyOnWriteArrayList<Dinleyici>()
    private val ana = Handler(Looper.getMainLooper())

    fun ekle(d: Dinleyici) {
        dinleyiciler += d
    }

    fun cikar(d: Dinleyici) {
        dinleyiciler -= d
    }

    fun olayYap(e: KeyEvent, kaynak: String) = Olay(System.currentTimeMillis(), e.keyCode, e.action, e.scanCode, kaynak, e.deviceId)

    fun yayinla(e: KeyEvent, kaynak: String) {
        if (dinleyiciler.isEmpty()) return
        val o = olayYap(e, kaynak)
        ana.post { for (d in dinleyiciler) d.olay(o) }
    }
}
