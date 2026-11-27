package com.egea.asistan.yurutme

import android.app.Activity
import com.egea.asistan.Izinler
import com.egea.asistan.core.Cevaplar
import com.egea.asistan.core.Hedef
import com.egea.asistan.core.KisiEslestirici
import com.egea.asistan.core.MedyaEylemi
import com.egea.asistan.core.Niyet
import com.egea.asistan.medya.Istasyon
import com.egea.asistan.medya.RadyoKatalogu
import com.egea.asistan.medya.RadyoKontrol
import com.egea.asistan.medya.TvKatalogu
import com.egea.asistan.ui.TvActivity
import com.egea.asistan.veri.Rehber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Eylemin konuşmaya göre zamanlaması. */
enum class Zamanlama {
    /** Önce eylem (ör. haritayı aç), sonra konuş; aktivite hemen kapanır, konuşma uygulama seviyesinde sürer. */
    ONCE,

    /** Önce konuş, konuşma bitince eylem (ör. ara, şarkı aç, sonraki şarkı). */
    SONRA
}

/** Eylemin sonucu: log için yöntem ve sonuç; gerekirse ek konuşma. */
data class Rapor(val yontem: String, val sonuc: String, val ekKonusma: String? = null)

sealed class Plan {
    data class Eylem(
        val konusma: String,
        val zamanlama: Zamanlama = Zamanlama.ONCE,
        /** true ise ses odağı bırakılırken müzik uygulamasının kendiliğinden devam etmesi engellenir. */
        val muzikDursun: Boolean = false,
        val eylem: (suspend (Activity) -> Rapor)? = null,
        val yontem: String = "-",
        val sonuc: String = "tamam"
    ) : Plan()

    /** "Ahmet Yılmaz mı, Ahmet Kaya mı?" */
    data class Soru(val konusma: String, val secenekler: List<String>, val sec: suspend (Int) -> Plan) : Plan()

    /** "Kimi arayayım?" */
    data class KisiSor(val konusma: String) : Plan()
}

/** Niyeti uygulanabilir bir plana çevirir. */
class Yurutucu(private val aktivite: Activity) {
    private val ctx = aktivite.applicationContext
    private val arama = AramaYoneticisi(ctx)
    private val nav = NavigasyonYoneticisi(ctx)
    private val medya = MedyaYoneticisi(ctx)
    private val muzik = MuzikYoneticisi(ctx, medya)
    private val uygulama = UygulamaYoneticisi(ctx)

    suspend fun planla(n: Niyet): Plan = when (n) {
        is Niyet.Ara -> araPlani(n)
        is Niyet.Navigasyon -> navPlani(n)
        is Niyet.Medya -> medyaPlani(n)
        is Niyet.SarkiCal -> sarkiPlani(n)
        is Niyet.UygulamaAc -> uygulamaPlani(n)
        is Niyet.Radyo -> radyoPlani(n)
        is Niyet.Televizyon -> tvPlani(n)
        is Niyet.Saat -> saatPlani(n)
        Niyet.Iptal -> Plan.Eylem(Cevaplar.TAMAM, yontem = "-", sonuc = "iptal edildi")
        is Niyet.Bilinmiyor -> Plan.Eylem(Cevaplar.ANLAMADIM, yontem = "-", sonuc = "anlaşılamadı")
    }

    // ------------------------------------------------------------------ telefon
    private suspend fun araPlani(n: Niyet.Ara): Plan {
        n.numara?.let { return aramaYap(it, null) }
        val kisi = n.kisi ?: return Plan.KisiSor(Cevaplar.KIMI_ARAYAYIM)
        val kisiler = withContext(Dispatchers.IO) { Rehber(ctx).tumKisiler() }
        val esler = KisiEslestirici.eslestir(kisi.adaylar, kisiler.map { it.ad })
        if (esler.isEmpty()) {
            val ad = if (kisi.kesin) kisi.gorunen else null
            val metin = when {
                kisiler.isEmpty() && !Izinler.kisiler(ctx) ->
                    "Rehbere erişim izni yok. Ayarlardan izin verin ya da vcf dosyası içe aktarın."
                kisiler.isEmpty() -> "Rehber boş görünüyor. Ayarlardan vcf dosyası içe aktarabilirsin."
                ad != null -> "Rehberde $ad bulunamadı."
                else -> "Bu isimde kimseyi bulamadım."
            }
            return Plan.Eylem(metin, yontem = "rehber", sonuc = "bulunamadı (${kisi.adaylar.joinToString("|")}, ${kisiler.size} kişi)")
        }
        val bulunanlar = esler.map { kisiler[it] }
        // Favori tek başına eşleşmeye öncelikli
        val favori = bulunanlar.filter { it.kaynak == "favori" }
        if (bulunanlar.size == 1 || favori.size == 1) {
            val k = if (favori.size == 1) favori[0] else bulunanlar[0]
            val no = k.tercihliNumara() ?: return Plan.Eylem("${k.ad} için numara yok.", sonuc = "numara yok")
            return aramaYap(no.numara, k.ad)
        }
        val secenekler = bulunanlar.take(4)
        return Plan.Soru(Cevaplar.secimSorusu(secenekler.map { it.ad }), secenekler.map { it.ad }) { i ->
            val k = secenekler[i]
            val no = k.tercihliNumara()
            if (no == null) Plan.Eylem("${k.ad} için numara yok.", sonuc = "numara yok") else aramaYap(no.numara, k.ad)
        }
    }

