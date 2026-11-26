package com.egea.asistan.yurutme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.egea.asistan.AsistanApp
import com.egea.asistan.Izinler

/**
 * Telefon araması için yöntem zinciri. Hangi yöntemin denendiği/çalıştığı loglanır:
 *  1) ACTION_CALL tel: (çözen activity varsa)
 *  2) TelecomManager.placeCall (Bluetooth HFP PhoneAccount varsa)
 *  3) ACTION_DIAL (numara hazırlanır, kullanıcı yeşil tuşa basar)
 *  4) Ayarlardan girilen özel intent şablonu (teybin Bluetooth uygulamasına göre)
 * Ayarlardan sıra değiştirilebilir ya da tek yönteme sabitlenebilir.
 */
class AramaYoneticisi(private val ctx: Context) {
    enum class Yontem(val kod: String) {
        CALL("ACTION_CALL"),
        TELECOM("Telecom.placeCall"),
        DIAL("ACTION_DIAL"),
        OZEL("Özel intent")
    }

    data class Sonuc(val yontem: Yontem?, val ayrinti: String) {
        val basarili get() = yontem != null
    }

    private val ayarlar get() = AsistanApp.ornek.ayarlar

    fun sira(): List<Yontem> = when (ayarlar.aramaYontemi) {
        "ozel_once" -> listOf(Yontem.OZEL, Yontem.CALL, Yontem.TELECOM, Yontem.DIAL)
        "call" -> listOf(Yontem.CALL)
        "telecom" -> listOf(Yontem.TELECOM)
        "dial" -> listOf(Yontem.DIAL)
        "ozel" -> listOf(Yontem.OZEL)
        else -> listOf(Yontem.CALL, Yontem.TELECOM, Yontem.DIAL, Yontem.OZEL)
    }

    private fun telUri(numara: String): Uri = Uri.fromParts("tel", numara, null)

    @Suppress("DEPRECATION")
    fun cozenler(action: String, numara: String = "123"): List<String> = try {
        ctx.packageManager.queryIntentActivities(Intent(action, telUri(numara)), 0)
            .map { it.activityInfo.packageName + "/" + it.activityInfo.name }
    } catch (e: Exception) {
        emptyList()
    }

