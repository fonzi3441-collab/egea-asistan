package com.egea.asistan.servis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.egea.asistan.AsistanApp

/** Teyp açılınca (BOOT_COMPLETED / hızlı açılış) ve uygulama güncellenince yüzen butonu başlatır. */
class AcilisAlicisi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(AsistanApp.ETIKET, "Açılış yayını: ${intent.action}")
        YuzenButonServisi.uygunsaBaslat(context)
    }
}
