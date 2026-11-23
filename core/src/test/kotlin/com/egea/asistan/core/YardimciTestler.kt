package com.egea.asistan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkceMetinTest {
    @Test
    fun normalizeTurkceHarfleriDogruCevirir() {
        assertEquals("istanbul'a", TurkceMetin.normalize("İSTANBUL'A"))
        assertEquals("ışık", TurkceMetin.normalize("IŞIK"))
        assertEquals("marmara park'a gideceğim", TurkceMetin.normalize("Marmara Park’a gideceğim!"))
        assertEquals("sesi yüzde 50 yap", TurkceMetin.normalize("Sesi %50 yap."))
        assertEquals("i", TurkceMetin.normalize("i\u0307"))
    }

    @Test
    fun katlama() {
        assertEquals("simarik cigdem ogur", TurkceMetin.katla("Şımarık Çiğdem Öğür"))
    }

    @Test
    fun soruEki() {
        assertEquals("mı", TurkceMetin.soruEki("Yılmaz"))
        assertEquals("mi", TurkceMetin.soruEki("Demir"))
        assertEquals("mu", TurkceMetin.soruEki("Uğur"))
        assertEquals("mü", TurkceMetin.soruEki("Gül"))
        assertEquals("mı", TurkceMetin.soruEki("Ahmet Kaya"))
    }

    @Test
    fun yonelmeEkiEkle() {
        assertEquals("Marmara Park'a", TurkceMetin.yonelmeEkiEkle("Marmara Park"))
        assertEquals("Kadıköy'e", TurkceMetin.yonelmeEkiEkle("Kadıköy"))
        assertEquals("Ankara'ya", TurkceMetin.yonelmeEkiEkle("Ankara"))
        assertEquals("Esenyurt'a", TurkceMetin.yonelmeEkiEkle("Esenyurt"))
        assertEquals("İzmir'e", TurkceMetin.yonelmeEkiEkle("İzmir"))
        assertEquals("Maltepe'ye", TurkceMetin.yonelmeEkiEkle("Maltepe"))
        assertEquals("Üsküdar'a", TurkceMetin.yonelmeEkiEkle("Üsküdar"))
        assertEquals("AVM'ye", TurkceMetin.yonelmeEkiEkle("AVM"))
        assertEquals("Kat 5'e", TurkceMetin.yonelmeEkiEkle("Kat 5"))
    }
}

class EklerTest {
    @Test
    fun yonelmeEki() {
        assertEquals("park", Ekler.yonelme("parka").kok)
        assertEquals("park", Ekler.yonelme("park'a").kok)
        assertEquals("kadıköy", Ekler.yonelme("kadıköye").kok)
        assertEquals("kadıköy", Ekler.yonelme("kadıköy'e").kok)
        assertEquals("ankara", Ekler.yonelme("ankaraya").kok)
        assertEquals("ev", Ekler.yonelme("eve").kok)
        assertEquals("parkı", Ekler.yonelme("parkına").kok)
        assertEquals("benzinlik", Ekler.yonelme("benzinliğe").kok)
        assertEquals("bursa", Ekler.yonelme("bursa", guvenli = true).kok)
        assertEquals("ankara", Ekler.yonelme("ankaraya", guvenli = true).kok)
    }

    @Test
    fun belirtmeEkiAdaylari() {
        assertTrue(Ekler.belirtmeAdaylari("ahmeti").any { it.kok == "ahmet" })
        assertTrue(Ekler.belirtmeAdaylari("ayşeyi").any { it.kok == "ayşe" })
        assertEquals(listOf("ali"), Ekler.belirtmeAdaylari("ali").map { it.kok })
        assertTrue(Ekler.belirtmeAdaylari("annemi").any { it.kok == "annem" })
        assertEquals(listOf("ahmet"), Ekler.belirtmeAdaylari("ahmet'i").map { it.kok })
        assertTrue(Ekler.belirtmeAdaylari("işyerini").any { it.kok == "işyeri" })
        assertTrue(Ekler.belirtmeAdaylari("hüseyini").any { it.kok == "hüseyin" })
        assertTrue(Ekler.belirtmeAdaylari("benzinliği").any { it.kok == "benzinlik" })
    }

    @Test
    fun iyelikTabanlari() {
        assertTrue("anne" in Ekler.iyelikTabanlari("annem"))
        assertTrue("baba" in Ekler.iyelikTabanlari("babam"))
        assertTrue("kardeş" in Ekler.iyelikTabanlari("kardeşim"))
    }

