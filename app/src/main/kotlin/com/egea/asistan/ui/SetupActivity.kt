package com.egea.asistan.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.egea.asistan.AsistanApp
import com.egea.asistan.Izinler
import com.egea.asistan.servis.MedyaDinleyiciServisi
import com.egea.asistan.servis.YuzenButonServisi

/**
 * İlk kurulum sihirbazı: izinleri tek tek ister. Teyplerde bazı ayar sayfaları gizli olabildiği için
 * sayfa açılamazsa ne yapılacağı yazar ve adım atlanabilir.
 */
class SetupActivity : ComponentActivity() {
    private data class Adim(
        val baslik: String,
        val aciklama: String,
        val durum: () -> Boolean?,
        val iste: () -> Boolean,
        val elle: String,
        val opsiyonel: Boolean = false
    )

    private val paket get() = packageName
    private var indeks = 0
    private var sonAcmaBasarisiz = false
    private val istenenler = mutableSetOf<String>()

    private val izinIstegi = registerForActivityResult(ActivityResultContracts.RequestPermission()) { goster() }

    private val adimlar: List<Adim> by lazy { adimlariKur() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        indeks = savedInstanceState?.getInt("indeks") ?: 0
        goster()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("indeks", indeks)
    }

    override fun onResume() {
        super.onResume()
        goster()
    }

    private fun ac(vararg intentler: Intent): Boolean {
        for (i in intentler) {
            try {
                startActivity(i)
                return true
            } catch (e: Exception) {
                // sonraki seçeneği dene
            }
        }
        return false
    }

    private fun uygulamaDetayi() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paket"))