    private fun aramaYap(numara: String, ad: String?): Plan {
        val ilk = arama.ilkUygun(numara)
            ?: return Plan.Eylem(
                "Arama yapılamıyor. Tanı ekranındaki arama bilgilerine bakın.",
                yontem = "yok",
                sonuc = "uygun arama yöntemi yok: " + arama.sira().joinToString { "${it.kod}=${arama.uygunDegil(it, numara)}" }
            )
        val seslenecek = ad ?: Cevaplar.numaraSeslendir(numara)
        val konusma = if (ilk == AramaYoneticisi.Yontem.DIAL) Cevaplar.numaraHazir(ad) else Cevaplar.araniyor(seslenecek)
        return Plan.Eylem(konusma, Zamanlama.SONRA, eylem = { act ->
            val s = arama.ara(act, numara, ad)
            val ek = when {
                s.yontem == null -> "Arama başlatılamadı."
                s.yontem == AramaYoneticisi.Yontem.DIAL && ilk != AramaYoneticisi.Yontem.DIAL -> Cevaplar.numaraHazir(null)
                else -> null
            }
            Rapor(s.yontem?.kod ?: "yok", (if (s.basarili) "başarılı" else "başarısız") + " [$numara] " + s.ayrinti, ek)
        })
    }

    // ------------------------------------------------------------------ navigasyon
    private fun navPlani(n: Niyet.Navigasyon): Plan {
        val adres = nav.adres(n.hedef) ?: return Plan.Eylem(
            if (n.hedef == Hedef.Ev) "Ev adresi kayıtlı değil. Ayarlardan ekleyebilirsin."
            else "İş adresi kayıtlı değil. Ayarlardan ekleyebilirsin.",
            yontem = "-", sonuc = "adres yok"
        )
        val konusma = if (n.rotaBaslat) Cevaplar.rota(n.hedef) else Cevaplar.haritadaAra(adres)
        return Plan.Eylem(konusma, Zamanlama.ONCE, eylem = { act ->
            val h = nav.hazirla(adres, n.rotaBaslat)
            if (h.intent.resolveActivity(act.packageManager) == null) {
                Rapor(h.yontem, "çözen uygulama yok: $adres", "Navigasyon uygulaması bulunamadı.")
            } else {
                act.startActivity(h.intent)
                Rapor(h.yontem, "başlatıldı: $adres")
            }
        })
    }

    // ------------------------------------------------------------------ medya
    private fun medyaPlani(n: Niyet.Medya): Plan = when (n.eylem) {
        MedyaEylemi.SONRAKI, MedyaEylemi.ONCEKI, MedyaEylemi.DEVAM -> {
            val metin = when (n.eylem) {
                MedyaEylemi.SONRAKI -> "Sonraki şarkı"
                MedyaEylemi.ONCEKI -> "Önceki şarkı"
                else -> "Devam ediyorum"
            }
            Plan.Eylem(metin, Zamanlama.SONRA, eylem = { _ -> Rapor(medya.eylem(n.eylem), "gönderildi") })
        }
        MedyaEylemi.DURDUR -> Plan.Eylem("Müzik durduruldu", Zamanlama.ONCE, muzikDursun = true, eylem = { _ ->
            Rapor(medya.eylem(MedyaEylemi.DURDUR), "gönderildi")
        })
        MedyaEylemi.SES_KAPAT -> Plan.Eylem("Ses kapatılıyor", Zamanlama.SONRA, eylem = { _ ->
            Rapor(medya.sesiKapat(), "ses kapatıldı")
        })
        else -> {
            val s = medya.sesAyarla(n)
            Plan.Eylem(s.konusma, Zamanlama.ONCE, yontem = "AudioManager STREAM_MUSIC", sonuc = "seviye ${s.seviye}/${s.maks}")
        }
    }

