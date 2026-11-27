package com.egea.asistan.servis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.medya.RadyoKatalogu
import com.egea.asistan.medya.RadyoKontrol
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Teyp açılınca (BOOT_COMPLETED / hızlı açılış) ve uygulama güncellenince yüzen butonu başlatır. Ayar açıksa
 * açılışta son dinlenen radyoyu çalmaya başlar (araç radyosu gibi); internet henüz yoksa radyo servisi bağlantı
 * gelene kadar yeniden dener.
 */
class AcilisAlicisi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(AsistanApp.ETIKET, "Açılış yayını: ${intent.action}")
        YuzenButonServisi.uygunsaBaslat(context)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!AsistanApp.ornek.ayarlar.acilistaRadyo) return
        val son = RadyoKatalogu.sonIstasyon() ?: return
        val sonuc = goAsync()
        val uc = context.applicationContext
        AsistanApp.ornek.kapsam.launch {
            try {
                withTimeout(9_000) { RadyoKontrol.cal(uc, listOf(son), 0) }
                Log.i(AsistanApp.ETIKET, "Açılışta son radyo çalınıyor: ${son.gorunenAd}")
            } catch (e: Exception) {
                Log.w(AsistanApp.ETIKET, "Açılışta radyo başlatılamadı", e)
            } finally {
                sonuc.finish()
            }
        }
    }
}