    private fun izinIste(izin: String): Boolean {
        if (Izinler.verildi(this, izin)) return true
        if (izin in istenenler && !shouldShowRequestPermissionRationale(izin)) {
            // Daha önce reddedildi ve sistem artık sormuyor: uygulama ayarlarını aç.
            return ac(uygulamaDetayi())
        }
        istenenler += izin
        return try {
            izinIstegi.launch(izin)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun adimlariKur(): List<Adim> {
        val adb = "Bilgisayardan (USB hata ayıklama açıksa):"
        val l = mutableListOf(
            Adim(
                "Mikrofon",
                "Konuşmanızı duyabilmek için mikrofon izni gerekli.",
                { Izinler.mikrofon(this) },
                { izinIste(Manifest.permission.RECORD_AUDIO) },
                "Ayarlar > Uygulamalar > Egea Asistan > İzinler > Mikrofon.\n$adb adb shell pm grant $paket android.permission.RECORD_AUDIO"
            ),
            Adim(
                "Kişiler",
                "\"Ahmet'i ara\" diyebilmek için rehberi okuma izni. Teyp rehberi buraya eşitlemiyorsa Ayarlar'dan .vcf içe aktarabilir ya da favori ekleyebilirsiniz.",
                { Izinler.kisiler(this) },
                { izinIste(Manifest.permission.READ_CONTACTS) },
                "Ayarlar > Uygulamalar > Egea Asistan > İzinler > Kişiler.\n$adb adb shell pm grant $paket android.permission.READ_CONTACTS"
            ),
            Adim(
                "Arama",
                "Numarayı doğrudan aramak için (ACTION_CALL / Telecom). İzin yoksa numara tuşlama ekranında hazırlanır.",
                { Izinler.arama(this) },
                { izinIste(Manifest.permission.CALL_PHONE) },
                "Ayarlar > Uygulamalar > Egea Asistan > İzinler > Telefon.\n$adb adb shell pm grant $paket android.permission.CALL_PHONE"
            ),
            Adim(
                "Telefon durumu",
                "Bluetooth (HFP) telefon hesabını bulmak için gerekli.",
                { Izinler.telefonDurumu(this) },
                { izinIste(Manifest.permission.READ_PHONE_STATE) },
                "Ayarlar > Uygulamalar > Egea Asistan > İzinler > Telefon.\n$adb adb shell pm grant $paket android.permission.READ_PHONE_STATE"
            )
        )
        if (Build.VERSION.SDK_INT >= 33) {
            l += Adim(
                "Bildirim gösterme",
                "Yüzen mikrofon butonu çalışırken küçük bir bildirim gösterilir.",
                { Izinler.bildirimGonderme(this) },
                { izinIste(Manifest.permission.POST_NOTIFICATIONS) },
                "Ayarlar > Uygulamalar > Egea Asistan > Bildirimler.\n$adb adb shell pm grant $paket android.permission.POST_NOTIFICATIONS",
                opsiyonel = true
            )
        }
        l += Adim(
            "Konum (TV sürüş kilidi)",
            "Opsiyonel. Araç hareket ederken TV görüntüsünü gizleyip sadece sesi vermek için GPS hızı kullanılır. Konum hiçbir yere gönderilmez.",
            { Izinler.konum(this) },
            { izinIste(Manifest.permission.ACCESS_FINE_LOCATION) },
            "Ayarlar > Uygulamalar > Egea Asistan > İzinler > Konum.\n$adb adb shell pm grant $paket android.permission.ACCESS_FINE_LOCATION",
            opsiyonel = true
        )
        l += Adim(
            "Üstte gösterme",
            "Her uygulamanın üstünde duran yüzen mikrofon butonu ve arka planda dinleme ekranını açabilmek için.",
            { Izinler.ustteGosterme(this) },
            {
                ac(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$paket")),
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                    uygulamaDetayi()
                )
            },
            "Ayarlar > Uygulamalar > Egea Asistan > Diğer uygulamaların üzerinde göster (Özel erişim altında da olabilir).\n$adb adb shell appops set $paket SYSTEM_ALERT_WINDOW allow"
        )
        l += Adim(
            "Bildirim erişimi",
            "Çalan müzik uygulamasına doğrudan sonraki/önceki/durdur komutu göndermek için. Verilmezse medya tuşlarıyla denenir.",
            { Izinler.bildirimErisimi(this) },
            {
                val bilesen = ComponentName(this, MedyaDinleyiciServisi::class.java).flattenToString()
                val detay = if (Build.VERSION.SDK_INT >= 30) {
                    listOf(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, bilesen)
                    )
                } else {
                    emptyList()
                }
                ac(*(detay + Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) + uygulamaDetayi()).toTypedArray())
            },
            "Ayarlar > Uygulamalar > Özel erişim > Bildirim erişimi > Egea Asistan.\n$adb adb shell cmd notification allow_listener $paket/${MedyaDinleyiciServisi::class.java.name}",
            opsiyonel = true
        )
        l += Adim(
            "Bilinmeyen kaynak",
            "Güncelle butonunun yeni APK'yı kurabilmesi için.",
            { Izinler.bilinmeyenKaynak(this) },
            {
                if (Build.VERSION.SDK_INT >= 26) {
                    ac(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$paket")),
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    )
                } else {
                    ac(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
            },
            "Ayarlar > Uygulamalar > Özel erişim > Bilinmeyen uygulamaları yükle > Egea Asistan (eski sürümlerde Ayarlar > Güvenlik > Bilinmeyen kaynaklar).\n$adb adb shell appops set $paket REQUEST_INSTALL_PACKAGES allow",
            opsiyonel = true
        )
        l += Adim(
            "Pil optimizasyonu",
            "Teyp uygulamaları arka planda kapatmasın; yüzen buton sürekli kalsın.",
            { Izinler.pilMuaf(this) },
            {
                ac(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$paket")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            },
            "Ayarlar > Pil > Pil optimizasyonu > Egea Asistan > Optimize etme. Teypte \"arka plan uygulamalarını kapat\" gibi bir ayar varsa Egea Asistan'ı hariç tutun.\n$adb adb shell dumpsys deviceidle whitelist +$paket",
            opsiyonel = true
        )
        l += Adim(
            "Direksiyon tuşu (erişilebilirlik)",
            "Opsiyonel. Direksiyondaki sesli komut tuşu doğrudan bu uygulamayı açmıyorsa, erişilebilirlik servisini açıp Ayarlar'da \"Tuşa basarak öğren\" ile tuş kodunu seçin.",
            { Izinler.erisilebilirlik(this) },
            { ac(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            "Ayarlar > Erişilebilirlik > Egea Asistan direksiyon tuşu > Aç.\n$adb adb shell settings put secure enabled_accessibility_services $paket/com.egea.asistan.servis.TusErisilebilirlikServisi\nadb shell settings put secure accessibility_enabled 1",
            opsiyonel = true
        )
        l += Adim(
            "Varsayılan asistan",
            "Opsiyonel. Teybin sesli komut tuşu çoğu zaman \"varsayılan asistan\"ı açar. Egea Asistan'ı seçerseniz tuşa basınca doğrudan dinler.",
            { Izinler.varsayilanAsistan(this) },
            { ac(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) },
            "Ayarlar > Uygulamalar > Varsayılan uygulamalar > Dijital asistan uygulaması (ya da Asistan ve sesli giriş) > Egea Asistan. Bu menü yoksa teybin \"direksiyon tuşu öğrenme\" ayarından sesli komut tuşunu Egea Asistan'a atayın veya erişilebilirlik adımını kullanın.",
            opsiyonel = true
        )
        return l
    }

    private fun goster() {
        if (indeks >= adimlar.size) {
            ozetGoster()
            return
        }
        val a = adimlar[indeks]
        val d = a.durum()
        setContentView(sayfa {
            val ust = "İlk kurulum • ${indeks + 1}/${adimlar.size}" + (if (a.opsiyonel) " • opsiyonel" else "")
            yazi(ust, 18f, Renk.SOLUK)
            baslik(a.baslik)
            yazi(a.aciklama, 21f)
            val durumMetni = when (d) {
                true -> "Durum: ✓ Verildi"
                false -> "Durum: ✗ Verilmedi"
                null -> "Durum: ? Bu cihazda okunamıyor"
            }
            yazi(durumMetni, 22f, if (d == true) Renk.YESIL else if (d == false) Renk.KIRMIZI else Renk.TURUNCU)
            satir {
                if (d != true) {
                    buton("İzin ver / Ayarı aç", Renk.VURGU) {
                        sonAcmaBasarisiz = !a.iste()
                        if (sonAcmaBasarisiz) goster()
                    }
                }
                buton(if (d == true) "İleri ›" else "Atla ›", if (d == true) Renk.YESIL else Renk.PANEL2) {
                    indeks++
                    sonAcmaBasarisiz = false
                    goster()
                }
                if (indeks > 0) {
                    buton("‹ Geri", Renk.PANEL2) {
                        indeks--
                        sonAcmaBasarisiz = false
                        goster()
                    }
                }
            }
            bolum(if (sonAcmaBasarisiz) "Ayar sayfası açılamadı — elle yapın:" else "Sayfa açılmazsa:")
            val elle: TextView = yazi(a.elle, 17f, if (sonAcmaBasarisiz) Renk.TURUNCU else Renk.SOLUK)
            elle.setTextIsSelectable(true)
            ayrac()
            buton("Kurulumu bitir (kalanları atla)", Renk.PANEL2) { indeks = adimlar.size; goster() }
        })
    }

    private fun ozetGoster() {
        setContentView(sayfa {
            baslik("Kurulum tamam")
            for (a in adimlar) {
                val d = a.durum()
                val isaret = when (d) {
                    true -> "✓"
                    false -> "✗"
                    null -> "?"
                }
                val satirMetni = "$isaret ${a.baslik}" + (if (a.opsiyonel) " (opsiyonel)" else "")
                val renk = if (d == true) Renk.YESIL else if (d == false) Renk.KIRMIZI else Renk.TURUNCU
                yazi(satirMetni, 20f, renk)
            }
            yazi("Eksik adımları sonra Ayarlar > Kurulum sihirbazı'ndan tamamlayabilirsiniz.", 18f, Renk.SOLUK)
            satir {
                buton("Dinlemeyi dene", Renk.YESIL) {
                    bitir()
                    startActivity(Intent(this@SetupActivity, ListenActivity::class.java))
                    finish()
                }
                buton("Ayarlar", Renk.VURGU) {
                    bitir()
                    startActivity(Intent(this@SetupActivity, SettingsActivity::class.java))
                    finish()
                }
                buton("‹ Geri", Renk.PANEL2) {
                    indeks = adimlar.size - 1
                    goster()
                }
            }
        })
    }

    private fun bitir() {
        AsistanApp.ornek.ayarlar.kurulumTamam = true
        YuzenButonServisi.uygunsaBaslat(this)
    }

}