    // ------------------------------------------------------------------ radyo
    private suspend fun radyoPlani(n: Niyet.Radyo): Plan {
        val liste = RadyoKatalogu.yukle(ctx)
        val sorgu = n.istasyon
        if (sorgu == null) {
            val son = RadyoKatalogu.sonIstasyon()
            val hedef = son ?: liste.firstOrNull()
                ?: return Plan.Eylem("Radyo listesi alınamadı. İnternet bağlantısını kontrol edin.", yontem = "Radio Browser", sonuc = "liste boş: ${RadyoKatalogu.sonHata}")
            val kuyruk = if (liste.any { it.kimlik == hedef.kimlik }) liste else listOf(hedef) + liste
            return radyoCal(kuyruk, kuyruk.indexOfFirst { it.kimlik == hedef.kimlik }, "Radyo açılıyor, ${hedef.gorunenAd}")
        }
        var bulunan = RadyoKatalogu.bul(liste, sorgu)
        var kuyruk = liste
        if (bulunan == null) {
            val uzak = RadyoKatalogu.ara(sorgu)
            bulunan = RadyoKatalogu.bul(uzak, sorgu, 0.6) ?: uzak.firstOrNull()
            if (bulunan != null) kuyruk = listOf(bulunan) + liste
        }
        val b = bulunan ?: return Plan.Eylem("${n.gorunen ?: sorgu} radyosu bulunamadı.", yontem = "Radio Browser", sonuc = "bulunamadı")
        return radyoCal(kuyruk, kuyruk.indexOf(b).coerceAtLeast(0), "${b.gorunenAd} açılıyor")
    }

    private fun radyoCal(kuyruk: List<Istasyon>, indeks: Int, konusma: String): Plan =
        Plan.Eylem(konusma, Zamanlama.SONRA, eylem = { _ ->
            RadyoKontrol.cal(ctx, kuyruk, indeks)
            val i = kuyruk[indeks.coerceIn(0, kuyruk.lastIndex)]
            Rapor("Radyo (Media3)", "çalıyor: ${i.ad} ${i.url}")
        })

    // ------------------------------------------------------------------ televizyon
    private suspend fun tvPlani(n: Niyet.Televizyon): Plan {
        val liste = TvKatalogu.yukle(ctx)
        if (liste.isEmpty()) return Plan.Eylem("Kanal listesi alınamadı.", yontem = "M3U", sonuc = "liste boş: ${TvKatalogu.sonHata}")
        val sorgu = n.kanal
        val kanal = if (sorgu == null) {
            TvKatalogu.sonKanal(liste) ?: liste.first()
        } else {
            TvKatalogu.bul(liste, sorgu) ?: return Plan.Eylem("${n.gorunen ?: sorgu} kanalı bulunamadı.", yontem = "M3U", sonuc = "bulunamadı")
        }
        return tvAc(kanal.ad, kanal.url)
    }

    private fun tvAc(ad: String, url: String): Plan =
        Plan.Eylem("$ad açılıyor", Zamanlama.ONCE, eylem = { act ->
            act.startActivity(TvActivity.intent(act, url))
            Rapor("TV (Media3)", "açıldı: $ad $url")
        })

    // ------------------------------------------------------------------ şarkı
    private suspend fun sarkiPlani(n: Niyet.SarkiCal): Plan {
        val ad = n.gorunen.ifBlank { n.sorgu }
        // "TRT 1 aç", "Joy Türk aç": önce TV kanalı ve radyo listesinde güçlü eşleşme ara.
        if (n.uygulama == null && !n.sadeceAra) {
            TvKatalogu.bul(TvKatalogu.liste(ctx), n.sorgu, 0.92)?.let { return tvAc(it.ad, it.url) }
            val radyolar = RadyoKatalogu.liste(ctx)
            RadyoKatalogu.bul(radyolar, n.sorgu, 0.92)?.let { r -> return radyoCal(radyolar, radyolar.indexOf(r), "${r.gorunenAd} açılıyor") }
        }
        val konusma = if (n.sadeceAra) {
            val yer = MuzikYoneticisi.bulunmaHali(n.uygulama)
            if (yer.isEmpty()) "$ad aranıyor" else "$yer $ad aranıyor"
        } else {
            "$ad açılıyor"
        }
        return Plan.Eylem(konusma, Zamanlama.SONRA, eylem = { act ->
            val yontem = muzik.calistir(act, n)
            val ek = if (yontem.startsWith("başarısız")) "Açılamadı. Müzik uygulaması bulunamadı." else null
            Rapor(yontem, "sorgu: ${n.sorgu}", ek)
        })
    }

    // ------------------------------------------------------------------ uygulama
    private suspend fun uygulamaPlani(n: Niyet.UygulamaAc): Plan {
        val b = withContext(Dispatchers.Default) { uygulama.bul(n.ad) }
        if (b == null) {
            if (n.sarkiOlabilir) return sarkiPlani(Niyet.SarkiCal(n.ad, n.gorunen))
            return Plan.Eylem("${n.gorunen} adında bir uygulama bulamadım.", yontem = "bulanık eşleşme", sonuc = "bulunamadı")
        }
        return Plan.Eylem("${b.etiket} açılıyor", Zamanlama.ONCE, eylem = { act -> Rapor(uygulama.ac(act, b), "açıldı") })
    }

    // ------------------------------------------------------------------ saat
    private fun saatPlani(n: Niyet.Saat): Plan {
        val c = Calendar.getInstance()
        val metin = if (n.tarih) {
            val haftaGunu = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // Pazartesi=1 … Pazar=7
            Cevaplar.tarih(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, haftaGunu)
        } else {
            Cevaplar.saat(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }
        return Plan.Eylem(metin, yontem = "Calendar", sonuc = metin)
    }
}
