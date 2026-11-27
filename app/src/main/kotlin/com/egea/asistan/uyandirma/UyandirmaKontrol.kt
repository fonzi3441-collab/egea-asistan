package com.egea.asistan.uyandirma

import android.os.Handler
import android.os.Looper

/** Uyandırma motorunun durumu ve duraklat/devam kontrolü (aynı süreçte, IPC'siz). */
object UyandirmaKontrol {
    @Volatile
    var motor: UyandirmaMotoru? = null

    @Volatile
    var durum: String = "kapalı"

    @Volatile
    var sonTetik: Long = 0

    /** Son konuşma parçasının eşiğe oranı (1 ve altı algılanır). */
    @Volatile
    var sonOran: Float = Float.NaN

    /** Söz başına eşikler (tanı ekranı için metin). */
    @Volatile
    var esikOzeti: String = "-"

    /** Bozuk göründüğü için (diğerlerinden çok uzun) kullanılmayan öğretme kaydı sayısı. */
    @Volatile
    var ayiklanan: Int = 0

    @Volatile
    var seviyeDb: Float = -100f

    @Volatile
    var tetikSayisi: Int = 0

    /** Son algılamada yaşanan sorun (tanı ekranı için; ör. ekran arka plandan açılamadı). */
    @Volatile
    var sonSorun: String? = null

    private val ana = Handler(Looper.getMainLooper())
    private val devamGorevi = Runnable { motor?.devamEt() }

    /** Mikrofonu hemen bırakır (dinleme ekranı/öğretme ekranı açılınca). */
    fun duraklat() {
        ana.removeCallbacks(devamGorevi)
        motor?.duraklat()
    }

    /** Uyandırma motoru mikrofonu tutmuyor mu (Android 8/9 aynı anda iki kayda izin vermez). */
    fun mikrofonBos(): Boolean = motor?.mikrofonAcik != true

    /** Biraz bekleyip (konuşma tanıma mikrofonu bıraksın) yeniden dinlemeye başlar. */
    fun devamEt(gecikmeMs: Long = 800L) {
        ana.removeCallbacks(devamGorevi)
        ana.postDelayed(devamGorevi, gecikmeMs)
    }
}
