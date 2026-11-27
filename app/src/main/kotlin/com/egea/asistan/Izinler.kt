package com.egea.asistan

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.egea.asistan.servis.TusErisilebilirlikServisi

/** İzin ve özel erişim durumları. */
object Izinler {
    fun verildi(ctx: Context, izin: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, izin) == PackageManager.PERMISSION_GRANTED

    fun mikrofon(ctx: Context) = verildi(ctx, Manifest.permission.RECORD_AUDIO)
    fun kisiler(ctx: Context) = verildi(ctx, Manifest.permission.READ_CONTACTS)
    fun arama(ctx: Context) = verildi(ctx, Manifest.permission.CALL_PHONE)
    fun telefonDurumu(ctx: Context) = verildi(ctx, Manifest.permission.READ_PHONE_STATE)

    fun konum(ctx: Context) = verildi(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
        verildi(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun bildirimGonderme(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || verildi(ctx, Manifest.permission.POST_NOTIFICATIONS)

    fun ustteGosterme(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun bildirimErisimi(ctx: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    } catch (e: Exception) {
        false
    }

    /** Bilinmeyen kaynaklardan kurulum izni; Android 8 öncesinde genel ayar okunamazsa null. */
    @Suppress("DEPRECATION")
    fun bilinmeyenKaynak(ctx: Context): Boolean? = if (Build.VERSION.SDK_INT >= 26) {
        ctx.packageManager.canRequestPackageInstalls()
    } else {
        try {
            Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) == 1
        } catch (e: Exception) {
            null
        }
    }

    fun pilMuaf(ctx: Context): Boolean = try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) {
        false
    }

    fun erisilebilirlik(ctx: Context): Boolean {
        val s = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: Exception) {
            null
        } ?: return false
        val bilesen = ComponentName(ctx, TusErisilebilirlikServisi::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == bilesen }
    }

    /** Varsayılan asistan uygulaması biz miyiz? Bazı sürümlerde okunamaz (null). */
    fun varsayilanAsistan(ctx: Context): Boolean? = try {
        Settings.Secure.getString(ctx.contentResolver, "assistant")?.contains(ctx.packageName)
    } catch (e: Exception) {
        null
    }
}