    @Test
    fun hamMetneUygulama() {
        assertEquals("Park", Ekler.yonelme("park'a").hamaUygula("Park'a"))
        assertEquals("Benzinlik", Ekler.yonelme("benzinliğe").hamaUygula("Benzinliğe"))
        assertEquals("Kadıköy", Ekler.yonelme("kadıköye").hamaUygula("Kadıköye"))
    }
}

class SayilarTest {
    @Test
    fun yaziylaSayilar() {
        assertEquals(15L, Sayilar.ilkSayi(listOf("on", "beş")))
        assertEquals(123L, Sayilar.ilkSayi(listOf("yüz", "yirmi", "üç")))
        assertEquals(2500L, Sayilar.ilkSayi(listOf("iki", "bin", "beş", "yüz")))
        assertEquals(15L, Sayilar.ilkSayi(listOf("on", "beşe")))
        assertEquals(4L, Sayilar.ilkSayi(listOf("dörde")))
        assertEquals(20L, Sayilar.ilkSayi(listOf("yirmiye")))
        assertEquals(15L, Sayilar.ilkSayi(listOf("15'e")))
        assertNull(Sayilar.parca("yüzde"))
        assertNull(Sayilar.parca("ahmet"))
    }

    @Test
    fun telefonNumarasi() {
        assertEquals("05321234567", Sayilar.telefonNumarasi(listOf("0532", "123", "45", "67'yi")))
        assertEquals(
            "05321234567",
            Sayilar.telefonNumarasi("sıfır beş yüz otuz iki yüz yirmi üç kırk beş altmış yedi".split(" "))
        )
        assertEquals("05001112233", Sayilar.telefonNumarasi("sıfır beş sıfır sıfır yüz on bir yirmi iki otuz üç".split(" ")))
        assertNull(Sayilar.telefonNumarasi(listOf("ahmet")))
        assertNull(Sayilar.telefonNumarasi(listOf("on")))
    }

    @Test
    fun sayiOkunuslari() {
        assertEquals("sıfır", Sayilar.yaziyla(0))
        assertEquals("on dört", Sayilar.yaziyla(14))
        assertEquals("yüz", Sayilar.yaziyla(100))
        assertEquals("bin iki yüz elli", Sayilar.yaziyla(1250))
        assertEquals("iki bin yirmi altı", Sayilar.yaziyla(2026))
        assertEquals("bir milyon", Sayilar.yaziyla(1_000_000))
    }
}

class KisiEslestiriciTest {
    private val rehber = listOf("Ahmet Yılmaz", "Ahmet Kaya", "Mehmet Demir", "Annem", "Babam", "Ayşe Güler", "Ali")

    @Test
    fun ayniIsimdeIkiKisiVarsaIkisiDeDoner() {
        assertEquals(listOf(0, 1), KisiEslestirici.eslestir(listOf("ahmet"), rehber))
    }

    @Test
    fun tamIsimTekKisi() {
        assertEquals(listOf(0), KisiEslestirici.eslestir(listOf("ahmet yılmaz"), rehber))
        assertEquals(listOf(0), KisiEslestirici.eslestir(listOf("yılmaz"), rehber))
        assertEquals(listOf(3), KisiEslestirici.eslestir(listOf("annemi", "annem", "anne"), rehber))
        assertEquals(listOf(5), KisiEslestirici.eslestir(listOf("ayse"), rehber))
        assertEquals(listOf(6), KisiEslestirici.eslestir(listOf("ali", "al"), rehber))
    }

    @Test
    fun bulanikEslesme() {
        assertEquals(listOf(2), KisiEslestirici.eslestir(listOf("mehmed"), rehber))
        assertTrue(KisiEslestirici.eslestir(listOf("xyz"), rehber).isEmpty())
    }
}

class SecimCozucuTest {
    private val iki = listOf("Ahmet Yılmaz", "Ahmet Kaya")

    @Test
    fun soyadiylaSecim() {
        assertEquals(Secim.Indeks(0), SecimCozucu.coz("Yılmaz", iki))
        assertEquals(Secim.Indeks(1), SecimCozucu.coz("Kaya olan", iki))
        assertEquals(Secim.Indeks(1), SecimCozucu.coz("Ahmet Kaya'yı ara", iki))
    }

