package com.egea.asistan.medya

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.egea.asistan.Izinler

/**
 * GPS hızından aracın hareket edip etmediğini bulur (TV sürüş kilidi için).
 * 10 km/s üstü iki ölçümde "hareketli", 5 km/s altı üç ölçümde "duruyor".
 */
class HizIzleyici(private val ctx: Context, private val degisti: (Boolean) -> Unit) : LocationListener {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var hareketli = false
        private set

    var sonHizKmS = -1f
        private set

    private var hizli = 0
    private var yavas = 0

    @SuppressLint("MissingPermission")
    fun baslat(): Boolean {
        if (!Izinler.konum(ctx)) return false
        return try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun durdur() {
        try {
            lm.removeUpdates(this)
        } catch (e: Exception) {
            // yok say
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!location.hasSpeed()) return
        val kmh = location.speed * 3.6f
        sonHizKmS = kmh
        if (kmh > 10f) {
            hizli++
            yavas = 0
        } else if (kmh < 5f) {
            yavas++
            hizli = 0
        }
        val yeni = when {
            !hareketli && hizli >= 2 -> true
            hareketli && yavas >= 3 -> false
            else -> hareketli
        }
        if (yeni != hareketli) {
            hareketli = yeni
            degisti(yeni)
        }
    }

    // Eski Android sürümlerinde bu yöntemler soyut; uygulanmazsa çökme olur.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}
}
