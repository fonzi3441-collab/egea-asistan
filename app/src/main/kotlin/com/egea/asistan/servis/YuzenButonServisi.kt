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
import android.os.IBinder
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
import com.egea.asistan.R
import com.egea.asistan.ui.ListenActivity
import kotlin.math.abs

/**
 * Her uygulamanın üstünde duran, sürüklenebilir yüzen mikrofon butonu (SYSTEM_ALERT_WINDOW +
 * ön plan servisi). Dokununca yarı saydam dinleme ekranı açılır.
 */
class YuzenButonServisi : Service() {
    private var wm: WindowManager? = null
    private var gorunum: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        onPlanaGec()
        balonuEkle()
        calisiyor = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == EYLEM_DURDUR) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (gorunum == null) balonuEkle()
        return START_STICKY
    }

    override fun onDestroy() {
        balonuKaldir()
        calisiyor = false
        super.onDestroy()
    }

    private fun onPlanaGec() {
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
            .setContentText("Konuşmak için dokunun")
            .setContentIntent(ac)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(BILDIRIM_NO, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(BILDIRIM_NO, bildirim)
            }
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "startForeground başarısız", e)
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

    private fun dinlemeyiAc() {
        try {
            startActivity(
                Intent(this, ListenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Dinleme ekranı açılamadı", e)
        }
    }

    companion object {
        const val KANAL = "yuzen_buton"
        const val EYLEM_DURDUR = "com.egea.asistan.YUZEN_BUTON_DURDUR"
        private const val BILDIRIM_NO = 1

        @Volatile
        var calisiyor = false
            private set

        /** Ayar açıksa ve üstte gösterme izni varsa servisi başlatır. */
        fun uygunsaBaslat(ctx: Context) {
            if (!AsistanApp.ornek.ayarlar.yuzenButon) return
            if (!Settings.canDrawOverlays(ctx)) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, YuzenButonServisi::class.java))
            } catch (e: Exception) {
                Log.w(AsistanApp.ETIKET, "Yüzen buton servisi başlatılamadı", e)
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
