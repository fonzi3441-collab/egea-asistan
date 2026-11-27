package com.egea.asistan.core

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Örnek cümle → beklenen niyet tablosu.
 *
 * Beklenen değer biçimi:
 *  ARA:isim (aday yazılışlar arasında isim olmalı) · ARA#numara · ARA? (kişi sorulacak)
 *  NAV:adres · NAV:EV · NAV:IS · HARITA:adres (rota değil, haritada arama)
 *  MEDYA:EYLEM · MEDYA:SES_AYARLA=15 · MEDYA:SES_AYARLA=%50 · MEDYA:SES_KIS/AZ
 *  SARKI:sorgu · SARKI:sorgu@UYGULAMA · ARAMA:sorgu · ARAMA:sorgu@UYGULAMA
 *  UYGULAMA:ad · RADYO · RADYO:istasyon · TV · TV:kanal · SAAT · TARIH · IPTAL · BILINMIYOR
 */
@RunWith(Parameterized::class)
class KomutCozucuTest(private val cumle: String, private val beklenen: String, @Suppress("unused") private val ad: String) {

    companion object {
        private val UYGULAMALAR = listOf(
            "YouTube", "YouTube Music", "Spotify", "Haritalar", "Waze", "Radio", "BT Phone", "Ayarlar", "Chrome"
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {2}")
        fun veriler(): List<Array<Any>> = listOf(
            // ---------------- Telefon araması
            "Ahmet'i ara" to "ARA:ahmet",
            "Ahmeti ara" to "ARA:ahmet",
            "annemi ara" to "ARA:annem",
            "Annemi arar mısın" to "ARA:annem",
            "babamı ara lütfen" to "ARA:babam",
            "Ayşe'yi ara" to "ARA:ayşe",
            "Ayşeyi ara" to "ARA:ayşe",
            "Ahmet Yılmaz'ı ara" to "ARA:ahmet yılmaz",
            "Mehmet Kaya'yı arayabilir misin" to "ARA:mehmet kaya",
            "0532 123 45 67'yi ara" to "ARA#05321234567",
            "05321234567 ara" to "ARA#05321234567",
            "sıfır beş yüz otuz iki yüz yirmi üç kırk beş altmış yediyi ara" to "ARA#05321234567",
            "+90 532 123 45 67'yi ara" to "ARA#+905321234567",
            "112'yi ara" to "ARA#112",
            "Ali'yi ara" to "ARA:ali",
            "Ali ara" to "ARA:ali",
            "beni Ahmet'e bağla" to "ARA:ahmet",
            "Ahmet'e telefon et" to "ARA:ahmet",
            "ara" to "ARA?",
            "Egea, Ahmet'i ara" to "ARA:ahmet",
            "hemen eşimi ara" to "ARA:eşim",
            "Ahmet'in numarasını ara" to "ARA:ahmet",
            "iş yerini ara" to "ARA:iş yeri",
            "Hüseyini ara" to "ARA:hüseyin",
            "Ahmet'i bir ara" to "ARA:ahmet",
            "Ahmet'i telefonla ara" to "ARA:ahmet",

            // ---------------- "ara" ama telefon değil: uygulamada arama
            "YouTube'da Tarkan ara" to "ARAMA:tarkan@YOUTUBE",
            "Youtubeda Tarkan ara" to "ARAMA:tarkan@YOUTUBE",
            "Tarkan'ı YouTube'da ara" to "ARAMA:tarkan@YOUTUBE",
            "Spotify'da Sezen Aksu ara" to "ARAMA:sezen aksu@SPOTIFY",
            "Google'da hava durumu ara" to "ARAMA:hava durumu@GOOGLE",
            "internette Egea yakıt tüketimi ara" to "ARAMA:egea yakıt tüketimi@GOOGLE",
            "Tarkan şarkısı ara" to "ARAMA:tarkan",
            "en yakın benzinliği ara" to "HARITA:en yakın benzinlik",
            "haritada Marmara Park'ı göster" to "HARITA:marmara park",
            "YouTube'dan Tarkan Şımarık aç" to "SARKI:tarkan şımarık@YOUTUBE",
            "Spotify'dan Sezen Aksu çal" to "SARKI:sezen aksu@SPOTIFY",
            "YouTube Müzik'te Tarkan çal" to "SARKI:tarkan@YOUTUBE_MUSIC",

            // ---------------- Navigasyon
            "Marmara Park'a gideceğim" to "NAV:marmara park",
            "Marmara Parka gideceğim" to "NAV:marmara park",
            "beni Kadıköy'e götür" to "NAV:kadıköy",
            "Kadıköye götür" to "NAV:kadıköy",
            "Esenyurt'a rota" to "NAV:esenyurt",
            "Esenyurt'a rota oluştur" to "NAV:esenyurt",
            "Ankara'ya git" to "NAV:ankara",
            "Ankaraya gidelim" to "NAV:ankara",
            "eve git" to "NAV:EV",
            "beni eve götür" to "NAV:EV",
            "evime gidelim" to "NAV:EV",
            "eve rota" to "NAV:EV",
            "eve dön" to "NAV:EV",
            "işe git" to "NAV:IS",
            "iş yerine götür" to "NAV:IS",
            "ofise gidelim" to "NAV:IS",
            "Taksim'e nasıl giderim" to "NAV:taksim",
            "Kadıköy'e yol tarifi" to "NAV:kadıköy",
            "rota Bursa" to "NAV:bursa",
            "navigasyon Kadıköy'e başlat" to "NAV:kadıköy",
            "İstanbul Havalimanı'na git" to "NAV:istanbul havalimanı",
            "Bağdat Caddesine git" to "NAV:bağdat caddesi",
            "en yakın benzinliğe git" to "NAV:en yakın benzinlik",
            "Kadıköy'e gitmek istiyorum" to "NAV:kadıköy",
            "Maltepe'ye götür" to "NAV:maltepe",
            "hastaneye git" to "NAV:hastane",
            "Kadıköy'e doğru sür" to "NAV:kadıköy",

            // ---------------- Medya kontrolü
            "müziği değiştir" to "MEDYA:SONRAKI",
            "sonraki şarkı" to "MEDYA:SONRAKI",
            "sonraki" to "MEDYA:SONRAKI",
            "bir sonraki şarkı" to "MEDYA:SONRAKI",
            "şarkıyı geç" to "MEDYA:SONRAKI",
            "başka şarkı aç" to "MEDYA:SONRAKI",
            "kanalı değiştir" to "MEDYA:SONRAKI",
            "önceki" to "MEDYA:ONCEKI",
            "önceki şarkı" to "MEDYA:ONCEKI",
            "geri" to "MEDYA:ONCEKI",
            "durdur" to "MEDYA:DURDUR",
            "müziği durdur" to "MEDYA:DURDUR",
            "müziği kapat" to "MEDYA:DURDUR",
            "duraklat" to "MEDYA:DURDUR",
            "devam et" to "MEDYA:DEVAM",
            "müziğe devam" to "MEDYA:DEVAM",
            "müziği aç" to "MEDYA:DEVAM",
            "bir şarkı çal" to "MEDYA:DEVAM",

            // ---------------- Ses seviyesi
            "sesi aç" to "MEDYA:SES_AC",
            "sesi kıs" to "MEDYA:SES_KIS",
            "sesi biraz kıs" to "MEDYA:SES_KIS/AZ",
            "sesi çok aç" to "MEDYA:SES_AC/COK",
            "sesi bir tık aç" to "MEDYA:SES_AC/AZ",
            "müziğin sesini yükselt" to "MEDYA:SES_AC",
            "sesi on beş yap" to "MEDYA:SES_AYARLA=15",
            "sesi 15 yap" to "MEDYA:SES_AYARLA=15",
            "ses yirmi" to "MEDYA:SES_AYARLA=20",
            "sesi on beşe getir" to "MEDYA:SES_AYARLA=15",
            "sesi dörde indir" to "MEDYA:SES_AYARLA=4",
            "sesi yüzde elli yap" to "MEDYA:SES_AYARLA=%50",
            "sesi %30 yap" to "MEDYA:SES_AYARLA=%30",
            "sesi sonuna kadar aç" to "MEDYA:SES_MAKS",
            "sesi kapat" to "MEDYA:SES_KAPAT",
            "sessize al" to "MEDYA:SES_KAPAT",

            // ---------------- Şarkı açma
            "Tarkan Şımarık aç" to "SARKI:tarkan şımarık",
            "Sezen Aksu çal" to "SARKI:sezen aksu",
            "Tarkan'ın Şımarık şarkısını aç" to "SARKI:tarkan şımarık",
            "Tarkan'dan Şımarık çal" to "SARKI:tarkan şımarık",
            "biraz Sezen Aksu dinleyelim" to "SARKI:sezen aksu",
            "Sezen Aksu dinlemek istiyorum" to "SARKI:sezen aksu",
            "Barış Manço oynat" to "SARKI:barış manço",
            "Tarkan Gidiyorum aç" to "SARKI:tarkan gidiyorum",
            "hareketli müzik aç" to "SARKI:hareketli müzik",

            // ---------------- Uygulama açma
            "YouTube'u aç" to "UYGULAMA:youtube",
            "Youtube aç" to "UYGULAMA:youtube",
            "Spotify aç" to "UYGULAMA:spotify",
            "haritayı aç" to "UYGULAMA:harita",
            "radyo uygulamasını aç" to "UYGULAMA:radyo",
            "Waze'i aç" to "UYGULAMA:waze",
            "müzik uygulamasını aç" to "UYGULAMA:müzik",
            "ayarları aç" to "UYGULAMA:ayarlar",
            "navigasyonu aç" to "UYGULAMA:navigasyon",
            "telefonu aç" to "UYGULAMA:telefon",
            "Chrome'u aç" to "UYGULAMA:chrome",

            // ---------------- Radyo
            "radyo aç" to "RADYO",
            "radyoyu aç" to "RADYO",
            "radyo" to "RADYO",
            "radyo dinlemek istiyorum" to "RADYO",
            "internet radyosu aç" to "RADYO",
            "Power FM aç" to "RADYO:power fm",
            "Kral FM'i aç" to "RADYO:kral fm",
            "radyoda Joy Türk aç" to "RADYO:joy türk",
            "Virgin Radio çal" to "RADYO:virgin radio",
            "Radyo Fenomen dinle" to "RADYO:fenomen",
            "radyoyu kapat" to "MEDYA:DURDUR",
            "radyoyu değiştir" to "MEDYA:SONRAKI",

            // ---------------- Televizyon
            "televizyon aç" to "TV",
            "televizyonu aç" to "TV",
            "TV'yi aç" to "TV",
            "televizyon" to "TV",
            "Show TV aç" to "TV:show tv",
            "Kanal D'yi aç" to "TV:kanal d",
            "TRT 1 izle" to "TV:trt 1",
            "haber kanalını aç" to "TV:haber",
            "televizyonda TRT Haber aç" to "TV:trt haber",
            "televizyonu kapat" to "MEDYA:DURDUR",
            "sonraki kanal" to "MEDYA:SONRAKI",
            "TRT 1 aç" to "SARKI:trt 1",

            // ---------------- Saat, iptal
            "saat kaç" to "SAAT",
            "saat kaç oldu" to "SAAT",
            "saati söyler misin" to "SAAT",
            "şu an saat kaç" to "SAAT",
            "bugün günlerden ne" to "TARIH",
            "iptal" to "IPTAL",
            "iptal et" to "IPTAL",
            "vazgeç" to "IPTAL",
            "boş ver" to "IPTAL",
            "tamam" to "IPTAL",
            "kapat" to "IPTAL",

            // ---------------- Anlaşılmayanlar
            "" to "BILINMIYOR",
            "merhaba nasılsın" to "BILINMIYOR",
            "hava çok güzel" to "BILINMIYOR",
            "asdfgh" to "BILINMIYOR"
        ).map { arrayOf<Any>(it.first, it.second, testAdi(it.first, it.second)) }

        /** Rapor dosya adları için ASCII ad (Türkçe karakterli dosya adı bazı sistemlerde yazılamıyor). */
        private fun testAdi(cumle: String, beklenen: String): String =
            (TurkceMetin.katla(cumle) + " -> " + TurkceMetin.katla(beklenen)).replace(Regex("[^A-Za-z0-9 >#@=%:?-]"), "_")

        fun ayrinti(n: Niyet): String = when (n) {
            is Niyet.Ara -> "Ara(numara=${n.numara}, kisi=${n.kisi})"
            is Niyet.Navigasyon -> "Navigasyon(${n.hedef}, rota=${n.rotaBaslat})"
            else -> n.toString()
        }

        fun eslesir(n: Niyet, b: String): Boolean = when {
            b == "ARA?" -> n is Niyet.Ara && n.numara == null && n.kisi == null
            b.startsWith("ARA#") -> n is Niyet.Ara && n.numara == b.removePrefix("ARA#")
            b.startsWith("ARA:") -> n is Niyet.Ara && n.kisi?.adaylar?.contains(b.removePrefix("ARA:")) == true
            b == "NAV:EV" -> n is Niyet.Navigasyon && n.hedef == Hedef.Ev && n.rotaBaslat
            b == "NAV:IS" -> n is Niyet.Navigasyon && n.hedef == Hedef.Is && n.rotaBaslat
            b.startsWith("NAV:") -> n is Niyet.Navigasyon && n.rotaBaslat &&
                (n.hedef as? Hedef.Adres)?.sorgu == b.removePrefix("NAV:")
            b.startsWith("HARITA:") -> n is Niyet.Navigasyon && !n.rotaBaslat &&
                (n.hedef as? Hedef.Adres)?.sorgu == b.removePrefix("HARITA:")
            b.startsWith("MEDYA:") -> {
                val s = b.removePrefix("MEDYA:")
                if (n !is Niyet.Medya) false
                else if (s.contains("=")) {
                    val (e, d) = s.split("=")
                    val yuzde = d.startsWith("%")
                    n.eylem.name == e && n.deger == d.removePrefix("%").toInt() && n.yuzde == yuzde
                } else if (s.contains("/")) {
                    val (e, m) = s.split("/")
                    n.eylem.name == e && n.miktar.name == m
                } else {
                    n.eylem.name == s && n.deger == null && n.miktar == Miktar.NORMAL
                }
            }
            b.startsWith("SARKI:") || b.startsWith("ARAMA:") -> {
                val ara = b.startsWith("ARAMA:")
                val govde = b.substringAfter(":")
                val sorgu = govde.substringBefore("@")
                val uyg = if (govde.contains("@")) HedefUygulama.valueOf(govde.substringAfter("@")) else null
                n is Niyet.SarkiCal && n.sorgu == sorgu && n.uygulama == uyg && n.sadeceAra == ara
            }
            b.startsWith("UYGULAMA:") -> n is Niyet.UygulamaAc && n.ad == b.removePrefix("UYGULAMA:")
            b == "RADYO" -> n is Niyet.Radyo && n.istasyon == null
            b.startsWith("RADYO:") -> n is Niyet.Radyo && n.istasyon == b.removePrefix("RADYO:")
            b == "TV" -> n is Niyet.Televizyon && n.kanal == null
            b.startsWith("TV:") -> n is Niyet.Televizyon && n.kanal == b.removePrefix("TV:")
            b == "SAAT" -> n is Niyet.Saat && !n.tarih
            b == "TARIH" -> n is Niyet.Saat && n.tarih
            b == "IPTAL" -> n == Niyet.Iptal
            b == "BILINMIYOR" -> n is Niyet.Bilinmiyor
            else -> error("Bilinmeyen beklenti: $b")
        }
    }

    @Test
    fun cozumle() {
        val n = KomutCozucu.coz(cumle, UYGULAMALAR)
        assertTrue("\"$cumle\" için beklenen $beklenen, bulunan ${ayrinti(n)}", eslesir(n, beklenen))
    }
}

class KomutCozucuEkTest {
    @Test
    fun alternatiflerdenIlkAnlasilanSecilir() {
        val n = KomutCozucu.cozAdaylar(listOf("bla bla bla", "Ahmet'i ara"))
        assertTrue(n is Niyet.Ara && n.kisi?.adaylar?.contains("ahmet") == true)
    }

    @Test
    fun kisiSorusununCevabiIsimOlarakCozulur() {
        val n = KomutCozucu.kisiCevabiCoz("annem")
        assertTrue(n is Niyet.Ara && n.kisi?.adaylar?.contains("annem") == true)
        val n2 = KomutCozucu.kisiCevabiCoz("0532 123 45 67")
        assertTrue(n2 is Niyet.Ara && n2.numara == "05321234567")
        assertTrue(KomutCozucu.kisiCevabiCoz("vazgeç") == Niyet.Iptal)
    }

    @Test
    fun gorunenMetinBuyukHarfiKorur() {
        val n = KomutCozucu.coz("Marmara Park'a gideceğim")
        assertTrue(n is Niyet.Navigasyon && (n.hedef as Hedef.Adres).gorunen == "Marmara Park")
        val s = KomutCozucu.coz("Tarkan Şımarık aç")
        assertTrue(s is Niyet.SarkiCal && s.gorunen == "Tarkan Şımarık")
    }

    @Test
    fun uygulamaBulunamazsaSarkiOlabilir() {
        val n = KomutCozucu.coz("Şımarık'ı aç")
        assertTrue(n is Niyet.UygulamaAc && n.sarkiOlabilir && n.ad == "şımarık")
    }
}
