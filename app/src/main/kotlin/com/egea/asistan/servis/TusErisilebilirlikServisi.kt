package com.egea.asistan.servis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.egea.asistan.AsistanApp
import com.egea.asistan.ui.ListenActivity

/**
 * Opsiyonel: Ayarlar'da seçilen tuş kodu (direksiyondaki sesli komut tuşu) gelince dinleme ekranını açar.
 * Başka tuşlara dokunmaz; sadece tanı/öğrenme ekranlarına bildirir.
 */
class TusErisilebilirlikServisi : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "Erişilebilirlik bayrakları ayarlanamadı", e)
        }
        bagli = true
        Log.i(AsistanApp.ETIKET, "Tuş erişilebilirlik servisi bağlandı")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bagli = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        TusOlaylari.yayinla(event, "erişilebilirlik")
        val kod = AsistanApp.ornek.ayarlar.tetikTusKodu
        if (kod > 0 && event.keyCode == kod) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                try {
                    startActivity(
                        Intent(this, ListenActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    )
                } catch (e: Exception) {
                    Log.w(AsistanApp.ETIKET, "Dinleme ekranı açılamadı", e)
                }
            }
            return true
        }
        return false
    }

    companion object {
        @Volatile
        var bagli = false
            private set
    }
}