    /** Bluetooth HFP telefon hesabı (READ_PHONE_STATE gerekir). */
    fun hfpHesabi(): PhoneAccountHandle? {
        if (!Izinler.telefonDurumu(ctx)) return null
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.callCapablePhoneAccounts.firstOrNull { h ->
                val c = h.componentName
                c.packageName.contains("bluetooth", ignoreCase = true) || c.className.contains("hfp", ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.w(AsistanApp.ETIKET, "PhoneAccount listelenemedi", e)
            null
        }
    }

    /** Yöntem kullanılamıyorsa nedeni; kullanılabiliyorsa null. */
    fun uygunDegil(y: Yontem, numara: String): String? = when (y) {
        Yontem.CALL -> when {
            !Izinler.arama(ctx) -> "CALL_PHONE izni yok"
            cozenler(Intent.ACTION_CALL, numara).isEmpty() -> "tel: CALL çözen yok"
            else -> null
        }
        Yontem.TELECOM -> when {
            !Izinler.arama(ctx) -> "CALL_PHONE izni yok"
            !Izinler.telefonDurumu(ctx) -> "READ_PHONE_STATE izni yok"
            hfpHesabi() == null -> "HFP PhoneAccount yok"
            else -> null
        }
        Yontem.DIAL -> if (cozenler(Intent.ACTION_DIAL, numara).isEmpty()) "tel: DIAL çözen yok" else null
        Yontem.OZEL -> when {
            !ayarlar.ozelTanimli -> "tanımlı değil"
            ayarlar.ozelTur == "activity" && !ozelCozuluyor(numara, null) -> "özel intent'i çözen activity yok"
            else -> null
        }
    }

    /** Zincirde ilk kullanılabilir yöntem (konuşma metnini seçmek için). */
    fun ilkUygun(numara: String): Yontem? = sira().firstOrNull { uygunDegil(it, numara) == null }

    /** Zinciri sırayla dener; başarılı ilk yöntemde durur. Her deneme ayrıntıya yazılır. */
    fun ara(aktivite: Activity, numara: String, ad: String?): Sonuc {
        val notlar = mutableListOf<String>()
        for (y in sira()) {
            val neden = uygunDegil(y, numara)
            if (neden != null) {
                notlar += "${y.kod}: atlandı ($neden)"
                continue
            }
            try {
                val ayrinti = calistir(aktivite, y, numara, ad)
                notlar += "${y.kod}: başarılı ($ayrinti)"
                Log.i(AsistanApp.ETIKET, "Arama: ${notlar.joinToString("; ")}")
                return Sonuc(y, notlar.joinToString("; "))
            } catch (e: Exception) {
                notlar += "${y.kod}: hata (${e.javaClass.simpleName}: ${e.message})"
            }
        }
        Log.w(AsistanApp.ETIKET, "Arama başarısız: ${notlar.joinToString("; ")}")
        return Sonuc(null, notlar.joinToString("; "))
    }

    @Suppress("DEPRECATION")
    private fun calistir(aktivite: Activity, y: Yontem, numara: String, ad: String?): String = when (y) {
        Yontem.CALL -> {
            val i = Intent(Intent.ACTION_CALL, telUri(numara))
            val cozen = cozenler(Intent.ACTION_CALL, numara).firstOrNull() ?: "?"
            aktivite.startActivity(i)
            cozen
        }
        Yontem.TELECOM -> {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val hesap = hfpHesabi() ?: throw IllegalStateException("HFP hesabı kayboldu")
            val ekstra = Bundle()
            ekstra.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, hesap)
            tm.placeCall(telUri(numara), ekstra)
            hesap.componentName.flattenToShortString()
        }
        Yontem.DIAL -> {
            val i = Intent(Intent.ACTION_DIAL, telUri(numara))
            val cozen = cozenler(Intent.ACTION_DIAL, numara).firstOrNull() ?: "?"
            aktivite.startActivity(i)
            cozen
        }
        Yontem.OZEL -> ozelGonder(aktivite, numara, ad)
    }

    private fun sablon(s: String, numara: String, ad: String?): String =
        s.replace("{numara}", numara).replace("{ad}", ad ?: "")

    private fun ozelIntent(numara: String, ad: String?): Intent {
        val a = ayarlar
        val i = Intent()
        if (a.ozelAction.isNotBlank()) i.action = a.ozelAction
        if (a.ozelPaket.isNotBlank()) {
            if (a.ozelSinif.isNotBlank()) {
                val sinif = if (a.ozelSinif.startsWith(".")) a.ozelPaket + a.ozelSinif else a.ozelSinif
                i.setClassName(a.ozelPaket, sinif)
            } else {
                i.setPackage(a.ozelPaket)
            }
        }
        if (a.ozelData.isNotBlank()) i.data = Uri.parse(sablon(a.ozelData, numara, ad))
        if (a.ozelExtraAnahtar.isNotBlank()) {
            i.putExtra(a.ozelExtraAnahtar, sablon(a.ozelExtraDeger.ifBlank { "{numara}" }, numara, ad))
        }
        return i
    }

    @Suppress("DEPRECATION")
    private fun ozelCozuluyor(numara: String, ad: String?): Boolean = try {
        ctx.packageManager.queryIntentActivities(ozelIntent(numara, ad), 0).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** Özel intent'i gönderir (Ayarlar'daki "Test et" düğmesi de bunu kullanır). */
    fun ozelGonder(aktivite: Activity, numara: String, ad: String?): String {
        val i = ozelIntent(numara, ad)
        when (ayarlar.ozelTur) {
            "broadcast" -> aktivite.sendBroadcast(i)
            "service" -> aktivite.startService(i)
            else -> {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                aktivite.startActivity(i)
            }
        }
        return "${ayarlar.ozelTur}: ${i.toUri(0)}"
    }
}
