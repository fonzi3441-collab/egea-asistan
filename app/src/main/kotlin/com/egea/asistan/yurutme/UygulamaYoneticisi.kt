package com.egea.asistan.yurutme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import com.egea.asistan.core.TurkceMetin
import com.egea.asistan.core.UygulamaEslestirici
import com.egea.asistan.ui.DiagnosticsActivity
import com.egea.asistan.ui.SettingsActivity

/** Yüklü uygulamaları adıyla bulup açar ("YouTube'u aç", "haritayı aç", "radyoyu aç"). */
class UygulamaYoneticisi(private val ctx: Context) {
    data class Bulunan(val etiket: String, val intent: Intent, val yontem: String)

    private val nav = NavigasyonYoneticisi(ctx)
    private val muzik = MuzikYoneticisi(ctx, MedyaYoneticisi(ctx))

    fun bul(ad: String): Bulunan? {
        val k = TurkceMetin.kucukHarf(ad).trim()
        val pm = ctx.packageManager
        // Rol takma adları: seçili navigasyon/müzik uygulaması, sistem ayarları, kendi ekranlarımız
        when (k) {
            "harita", "haritalar", "navigasyon", "navi", "maps", "yol tarifi" -> nav.secili()?.let { u ->
                pm.getLaunchIntentForPackage(u.paket)?.let { return Bulunan(u.ad, it, "seçili navigasyon (${u.paket})") }
            }
            "müzik", "müzik çalar" -> muzik.secili()?.let { u ->
                pm.getLaunchIntentForPackage(u.paket)?.let { return Bulunan(u.ad, it, "seçili müzik (${u.paket})") }
            }
            "ayarlar", "ayar", "sistem ayarları" -> return Bulunan("Ayarlar", Intent(Settings.ACTION_SETTINGS), "Settings.ACTION_SETTINGS")
            "egea", "egea ayarları", "asistan ayarları", "egea asistan ayarları", "asistan" ->
                return Bulunan("Egea Ayarlar", Intent(ctx, SettingsActivity::class.java), "kendi ayarlar ekranı")
            "tanı", "tanı ekranı", "teşhis" -> return Bulunan("Tanı ekranı", Intent(ctx, DiagnosticsActivity::class.java), "kendi tanı ekranı")
        }
        val u = UygulamaEslestirici.bul(k, baslatilabilirler(ctx)) ?: return null
        val i = pm.getLaunchIntentForPackage(u.paket) ?: return null
        return Bulunan(u.etiket, i, "bulanık eşleşme (${u.paket})")
    }

    fun ac(aktivite: Activity, b: Bulunan): String {
        b.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        aktivite.startActivity(b.intent)
        return b.yontem
    }

    companion object {
        @Volatile
        private var onbellek: List<UygulamaEslestirici.Uygulama> = emptyList()

        @Volatile
        private var onbellekZamani = 0L

        /** Başlatıcıda görünen uygulamalar (etiket + paket). 60 sn önbelleklenir. */
        @Suppress("DEPRECATION")
        fun baslatilabilirler(ctx: Context): List<UygulamaEslestirici.Uygulama> {
            val simdi = SystemClock.elapsedRealtime()
            if (onbellek.isNotEmpty() && simdi - onbellekZamani < 60_000L) return onbellek
            val pm = ctx.packageManager
            val liste = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { UygulamaEslestirici.Uygulama(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                    .filter { it.paket != ctx.packageName }
                    .distinctBy { it.paket }
            } catch (e: Exception) {
                emptyList()
            }
            onbellek = liste
            onbellekZamani = simdi
            return liste
        }

        fun etiketler(ctx: Context): List<String> = baslatilabilirler(ctx).map { it.etiket }
    }
}