    @Test
    fun siraIleSecim() {
        assertEquals(Secim.Indeks(1), SecimCozucu.coz("ikincisi", iki))
        assertEquals(Secim.Indeks(0), SecimCozucu.coz("ilki", iki))
        assertEquals(Secim.Indeks(2), SecimCozucu.coz("sonuncu", iki + "Ahmet Demir"))
    }

    @Test
    fun iptalVeBelirsiz() {
        assertEquals(Secim.Iptal, SecimCozucu.coz("iptal", iki))
        assertEquals(Secim.Anlasilmadi, SecimCozucu.coz("Ahmet", iki))
    }
}

class UygulamaEslestiriciTest {
    private val uygulamalar = listOf(
        UygulamaEslestirici.Uygulama("YouTube", "com.google.android.youtube"),
        UygulamaEslestirici.Uygulama("YouTube Music", "com.google.android.apps.youtube.music"),
        UygulamaEslestirici.Uygulama("Spotify", "com.spotify.music"),
        UygulamaEslestirici.Uygulama("Haritalar", "com.google.android.apps.maps"),
        UygulamaEslestirici.Uygulama("Radio", "com.syu.radio"),
        UygulamaEslestirici.Uygulama("BT Phone", "com.syu.bt"),
        UygulamaEslestirici.Uygulama("Ayarlar", "com.android.settings"),
        UygulamaEslestirici.Uygulama("Chrome", "com.android.chrome")
    )

    private fun bul(s: String) = UygulamaEslestirici.bul(s, uygulamalar)?.etiket

    @Test
    fun dogrudanVeTakmaAdla() {
        assertEquals("YouTube", bul("youtube"))
        assertEquals("YouTube Music", bul("youtube müzik"))
        assertEquals("Spotify", bul("spotify"))
        assertEquals("Haritalar", bul("harita"))
        assertEquals("Radio", bul("radyo"))
        assertEquals("BT Phone", bul("telefon"))
        assertEquals("Ayarlar", bul("ayarlar"))
        assertEquals("Chrome", bul("tarayıcı"))
        assertNull(bul("netflix"))
    }
}

class VcfOkuyucuTest {
    @Test
    fun vcard21Ve30Okunur() {
        val vcf = """
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Y=C4=B1lmaz;Ahmet;;;
            FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Ahmet Y=C4=B1lmaz
            TEL;CELL:0532 123 45 67
            TEL;HOME:0212 555 00 00
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            N:Kaya;Ayşe;;;
            TEL;TYPE=CELL:+905551112233
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Mehmet D
             emir
            item1.TEL;TYPE=WORK:0216 444 44 44
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Numarasız
            END:VCARD
        """.trimIndent().replace("\n", "\r\n")
        val kisiler = VcfOkuyucu.oku(vcf)
        assertEquals(3, kisiler.size)
        assertEquals("Ahmet Yılmaz", kisiler[0].ad)
        assertEquals(listOf("05321234567", "02125550000"), kisiler[0].numaralar.map { it.numara })
        assertEquals("cep", kisiler[0].numaralar[0].tur)
        assertEquals("Ayşe Kaya", kisiler[1].ad)
        assertEquals("+905551112233", kisiler[1].numaralar[0].numara)
        assertEquals("Mehmet Demir", kisiler[2].ad)
        assertEquals("iş", kisiler[2].numaralar[0].tur)
    }
}

class CevaplarTest {
    @Test
    fun cumleler() {
        assertEquals("Ahmet Yılmaz mı, Ahmet Kaya mı?", Cevaplar.secimSorusu(listOf("Ahmet Yılmaz", "Ahmet Kaya")))
        assertEquals("Marmara Park'a rota başlatıyorum", Cevaplar.rota(Hedef.Adres("marmara park", "Marmara Park")))
        assertEquals("Eve rota başlatıyorum", Cevaplar.rota(Hedef.Ev))
        assertEquals("Saat on dört otuz beş", Cevaplar.saat(14, 35))
        assertEquals("Saat dokuz sıfır beş", Cevaplar.saat(9, 5))
        assertEquals("Saat on yedi", Cevaplar.saat(17, 0))
        assertEquals("0532, 123, 45, 67", Cevaplar.numaraSeslendir("05321234567"))
        assertEquals("Bugün yirmi üç Kasım, Pazartesi", Cevaplar.tarih(23, 11, 1))
    }
}
