package com.egea.asistan.servis

import android.service.notification.NotificationListenerService
import android.util.Log
import com.egea.asistan.AsistanApp

/**
 * Bildirim erişimi için boş dinleyici. Bu servis etkin olunca MediaSessionManager.getActiveSessions
 * ile çalan müzik uygulamasına doğrudan komut gönderilebilir.
 */
class MedyaDinleyiciServisi : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(AsistanApp.ETIKET, "Bildirim dinleyici bağlandı")
    }
}
