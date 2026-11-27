package com.egea.asistan.servis

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.egea.asistan.AsistanApp
import com.egea.asistan.Izinler
import com.egea.asistan.R
import com.egea.asistan.ui.ListenActivity
import com.egea.asistan.uyandirma.UyandirmaDeposu
import com.egea.asistan.uyandirma.UyandirmaKontrol
import com.egea.asistan.uyandirma.UyandirmaMotoru
import kotlin.math.abs

/**
 * Asistanın arka plan servisi (ön plan servisi):
 *  - her uygulamanın üstünde duran, sürüklenebilir yüzen mikrofon butonu (SYSTEM_ALERT_WINDOW),
 *  - "Hey Egea" uyandırma kelimesi dinleme (mikrofon, internetsiz).
 * Dokununca / kelime duyulunca yarı saydam dinleme ekranı açılır.
 */
class YuzenButonServisi : Service() {
    private var wm: WindowManager? = null
    private var gorunum: View? = null
    private var motor: UyandirmaMotoru? = null
    private var mikrofonluOnPlan = false
    private val ana = Handler(Looper.getMainLooper())
    private var engelUyarildi = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        onPlanaGec(false)
        calisiyor = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == EYLEM_DURDUR) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Her startForegroundService çağrısından sonra startForeground yenilenmeli.
        onPlanaGec(mikrofonluOnPlan)
        val a = AsistanApp.ornek.ayarlar
        if (a.yuzenButon && Settings.canDrawOverlays(this)) balonuEkle() else balonuKaldir()
        uyandirmaGuncelle(intent?.getBooleanExtra(EXTRA_YENIDEN_YUKLE, false) == true)
        if (gorunum == null && motor == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ana.removeCallbacksAndMessages(null)
        motor?.durdur()
        motor = null
        UyandirmaKontrol.motor = null
        UyandirmaKontrol.durum = "kapalı"
        balonuKaldir()
        calisiyor = false
        super.onDestroy()
    }

    /** Hey Egea motorunu ayarlara göre başlatır/durdurur. [yeniden]: yeni şablonlarla yeniden başlat. */
    private fun uyandirmaGuncelle(yeniden: Boolean) {
        val a = AsistanApp.ornek.ayarlar
        val ister = a.uyandirma && Izinler.mikrofon(this) && UyandirmaDeposu.varMi(this)
        if (yeniden || !ister) {
            motor?.durdur()
            motor = null
            UyandirmaKontrol.motor = null
            if (!ister) {
                UyandirmaKontrol.durum = if (a.uyandirma) "izin ya da örnek eksik" else "kapalı"
                if (mikrofonluOnPlan) onPlanaGec(false)
            }
        }
        if (ister && motor == null) {
            if (!onPlanaGec(true)) {
                UyandirmaKontrol.durum = "arka planda başlatılamadı — Egea'yı bir kez açın"
                return
            }
            val m = UyandirmaMotoru(this) { dinlemeyiAc(uyandirma = true) }
            motor = m
            UyandirmaKontrol.motor = m
            m.baslat()
        }
    }

    /** Ön plana geçer; [mikrofon] true ise mikrofon türüyle. Mikrofon türü reddedilirse false döner. */
    private fun onPlanaGec(mikrofon: Boolean): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val kanal = NotificationChannel(KANAL, "Yüzen mikrofon", NotificationManager.IMPORTANCE_MIN)
            kanal.setShowBadge(false)
            nm.createNotificationChannel(kanal)
        }
        val ac = PendingIntent.getActivity(
            this, 0,
            Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val bildirim = bildirimKurucu()
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Egea Asistan")
            .setContentText(if (mikrofon) "“Hey Egea” dinleniyor • dokunarak konuşun" else "Konuşmak için dokunun")
            .setContentIntent(ac)
            .setOngoing(true)
            .build()
        return try {
            if (mikrofon && Build.VERSION.SDK_INT >= 30) {
                startForeground(
                    BILDIRIM_NO, bildirim,
                    (if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0) or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else if (Build.VERSION.SDK_INT >= 34) {
                startForeground(BILDIRIM_NO, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(BILDIRIM_NO, bildirim)
            }
            mikrofonluOnPlan = mikrofon
            true
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "startForeground başarısız (mikrofon=$mikrofon)", e)
            if (mikrofon) {
                onPlanaGec(false)
                false
            } else {
                false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun bildirimKurucu(): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, KANAL) else Notification.Builder(this)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    private fun balonuEkle() {
        if (gorunum != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(AsistanApp.ETIKET, "Üstte gösterme izni yok, balon eklenmedi")
            return
        }
        val pencere = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val boyut = dp(64)
        val v = FrameLayout(this)
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xE61565C0.toInt())
            setStroke(dp(2), 0x80FFFFFF.toInt())
        }
        val ikon = ImageView(this)
        ikon.setImageResource(R.drawable.ic_mic)
        v.addView(ikon, FrameLayout.LayoutParams(boyut * 6 / 10, boyut * 6 / 10, Gravity.CENTER))
        v.contentDescription = "Egea Asistan mikrofon"

        val tip = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val p = WindowManager.LayoutParams(
            boyut, boyut, tip,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        val ekran = resources.displayMetrics
        val ayarlar = AsistanApp.ornek.ayarlar
        p.x = if (ayarlar.balonX >= 0) ayarlar.balonX.coerceAtMost(ekran.widthPixels - boyut) else ekran.widthPixels - boyut - dp(12)
        p.y = if (ayarlar.balonY >= 0) ayarlar.balonY.coerceAtMost(ekran.heightPixels - boyut) else ekran.heightPixels / 2 - boyut / 2

        val esik = ViewConfiguration.get(this).scaledTouchSlop
        var basX = 0
        var basY = 0
        var dokunX = 0f
        var dokunY = 0f
        var suruklendi = false
        v.setOnTouchListener { gor, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    basX = p.x
                    basY = p.y
                    dokunX = e.rawX
                    dokunY = e.rawY
                    suruklendi = false
                    gor.alpha = 0.7f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - dokunX).toInt()
                    val dy = (e.rawY - dokunY).toInt()
                    if (!suruklendi && (abs(dx) > esik || abs(dy) > esik)) suruklendi = true
                    if (suruklendi) {
                        p.x = basX + dx
                        p.y = basY + dy
                        try {
                            pencere.updateViewLayout(gor, p)
                        } catch (ex: Exception) {
                            Log.w(AsistanApp.ETIKET, "Balon taşınamadı", ex)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gor.alpha = 1f
                    if (suruklendi) {
                        ayarlar.balonX = p.x
                        ayarlar.balonY = p.y
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        gor.performClick()
                        dinlemeyiAc()
                    }
                    true
                }
                else -> false
            }
        }
        try {
            pencere.addView(v, p)
            wm = pencere
            gorunum = v
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Balon eklenemedi", e)
        }
    }

    private fun balonuKaldir() {
        val v = gorunum ?: return
        try {
            wm?.removeView(v)
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Balon kaldırılamadı", e)
        }
        gorunum = null
    }

    private fun dinlemeyiAc(uyandirma: Boolean = false) {
        val istek = SystemClock.elapsedRealtime()
        try {
            startActivity(
                Intent(this, ListenActivity::class.java)
                    .putExtra(ListenActivity.EXTRA_UYANDIRMA, uyandirma)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Dinleme ekranı açılamadı", e)
            if (uyandirma) UyandirmaKontrol.devamEt(1000)
            return
        }
        if (!uyandirma) return
        // Android 10+ arka plandan ekran açmayı "üstte gösterme" izni yoksa hata vermeden engeller. Motor algılayınca
        // kendini duraklattığı ve ancak dinleme ekranı kapanınca devam ettiği için, ekran 3 sn içinde açılmadıysa
        // motoru yeniden başlat (sağır kalmasın) ve kullanıcıyı bir kez sesli uyar.
        ana.postDelayed({
            if (ListenActivity.sonAcilis < istek) {
                Log.w(AsistanApp.ETIKET, "Hey Egea: dinleme ekranı açılamadı (arka plandan açma engellenmiş olabilir)")
                UyandirmaKontrol.sonSorun = "dinleme ekranı açılamadı — “Üstte gösterme” izni verin"
                if (!engelUyarildi) {
                    engelUyarildi = true
                    AsistanApp.ornek.konusmaci.konus("Dinleme ekranı açılamadı. Ayarlardan üstte gösterme iznini verin.")
                }
                UyandirmaKontrol.devamEt(0)
            } else {
                UyandirmaKontrol.sonSorun = null
            }
        }, 3000)
    }

    companion object {
        const val KANAL = "yuzen_buton"
        const val EYLEM_DURDUR = "com.egea.asistan.YUZEN_BUTON_DURDUR"
        private const val EXTRA_YENIDEN_YUKLE = "yeniden_yukle"
        private const val BILDIRIM_NO = 1

        @Volatile
        var calisiyor = false
            private set

        /**
         * Yüzen buton (ayar açık + üstte gösterme izni) ya da Hey Egea (açık + mikrofon izni + örnek) gerekiyorsa
         * servisi başlatır/günceller. Ön plandaki bir ekrandan çağrılırsa mikrofon kısıtına takılmaz.
         */
        fun uygunsaBaslat(ctx: Context, yenidenYukle: Boolean = false) {
            val a = AsistanApp.ornek.ayarlar
            val balon = a.yuzenButon && Settings.canDrawOverlays(ctx)
            val uyandirma = a.uyandirma && Izinler.mikrofon(ctx) && UyandirmaDeposu.varMi(ctx)
            if (!balon && !uyandirma && !calisiyor) return
            try {
                ContextCompat.startForegroundService(
                    ctx, Intent(ctx, YuzenButonServisi::class.java).putExtra(EXTRA_YENIDEN_YUKLE, yenidenYukle)
                )
            } catch (e: Exception) {
                Log.w(AsistanApp.ETIKET, "Asistan servisi başlatılamadı", e)
            }
        }

        fun durdur(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, YuzenButonServisi::class.java))
            } catch (e: Exception) {
                Log.w(AsistanApp.ETIKET, "Servis durdurulamadı", e)
            }
        }
    }
}
