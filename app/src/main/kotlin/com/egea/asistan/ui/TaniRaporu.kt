package com.egea.asistan.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.view.KeyEvent
import com.egea.asistan.AsistanApp
import com.egea.asistan.BuildConfig
import com.egea.asistan.Izinler
import com.egea.asistan.servis.TusErisilebilirlikServisi
import com.egea.asistan.servis.TusOlaylari
import com.egea.asistan.servis.YuzenButonServisi
import com.egea.asistan.medya.RadyoDurumu
import com.egea.asistan.medya.RadyoKatalogu
import com.egea.asistan.medya.RadyoKontrol
import com.egea.asistan.medya.TvKatalogu
import com.egea.asistan.uyandirma.UyandirmaDeposu
import com.egea.asistan.uyandirma.UyandirmaKontrol
import com.egea.asistan.ses.Konusmaci
import com.egea.asistan.yurutme.AramaYoneticisi
import com.egea.asistan.yurutme.MedyaYoneticisi
import com.egea.asistan.yurutme.MuzikYoneticisi
import com.egea.asistan.yurutme.NavigasyonYoneticisi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tanı ekranının üç sütunu için metinler. Fotoğrafı çekilip gönderilecek şekilde sıkı yazılır. */
class TaniRaporu(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val saatBicimi = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("tr-TR"))
    private val tarihBicimi = SimpleDateFormat("dd.MM HH:mm:ss", Locale.forLanguageTag("tr-TR"))

    private fun StringBuilder.baslik(s: String) = append("\n━━ ").append(s).append(" ━━\n")
    private fun StringBuilder.satir(s: String) = append(s).append('\n')
    private fun isaret(b: Boolean?) = when (b) {
        true -> "✓"
        false -> "✗"
        null -> "?"
    }

    private fun getprop(anahtar: String): String = try {
        val p = ProcessBuilder("getprop", anahtar).redirectErrorStream(true).start()
        val s = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor()
        s.ifEmpty { "-" }
    } catch (e: Exception) {
        "?"
    }

    private fun apiAdi(api: Int): String = when (api) {
        24 -> "7.0"; 25 -> "7.1"; 26 -> "8.0"; 27 -> "8.1"; 28 -> "9"; 29 -> "10"; 30 -> "11"
        31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"; 37 -> "17"
        else -> "?"
    }

    @Suppress("DEPRECATION")
    private fun cozenler(i: Intent): List<String> = try {
        pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName + "/" + it.activityInfo.name.removePrefix(it.activityInfo.packageName) }
    } catch (e: Exception) {
        emptyList()
    }

    @Suppress("DEPRECATION")
    private fun surum(paket: String): String? = try {
        pm.getPackageInfo(paket, 0).versionName ?: "?"
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ sütun 1
    @Suppress("DEPRECATION")
    fun sistem(turkceTanima: String, ekranBilgisi: String): String = buildString {
        baslik("CİHAZ")
        satir("Android (ayarlardaki): ${Build.VERSION.RELEASE}")
        satir("GERÇEK API: ${Build.VERSION.SDK_INT} → Android ${apiAdi(Build.VERSION.SDK_INT)}")
        satir("getprop sdk: ${getprop("ro.build.version.sdk")} • system: ${getprop("ro.system.build.version.sdk")} • vendor: ${getprop("ro.vendor.build.version.sdk")}")
        satir("ilk API: product ${getprop("ro.product.first_api_level")} • board ${getprop("ro.board.first_api_level")}")
        satir("Güvenlik yaması: ${Build.VERSION.SECURITY_PATCH}")
        satir("Üretici/model: ${Build.MANUFACTURER} / ${Build.MODEL}")
        satir("Marka/cihaz/ürün: ${Build.BRAND} / ${Build.DEVICE} / ${Build.PRODUCT}")
        satir("Donanım/board: ${Build.HARDWARE} / ${Build.BOARD}")
        satir("Build: ${Build.DISPLAY}")
        satir("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        satir("64-bit: ${Build.SUPPORTED_64_BIT_ABIS.joinToString().ifEmpty { "yok" }} • os.arch: ${System.getProperty("os.arch")}")
        val cpu = try {
            File("/proc/cpuinfo").readLines()
                .filter { it.startsWith("Hardware") || it.startsWith("model name") || it.startsWith("Processor") || it.startsWith("CPU part") }
                .map { it.substringAfter(':').trim() }.distinct().take(3).joinToString(" | ")
        } catch (e: Exception) {
            "?"
        }
        satir("CPU: ${Runtime.getRuntime().availableProcessors()} çekirdek • $cpu")
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val memTotal = try {
            File("/proc/meminfo").readLines().firstOrNull { it.startsWith("MemTotal") }
                ?.filter { it.isDigit() }?.toLongOrNull()?.let { "${it / 1024} MB" } ?: "?"
        } catch (e: Exception) {
            "?"
        }
        satir("RAM: %.2f GB (sistem) • /proc/meminfo MemTotal: %s".format(mi.totalMem / 1073741824.0, memTotal))
        val stat = StatFs(ctx.filesDir.absolutePath)
        satir("Depolama /data: %.1f GB toplam, %.1f GB boş".format(stat.totalBytes / 1e9, stat.availableBytes / 1e9))
        val dm = ctx.resources.displayMetrics
        satir("Ekran: ${dm.widthPixels}×${dm.heightPixels} px, ${dm.densityDpi} dpi, ölçek ${dm.density}, en küçük genişlik ${ctx.resources.configuration.smallestScreenWidthDp} dp")
        satir("Yenileme/FPS: $ekranBilgisi")
        satir("Dil: ${Locale.getDefault()} • Telefon özelliği: ${pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)}")
        satir("Uygulama: ${BuildConfig.VERSION_NAME} (kod ${BuildConfig.VERSION_CODE}) • güncelleme: ${BuildConfig.GUNCELLEME_REPO}")

        baslik("SES TANIMA")
        satir("SpeechRecognizer kullanılabilir: ${if (SpeechRecognizer.isRecognitionAvailable(ctx)) "EVET" else "HAYIR"}")
        if (Build.VERSION.SDK_INT >= 31) {
            val cihazUstu = try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
            } catch (e: Exception) {
                false
            }
            satir("Cihaz üstü tanıma: ${if (cihazUstu) "EVET" else "HAYIR"}")
        }
        val servisler = try {
            pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).map { it.serviceInfo.packageName + "/" + it.serviceInfo.name.substringAfterLast('.') }
        } catch (e: Exception) {
            emptyList()
        }
        satir("RecognitionService: " + servisler.ifEmpty { listOf("YOK") }.joinToString())
        satir("RECOGNIZE_SPEECH çözen: " + cozenler(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)).ifEmpty { listOf("YOK") }.joinToString())
        val varsayilan = try {
            Settings.Secure.getString(ctx.contentResolver, "voice_recognition_service") ?: "-"
        } catch (e: Exception) {
            "okunamadı"
        }
        satir("Varsayılan tanıyıcı: $varsayilan")
        satir("Türkçe (tr-TR) tanıma: $turkceTanima")

        baslik("SESLİ CEVAP (TTS)")
        val k = AsistanApp.ornek.konusmaci
        satir("Durum: ${k.durum} • motor: ${k.motor ?: "-"}")
        satir("Motorlar: " + k.motorlar().ifEmpty { listOf("YOK") }.joinToString())
        satir("Türkçe ses (setLanguage): ${Konusmaci.dilSonucuAdi(k.dilSonucu)}")
        satir("Türkçe ses (isLanguageAvailable): ${Konusmaci.dilSonucuAdi(k.turkceDurumu())}")
        val turkceYok = k.durum != Konusmaci.Durum.HAZIR || (k.turkceDurumu() ?: TextToSpeech.LANG_NOT_SUPPORTED) < 0
        if (turkceYok) {
            satir("⚠ TÜRKÇE SES YOK: \"Google Konuşma Hizmetleri\" (com.google.android.tts) kurun,")
            satir("  sonra Ayarlar > Dil > Metin okuma çıkışı > Türkçe ses verisini indirin.")
        }

        baslik("HEY EGEA (uyandırma)")
        val uv = UyandirmaDeposu.yukle(ctx)
        satir("Ayar: ${if (AsistanApp.ornek.ayarlar.uyandirma) "açık" else "kapalı"} • örnek: ${uv?.sablonlar?.size ?: 0} • kaynak: ${AsistanApp.ornek.ayarlar.uyandirmaKaynak}")
        satir("Motor: ${UyandirmaKontrol.durum}")
        satir("Eşik: ${"%.1f".format(UyandirmaKontrol.esik)} (taban ${"%.1f".format(uv?.tabanMesafe ?: 0f)} × ${"%.2f".format(AsistanApp.ornek.ayarlar.uyandirmaCarpan)}) • son söz mesafesi: ${"%.1f".format(UyandirmaKontrol.sonMesafe)}")
        val sonTetik = if (UyandirmaKontrol.sonTetik > 0) SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(UyandirmaKontrol.sonTetik)) else "-"
        satir("Algılama sayısı: ${UyandirmaKontrol.tetikSayisi} • son: $sonTetik • seviye: ${"%.0f".format(UyandirmaKontrol.seviyeDb)} dB")

        baslik("RADYO & TV")
        // Bu rapor arka plan iş parçacığında hazırlanır: MediaController'a dokunulmaz, ana iş parçacığında
        // tutulan özet okunur (Media3 denetleyicisi başka iş parçacığından çağrılırsa uygulama çöker).
        val r = RadyoKontrol.ozet
        satir("Radyo listesi: ${RadyoKatalogu.liste(ctx).size} istasyon • son hata: ${RadyoKatalogu.sonHata ?: "-"}")
        satir("Çalan: ${r.baslik ?: "-"} (${if (r.caliyor) "çalıyor" else "durdu"}) • oynatma hatası: ${RadyoDurumu.hata ?: "-"}")
        satir("TV listesi: ${TvKatalogu.liste(ctx).size} kanal • ${TvKatalogu.listeAdresi()} • son hata: ${TvKatalogu.sonHata ?: "-"}")
        satir("Sürüş kilidi: ${if (AsistanApp.ornek.ayarlar.surusKilidi) "açık" else "kapalı"} • konum izni: ${isaret(Izinler.konum(ctx))}")

        baslik("İZİNLER")
        satir("${isaret(Izinler.mikrofon(ctx))} Mikrofon  ${isaret(Izinler.kisiler(ctx))} Kişiler  ${isaret(Izinler.arama(ctx))} Arama  ${isaret(Izinler.telefonDurumu(ctx))} Tel.durumu")
        satir("${isaret(Izinler.bildirimGonderme(ctx))} Bildirim  ${isaret(Izinler.ustteGosterme(ctx))} Üstte göster  ${isaret(Izinler.bildirimErisimi(ctx))} Bildirim erişimi")
        satir("${isaret(Izinler.bilinmeyenKaynak(ctx))} Bilinmeyen kaynak  ${isaret(Izinler.pilMuaf(ctx))} Pil muaf  ${isaret(Izinler.erisilebilirlik(ctx))} Erişilebilirlik  ${isaret(Izinler.varsayilanAsistan(ctx))} Vars. asistan")
        satir("Yüzen buton servisi: ${if (YuzenButonServisi.calisiyor) "çalışıyor" else "durdu"} • erişilebilirlik bağlı: ${TusErisilebilirlikServisi.bagli}")
    }

    // ------------------------------------------------------------------ sütun 2
    fun uygulamalar(tuslar: List<TusOlaylari.Olay>): String = buildString {
        val ayarlar = AsistanApp.ornek.ayarlar
        val nav = NavigasyonYoneticisi(ctx)
        val medya = MedyaYoneticisi(ctx)
        val muzik = MuzikYoneticisi(ctx, medya)

        baslik("NAVİGASYON")
        satir("Ayar: ${ayarlar.navUygulama} → kullanılacak: ${nav.secili()?.ad ?: "yok (geo:)"}")
        for (u in NavigasyonYoneticisi.HEPSI) {
            val s = surum(u.paket)
            satir("${isaret(s != null)} ${u.ad} (${u.paket})${s?.let { " v$it" } ?: ""}")
        }
        satir("geo: çözen: " + cozenler(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=test"))).take(4).ifEmpty { listOf("YOK") }.joinToString())
        satir("Ev: ${ayarlar.evAdresi.ifBlank { "-" }} • İş: ${ayarlar.isAdresi.ifBlank { "-" }}")

        baslik("MÜZİK")
        satir("Ayar: ${ayarlar.muzikUygulama} → kullanılacak: ${muzik.secili()?.ad ?: "yok"} • YouTube API: ${if (ayarlar.youtubeAnahtari.isBlank()) "yok" else "var"}")
        for (u in MuzikYoneticisi.HEPSI) {
            val s = surum(u.paket)
            satir("${isaret(s != null)} ${u.ad} (${u.paket})${s?.let { " v$it" } ?: ""}")
        }
        satir("PLAY_FROM_SEARCH çözen: " + cozenler(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)).take(5).ifEmpty { listOf("YOK") }.joinToString())
        val oturumlar = medya.oturumlar()
        if (!Izinler.bildirimErisimi(ctx)) {
            satir("Medya oturumları: bildirim erişimi yok (medya tuşları kullanılır)")
        } else {
            satir("Medya oturumları: " + oturumlar.map {
                val st = when (it.playbackState?.state) {
                    PlaybackState.STATE_PLAYING -> "çalıyor"
                    PlaybackState.STATE_PAUSED -> "duraklatıldı"
                    PlaybackState.STATE_STOPPED -> "durdu"
                    null -> "-"
                    else -> "durum ${it.playbackState?.state}"
                }
                "${it.packageName} [$st]"
            }.ifEmpty { listOf("yok") }.joinToString())
        }

        baslik("BLUETOOTH / TELEFON UYGULAMALARI")
        val anahtarlar = listOf("bt", "bluetooth", "phone", "dial", "call", "telefon", "hfp", "handsfree", "contacts", "telecom")
        @Suppress("DEPRECATION")
        val uygulamalar = try {
            pm.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList<ApplicationInfo>()
        }
        val bt = uygulamalar.mapNotNull { ai ->
            val etiket = try {
                ai.loadLabel(pm).toString()
            } catch (e: Exception) {
                ai.packageName
            }
            val metin = (ai.packageName + " " + etiket).lowercase(Locale.ROOT)
            val parcalar = metin.split('.', ' ', '_', '-')
            if (anahtarlar.any { a -> parcalar.contains(a) || (a.length > 3 && metin.contains(a)) }) "$etiket (${ai.packageName})" else null
        }.sorted()
        if (bt.isEmpty()) satir("bulunamadı") else bt.take(18).forEach { satir("• $it") }

        baslik("tel: ÇÖZÜCÜLER")
        val arama = AramaYoneticisi(ctx)
        satir("CALL: " + arama.cozenler(Intent.ACTION_CALL).ifEmpty { listOf("YOK") }.joinToString())
        satir("DIAL: " + arama.cozenler(Intent.ACTION_DIAL).ifEmpty { listOf("YOK") }.joinToString())
        val tm = try {
            ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        } catch (e: Exception) {
            null
        }
        val cevirici = try {
            tm?.defaultDialerPackage ?: "-"
        } catch (e: Exception) {
            "?"
        }
        satir("Varsayılan çevirici: $cevirici")

        baslik("TELECOM PHONEACCOUNT")
        if (tm == null) {
            satir("TelecomManager yok")
        } else if (!Izinler.telefonDurumu(ctx)) {
            satir("READ_PHONE_STATE izni yok (Kurulum > Telefon durumu)")
        } else {
            try {
                val hesaplar = tm.callCapablePhoneAccounts
                if (hesaplar.isEmpty()) satir("hesap yok")
                for (h in hesaplar) {
                    val pa = tm.getPhoneAccount(h)
                    val hfp = h.componentName.packageName.contains("bluetooth", true) || h.componentName.className.contains("hfp", true)
                    satir("${if (hfp) "[HFP] " else ""}${h.componentName.flattenToShortString()} id=${h.id} \"${pa?.label ?: ""}\"")
                }
            } catch (e: Exception) {
                satir("okunamadı: ${e.message}")
            }
        }

        baslik("ARAMA YÖNTEMİ")
        satir("Ayar: ${ayarlar.aramaYontemi} • sıra: ${arama.sira().joinToString(" → ") { it.kod }}")
        for (y in arama.sira()) satir("  ${y.kod}: ${arama.uygunDegil(y, "05001112233") ?: "kullanılabilir"}")
        val ozel = if (ayarlar.ozelTanimli) {
            "${ayarlar.ozelTur} ${ayarlar.ozelPaket}/${ayarlar.ozelSinif} ${ayarlar.ozelAction} data=${ayarlar.ozelData} ${ayarlar.ozelExtraAnahtar}=${ayarlar.ozelExtraDeger}"
        } else {
            "tanımlı değil"
        }
        satir("Özel intent: $ozel")

        baslik("TUŞLAR (bu ekran açıkken basın)")
        val kod = ayarlar.tetikTusKodu
        val tetik = if (kod > 0) "$kod (${KeyEvent.keyCodeToString(kod)})" else "ayarlı değil"
        satir("Tetik tuşu: $tetik")
        if (tuslar.isEmpty()) satir("(henüz tuş yok — direksiyon tuşlarına basın)")
        for (o in tuslar.take(14)) {
            val yon = if (o.eylem == KeyEvent.ACTION_DOWN) "↓" else if (o.eylem == KeyEvent.ACTION_UP) "↑" else "•"
            satir("${saatBicimi.format(Date(o.zaman))} ${KeyEvent.keyCodeToString(o.kod)} (${o.kod}) $yon tarama=${o.tarama} cihaz=${o.cihaz} [${o.kaynak}]")
        }
    }

    // ------------------------------------------------------------------ sütun 3
    fun komutlar(): String = buildString {
        val l = AsistanApp.ornek.log.hepsi()
        baslik("SON ${l.size} KOMUT (metin → niyet → yöntem → sonuç)")
        if (l.isEmpty()) satir("henüz komut yok")
        for (k in l) {
            satir("${tarihBicimi.format(Date(k.zaman))} \"${k.metin}\"")
            satir("  → ${k.niyet} → ${k.yontem} → ${k.sonuc}")
        }
    }
}
