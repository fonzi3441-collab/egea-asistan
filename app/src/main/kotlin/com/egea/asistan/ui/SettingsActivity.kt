package com.egea.asistan.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.egea.asistan.AsistanApp
import com.egea.asistan.BuildConfig
import com.egea.asistan.Favori
import com.egea.asistan.Izinler
import com.egea.asistan.core.VcfOkuyucu
import com.egea.asistan.guncelleme.Guncelleyici
import com.egea.asistan.medya.RadyoKatalogu
import com.egea.asistan.medya.TvKatalogu
import com.egea.asistan.uyandirma.UyandirmaDeposu
import com.egea.asistan.uyandirma.UyandirmaKontrol
import com.egea.asistan.servis.TusOlaylari
import com.egea.asistan.servis.YuzenButonServisi
import com.egea.asistan.veri.Rehber
import com.egea.asistan.yurutme.AramaYoneticisi
import com.egea.asistan.yurutme.MedyaYoneticisi
import com.egea.asistan.yurutme.MuzikYoneticisi
import com.egea.asistan.yurutme.NavigasyonYoneticisi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ayarlar ekranı (ayrı başlatıcı ikonu: "Egea Ayarlar"). */
class SettingsActivity : ComponentActivity() {
    private val ayarlar get() = AsistanApp.ornek.ayarlar
    private var tusOgreniliyor = false
    private var tusYazi: TextView? = null
    private var guncellemeYazi: TextView? = null
    private var vcfYazi: TextView? = null
    private var favoriKutusu: LinearLayout? = null
    private var uyandirmaYazi: TextView? = null
    private var medyaYazi: TextView? = null

    private val konumIzni = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val tusDinleyici = TusOlaylari.Dinleyici { o ->
        if (tusOgreniliyor && o.eylem == KeyEvent.ACTION_UP) tusSecildi(o.kod)
    }

