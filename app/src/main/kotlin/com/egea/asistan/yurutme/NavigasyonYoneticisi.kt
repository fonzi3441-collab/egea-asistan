package com.egea.asistan.yurutme

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.core.Hedef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Navigasyon: Google Haritalar (google.navigation: ile doğrudan rota), Waze (navigate=yes),
 * Yandex Navigasyon (koordinat bulunursa build_route_on_map, yoksa map_search).
 */
class NavigasyonYoneticisi(private val ctx: Context) {
    data class NavUygulama(val kod: String, val ad: String, val paket: String)

    data class Hazirlik(val intent: Intent, val yontem: String)

    private val ayarlar get() = AsistanApp.ornek.ayarlar

    @Suppress("DEPRECATION")
    fun yukluMu(paket: String): Boolean = try {
        ctx.packageManager.getPackageInfo(paket, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Ayardaki uygulama yüklüyse o, değilse yüklü ilk uygulama (Google → Waze → Yandex). */
    fun secili(): NavUygulama? {
        val s = HEPSI.firstOrNull { it.kod == ayarlar.navUygulama }
        if (s != null && yukluMu(s.paket)) return s
        return HEPSI.firstOrNull { yukluMu(it.paket) }
    }

    /** Hedefin adres metni; ev/iş adresi kayıtlı değilse null. */
    fun adres(h: Hedef): String? = when (h) {
        Hedef.Ev -> ayarlar.evAdresi.takeIf { it.isNotBlank() }
        Hedef.Is -> ayarlar.isAdresi.takeIf { it.isNotBlank() }
        is Hedef.Adres -> h.gorunen.ifBlank { h.sorgu }
    }

    /** Geocoder ile koordinat (internet ve Google/AOSP konum servisi gerekir); bulunamazsa null. */
    suspend fun konum(adres: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(5000L) {
            try {
                if (!Geocoder.isPresent()) return@withTimeoutOrNull null
                @Suppress("DEPRECATION")
                val liste = Geocoder(ctx, Locale.forLanguageTag("tr-TR")).getFromLocationName(adres, 1)
                val a = liste?.firstOrNull() ?: return@withTimeoutOrNull null
                a.latitude to a.longitude
            } catch (e: Exception) {
                Log.w(AsistanApp.ETIKET, "Geocoder hatası", e)
                null
            }
        }
    }

    suspend fun hazirla(adres: String, rota: Boolean): Hazirlik {
        val u = secili()
        val q = Uri.encode(adres)
        val kod = u?.kod ?: "genel"
        val paket = u?.paket ?: ""
        return when (kod) {
            "google" -> {
                val uri = if (rota) "google.navigation:q=$q&mode=d" else "geo:0,0?q=$q"
                Hazirlik(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(paket), "Google Haritalar ($uri)")
            }
            "waze" -> {
                val k = if (rota) konum(adres) else null
                val uri = when {
                    k != null -> "https://waze.com/ul?ll=${k.first},${k.second}&navigate=yes"
                    rota -> "https://waze.com/ul?q=$q&navigate=yes"
                    else -> "https://waze.com/ul?q=$q"
                }
                Hazirlik(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(paket), "Waze ($uri)")
            }
            "yandex" -> {
                val k = if (rota) konum(adres) else null
                val uri = if (k != null) {
                    "yandexnavi://build_route_on_map?lat_to=${k.first}&lon_to=${k.second}"
                } else {
                    "yandexnavi://map_search?text=$q"
                }
                Hazirlik(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(paket), "Yandex ($uri)")
            }
            else -> {
                val uri = "geo:0,0?q=$q"
                Hazirlik(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), "genel geo: (navigasyon uygulaması bulunamadı)")
            }
        }.also { it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    companion object {
        val GOOGLE = NavUygulama("google", "Google Haritalar", "com.google.android.apps.maps")
        val WAZE = NavUygulama("waze", "Waze", "com.waze")
        val YANDEX = NavUygulama("yandex", "Yandex Navigasyon", "ru.yandex.yandexnavi")
        val HEPSI = listOf(GOOGLE, WAZE, YANDEX)
    }
}