    private val vcfSec = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vcfOku(it) } }
    private val vcfSecYedek = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vcfOku(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kur()
    }

    override fun onResume() {
        super.onResume()
        TusOlaylari.ekle(tusDinleyici)
        YuzenButonServisi.uygunsaBaslat(this)
        uyandirmaYazi?.text = uyandirmaMetni()
    }

    private fun uyandirmaMetni(): String {
        val v = UyandirmaDeposu.yukle(this)
        val ornek = if (v == null) "öğretilmedi" else "${v.sablonlar.size} örnek"
        val acik = if (ayarlar.uyandirma) "açık" else "kapalı"
        return "Durum: $acik • $ornek • motor: ${UyandirmaKontrol.durum}"
    }

    private fun medyaMetni(): String {
        val r = RadyoKatalogu.liste(this).size
        val t = TvKatalogu.liste(this).size
        return "Radyo: $r istasyon • TV: $t kanal • TV listesi: ${TvKatalogu.listeAdresi()}"
    }

    override fun onPause() {
        TusOlaylari.cikar(tusDinleyici)
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (tusOgreniliyor && event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) tusSecildi(event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun kur() {
        val nav = NavigasyonYoneticisi(this)
        val muzik = MuzikYoneticisi(this, MedyaYoneticisi(this))
        setContentView(sayfa {
            baslik("Egea Asistan Ayarları")
            yazi("Sürüm ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})", 16f, Renk.SOLUK)
            satir {
                buton("🎤 Dinlemeyi başlat", Renk.YESIL) { startActivity(Intent(this@SettingsActivity, ListenActivity::class.java)) }
                buton("Ana ekran (Radyo/TV)", Renk.VURGU) { startActivity(Intent(this@SettingsActivity, AnaEkranActivity::class.java)) }
                buton("Tanı ekranı", Renk.VURGU) { startActivity(Intent(this@SettingsActivity, DiagnosticsActivity::class.java)) }
                buton("Kurulum sihirbazı", Renk.PANEL2) { startActivity(Intent(this@SettingsActivity, SetupActivity::class.java)) }
            }

            // ---------------- Hey Egea
            bolum("“Hey Egea” uyandırma kelimesi")
            uyandirmaYazi = yazi(uyandirmaMetni(), 17f)
            anahtar("“Hey Egea” deyince dinle", ayarlar.uyandirma) { acik ->
                if (acik && !UyandirmaDeposu.varMi(this@SettingsActivity)) {
                    ayarlar.uyandirma = true
                    startActivity(Intent(this@SettingsActivity, UyandirmaOgretActivity::class.java))
                } else {
                    ayarlar.uyandirma = acik
                    YuzenButonServisi.uygunsaBaslat(this@SettingsActivity, yenidenYukle = true)
                }
                uyandirmaYazi?.text = uyandirmaMetni()
            }
            buton("“Hey Egea”yı öğret / yeniden öğret", Renk.VURGU) {
                startActivity(Intent(this@SettingsActivity, UyandirmaOgretActivity::class.java))
            }
            yazi("Mikrofon kaynağı (algılama kötüyse değiştirin):", 16f, Renk.SOLUK)
            secenekler(
                listOf(
                    "tanima" to "Ses tanıma (önerilen)",
                    "iletisim" to "İletişim — yankı/müzik giderme (destekleyen teyplerde)",
                    "mikrofon" to "Ham mikrofon"
                ),
                ayarlar.uyandirmaKaynak
            ) {
                ayarlar.uyandirmaKaynak = it
                YuzenButonServisi.uygunsaBaslat(this@SettingsActivity, yenidenYukle = true)
            }
            anahtar("Mikrofon/tuş ile açınca da önce “Dinliyorum” de", ayarlar.dinliyorumDe) { ayarlar.dinliyorumDe = it }

            // ---------------- Radyo ve TV
            bolum("Radyo ve TV")
            medyaYazi = yazi(medyaMetni(), 16f, Renk.SOLUK)
            satir {
                buton("Radyo listesini yenile", Renk.PANEL2) {
                    lifecycleScope.launch {
                        RadyoKatalogu.yukle(this@SettingsActivity, zorla = true)
                        medyaYazi?.text = medyaMetni()
                    }
                }
                buton("TV listesini yenile", Renk.PANEL2) {
                    lifecycleScope.launch {
                        TvKatalogu.yukle(this@SettingsActivity, zorla = true)
                        medyaYazi?.text = medyaMetni()
                    }
                }
            }
            yazi("TV kanal listesi (M3U adresi). Boş bırakılırsa açık yayınlardan oluşan varsayılan liste: ${TvKatalogu.VARSAYILAN_LISTE}", 15f, Renk.SOLUK)
            girdi("https://…/liste.m3u", ayarlar.tvListesi, InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT) { ayarlar.tvListesi = it }
            anahtar("Teyp açılınca son dinlenen radyoyu çal (araç radyosu gibi)", ayarlar.acilistaRadyo) { ayarlar.acilistaRadyo = it }
            anahtar("Araç hareket halindeyken TV görüntüsünü gizle (önerilir)", ayarlar.surusKilidi) { ayarlar.surusKilidi = it }
            if (!Izinler.konum(this@SettingsActivity)) {
                buton("Sürüş kilidi için konum izni ver", Renk.TURUNCU) {
                    konumIzni.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            anahtar("Ana ekranda FPS göstergesi", ayarlar.fpsGoster) { ayarlar.fpsGoster = it }

            // ---------------- Navigasyon
            bolum("Navigasyon uygulaması")
            val navSecenekler = listOf("otomatik" to "Otomatik (yüklü olanı seç: Google → Waze → Yandex)") +
                NavigasyonYoneticisi.HEPSI.map { it.kod to (it.ad + (if (nav.yukluMu(it.paket)) "  ✓ yüklü" else "  (yüklü değil)")) }
            secenekler(navSecenekler, ayarlar.navUygulama) { ayarlar.navUygulama = it }
            yazi("Şu an kullanılacak: " + (nav.secili()?.ad ?: "yok (genel harita bağlantısı)"), 16f, Renk.SOLUK)

            bolum("Ev ve iş adresi")
            girdi("Ev adresi (ör. Kadıköy Moda Cad. 10, İstanbul)", ayarlar.evAdresi) { ayarlar.evAdresi = it }
            girdi("İş adresi", ayarlar.isAdresi) { ayarlar.isAdresi = it }

            // ---------------- Müzik
            bolum("Müzik uygulaması (şarkı açma)")
            val muzikSecenekler = listOf("otomatik" to "Otomatik (Spotify → YouTube Music → YouTube)") +
                MuzikYoneticisi.HEPSI.map { it.kod to (it.ad + (if (muzik.yukluMu(it.paket)) "  ✓ yüklü" else "  (yüklü değil)")) }
            secenekler(muzikSecenekler, ayarlar.muzikUygulama) { ayarlar.muzikUygulama = it }

            bolum("YouTube Data API anahtarı (opsiyonel)")
            yazi("Varsa \"YouTube'da X aç\" ilk videoyu doğrudan açar; yoksa arama sonuçları açılır. Anahtarı Android uygulamasına kısıtlarsanız paket: ${packageName}, SHA-1: ${MuzikYoneticisi.imzaSha1(this@SettingsActivity) ?: "?"}", 15f, Renk.SOLUK)
            girdi("AIza…", ayarlar.youtubeAnahtari) { ayarlar.youtubeAnahtari = it }

            // ---------------- Rehber
            bolum("Rehber")
            yazi("Android rehberi: " + (if (Izinler.kisiler(this@SettingsActivity)) "izin var" else "izin yok"), 17f)
            vcfYazi = yazi("İçe aktarılan (.vcf): ${Rehber(this@SettingsActivity).vcfSayisi()} kişi", 17f)
            satir {
                buton(".vcf içe aktar", Renk.VURGU) { vcfSecimiAc() }
                buton("İçe aktarılanları sil", Renk.PANEL2) {
                    Rehber(this@SettingsActivity).vcfTemizle()
                    vcfYazi?.text = "İçe aktarılan (.vcf): 0 kişi"
                }
            }
            yazi("Telefonunuzdan rehberi .vcf olarak dışa aktarıp USB bellek veya indirme ile teybe kopyalayın.", 15f, Renk.SOLUK)

            bolum("Favoriler (öncelikli kişiler)")
            favoriKutusu = dikey { }
            favorileriGoster()
            buton("+ Favori ekle", Renk.VURGU) { favoriEkleDialog() }

            // ---------------- Arama yöntemi
            bolum("Telefon araması yöntemi")
            secenekler(
                listOf(
                    "otomatik" to "Otomatik zincir: ACTION_CALL → Telecom (HFP) → DIAL → özel intent",
                    "ozel_once" to "Önce özel intent, sonra zincir",
                    "call" to "Sadece ACTION_CALL",
                    "telecom" to "Sadece TelecomManager.placeCall (HFP)",
                    "dial" to "Sadece DIAL (numara hazır, yeşil tuşa basılır)",
                    "ozel" to "Sadece özel intent"
                ),
                ayarlar.aramaYontemi
            ) { ayarlar.aramaYontemi = it }

            bolum("Özel arama intent'i (teybin Bluetooth uygulaması için)")
            yazi("Şablonlarda {numara} ve {ad} yazabilirsiniz. Tanı ekranındaki Bluetooth uygulaması paket adlarına bakın.", 15f, Renk.SOLUK)
            secenekler(
                listOf("activity" to "startActivity", "broadcast" to "sendBroadcast", "service" to "startService"),
                ayarlar.ozelTur
            ) { ayarlar.ozelTur = it }
            girdi("Paket (ör. com.syu.bt)", ayarlar.ozelPaket) { ayarlar.ozelPaket = it }
            girdi("Sınıf (opsiyonel, ör. .BtDialActivity)", ayarlar.ozelSinif) { ayarlar.ozelSinif = it }
            girdi("Action (ör. android.intent.action.CALL)", ayarlar.ozelAction) { ayarlar.ozelAction = it }
            girdi("Data şablonu (ör. tel:{numara})", ayarlar.ozelData) { ayarlar.ozelData = it }
            girdi("Extra anahtarı (ör. number)", ayarlar.ozelExtraAnahtar) { ayarlar.ozelExtraAnahtar = it }
            girdi("Extra değeri (varsayılan {numara})", ayarlar.ozelExtraDeger) { ayarlar.ozelExtraDeger = it }
            var testNumara = ""
            girdi("Test numarası", "", InputType.TYPE_CLASS_PHONE) { testNumara = it }
            satir {
                buton("Özel intent'i test et", Renk.VURGU) {
                    if (testNumara.isBlank()) {
                        Toast.makeText(this@SettingsActivity, "Önce test numarası yazın", Toast.LENGTH_SHORT).show()
                    } else {
                        val sonuc = try {
                            AramaYoneticisi(this@SettingsActivity).ozelGonder(this@SettingsActivity, testNumara, "Test")
                        } catch (e: Exception) {
                            "hata: ${e.javaClass.simpleName}: ${e.message}"
                        }
                        AsistanApp.ornek.log.ekle("(test) $testNumara", "ARA(test)", "Özel intent", sonuc)
                        Toast.makeText(this@SettingsActivity, sonuc, Toast.LENGTH_LONG).show()
                    }
                }
                buton("Zinciri test et", Renk.PANEL2) {
                    if (testNumara.isBlank()) {
                        Toast.makeText(this@SettingsActivity, "Önce test numarası yazın", Toast.LENGTH_SHORT).show()
                    } else {
                        val s = AramaYoneticisi(this@SettingsActivity).ara(this@SettingsActivity, testNumara, null)
                        AsistanApp.ornek.log.ekle("(test) $testNumara", "ARA(test)", s.yontem?.kod ?: "yok", s.ayrinti)
                        Toast.makeText(this@SettingsActivity, s.ayrinti, Toast.LENGTH_LONG).show()
                    }
                }
            }

            // ---------------- Tetik tuşu
            bolum("Direksiyon tuşu")
            tusYazi = yazi(tusMetni(), 18f)
            satir {
                buton("Tuşa basarak öğren", Renk.VURGU) {
                    tusOgreniliyor = true
                    tusYazi?.text = "Şimdi direksiyondaki sesli komut tuşuna basın… (iptal için Geri)"
                }
                buton("Temizle", Renk.PANEL2) {
                    tusOgreniliyor = false
                    ayarlar.tetikTusKodu = 0
                    tusYazi?.text = tusMetni()
                }
                buton("Erişilebilirlik ayarları", Renk.PANEL2) { ayarAc(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            girdi("Tuş kodu (elle, sayı)", if (ayarlar.tetikTusKodu > 0) ayarlar.tetikTusKodu.toString() else "", InputType.TYPE_CLASS_NUMBER) {
                ayarlar.tetikTusKodu = it.toIntOrNull() ?: 0
            }
            yazi("Tuş başka uygulamalar açıkken de çalışsın diye erişilebilirlik servisi açık olmalı. Tuş teybin \"sesli komut\" uygulamasını açıyorsa Kurulum'daki \"Varsayılan asistan\" adımını deneyin.", 15f, Renk.SOLUK)

            // ---------------- Yüzen buton
            bolum("Yüzen mikrofon butonu")
            anahtar("Her uygulamanın üstünde göster", ayarlar.yuzenButon) { acik ->
                ayarlar.yuzenButon = acik
                if (acik) YuzenButonServisi.uygunsaBaslat(this@SettingsActivity) else YuzenButonServisi.durdur(this@SettingsActivity)
            }
            satir {
                buton("Konumu sıfırla", Renk.PANEL2) {
                    ayarlar.balonX = -1
                    ayarlar.balonY = -1
                    YuzenButonServisi.durdur(this@SettingsActivity)
                    YuzenButonServisi.uygunsaBaslat(this@SettingsActivity)
                }
                buton("Sesli cevabı dene", Renk.PANEL2) {
                    AsistanApp.ornek.konusmaci.konus("Merhaba, ben Egea Asistan. Marmara Park'a rota başlatıyorum.")
                }
            }

            // ---------------- Güncelleme
            bolum("Güncelleme")
            guncellemeYazi = yazi("Kaynak: ${Guncelleyici.apkLinki}", 15f, Renk.SOLUK)
            buton("Güncellemeleri denetle ve kur", Renk.YESIL) { guncelle(false) }
        })
    }

    private fun ayarAc(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Bu ayar sayfası teypte yok", Toast.LENGTH_LONG).show()
        }
    }

    private fun tusMetni(): String {
        val kod = ayarlar.tetikTusKodu
        val servis = if (Izinler.erisilebilirlik(this)) "erişilebilirlik servisi açık" else "erişilebilirlik servisi KAPALI"
        return if (kod > 0) "Tetik tuşu: $kod (${KeyEvent.keyCodeToString(kod)}) • $servis" else "Tetik tuşu ayarlı değil • $servis"
    }

    private fun tusSecildi(kod: Int) {
        if (!tusOgreniliyor) return
        tusOgreniliyor = false
        ayarlar.tetikTusKodu = kod
        tusYazi?.text = tusMetni()
        Toast.makeText(this, "Tuş kaydedildi: $kod", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- vcf
    private fun vcfSecimiAc() {
        try {
            vcfSec.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "text/plain", "application/octet-stream", "*/*"))
        } catch (e: Exception) {
            try {
                vcfSecYedek.launch("*/*")
            } catch (e2: Exception) {
                Toast.makeText(this, "Dosya seçici bulunamadı", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun vcfOku(uri: Uri) {
        lifecycleScope.launch {
            val sonuc = withContext(Dispatchers.IO) {
                try {
                    val metin = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    val kisiler = VcfOkuyucu.oku(metin)
                    if (kisiler.isNotEmpty()) Rehber(this@SettingsActivity).vcfKaydet(kisiler)
                    "${kisiler.size} kişi içe aktarıldı"
                } catch (e: Exception) {
                    "Okunamadı: ${e.message}"
                }
            }
            vcfYazi?.text = "İçe aktarılan (.vcf): ${Rehber(this@SettingsActivity).vcfSayisi()} kişi — $sonuc"
            Toast.makeText(this@SettingsActivity, sonuc, Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- favoriler
    private fun favorileriGoster() {
        val kutu = favoriKutusu ?: return
        kutu.removeAllViews()
        val k = Kurucu(this, kutu)
        val liste = ayarlar.favoriler
        if (liste.isEmpty()) k.yazi("Favori yok. \"annem\", \"eşim\" gibi adlarla ekleyebilirsiniz.", 16f, Renk.SOLUK)
        for (f in liste) {
            k.satir {
                yazi("${f.ad} — ${f.numara}", 19f)
                buton("Sil", Renk.KIRMIZI) {
                    ayarlar.favoriler = ayarlar.favoriler.filterNot { it == f }
                    favorileriGoster()
                }
            }
        }
    }

    private fun favoriEkleDialog() {
        val kutu = LinearLayout(this)
        kutu.orientation = LinearLayout.VERTICAL
        kutu.setPadding(dp(20), dp(10), dp(20), 0)
        val ad = EditText(this).apply { hint = "Ad (ör. Annem)"; textSize = 20f }
        val no = EditText(this).apply { hint = "Numara"; textSize = 20f; inputType = InputType.TYPE_CLASS_PHONE }
        kutu.addView(ad)
        kutu.addView(no)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Favori ekle")
            .setView(kutu)
            .setPositiveButton("Ekle") { _, _ ->
                val a = ad.text.toString().trim()
                val n = no.text.toString().trim()
                if (a.isNotEmpty() && n.isNotEmpty()) {
                    ayarlar.favoriler = ayarlar.favoriler + Favori(a, n)
                    favorileriGoster()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // ---------------------------------------------------------------- güncelleme
    private fun guncelle(zorla: Boolean) {
        val yazi = guncellemeYazi
        yazi?.text = "Kontrol ediliyor…"
        lifecycleScope.launch {
            try {
                val bilgi = Guncelleyici.kontrol()
                val yuklu = BuildConfig.VERSION_CODE
                if (!zorla && bilgi.surumKodu != null && bilgi.surumKodu <= yuklu) {
                    yazi?.text = "Güncel sürüm kullanılıyor (yüklü $yuklu, son ${bilgi.surumKodu})."
                    AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Güncel")
                        .setMessage("Yüklü sürüm $yuklu, son sürüm ${bilgi.surumKodu}. Yine de indirilsin mi?")
                        .setPositiveButton("İndir") { _, _ -> guncelle(true) }
                        .setNegativeButton("Hayır", null)
                        .show()
                    return@launch
                }
                if (!Guncelleyici.kurulumIzniVar(this@SettingsActivity)) {
                    yazi?.text = "Önce \"bilinmeyen uygulamaları yükle\" iznini verin, sonra tekrar basın."
                    try {
                        Guncelleyici.kurulumIzniEkrani(this@SettingsActivity)
                    } catch (e: Exception) {
                        yazi?.text = "İzin ekranı açılamadı. Kurulum sihirbazındaki \"Bilinmeyen kaynak\" adımına bakın."
                    }
                    return@launch
                }
                yazi?.text = "İndiriliyor… (sürüm ${bilgi.surumKodu ?: "?"})"
                val dosya = Guncelleyici.indir(this@SettingsActivity) { yuzde -> yazi?.text = "İndiriliyor… %$yuzde" }
                yazi?.text = "İndirildi, kurulum ekranı açılıyor."
                Guncelleyici.kur(this@SettingsActivity, dosya)
            } catch (e: Exception) {
                yazi?.text = "Güncelleme hatası: ${e.message}\nTarayıcıdan indirin: ${Guncelleyici.apkLinki}"
            }
        }
    }
}
