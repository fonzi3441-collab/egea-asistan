package com.egea.asistan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class M3uOkuyucuTest {
    private val ornek = """
        #EXTM3U
        #EXTINF:-1 tvg-id="TRT1.tr@SD" tvg-logo="https://i.imgur.com/trt1.png" group-title="General",TRT 1 (1440p)
        https://tv-trt1.medya.trt.com.tr/master.m3u8
        #EXTINF:-1 tvg-id="KanalD.tr@SD" tvg-logo="https://x/kanald.png" group-title="General",Kanal D (Turkiye) (1080p)
        https://demiroren.example/kanald.m3u8
        #EXTINF:-1 tvg-id="Haberturk.tr" group-title="News",Haberturk TV (720p) [Not 24/7]
        #EXTVLCOPT:http-user-agent=Mozilla/5.0 Test
        #EXTVLCOPT:http-referrer=https://haberturk.example/
        https://ht.example/live.m3u8
        #EXTINF:-1 group-title="Kids, Çocuk",Spacetoon
        https://st.example/a.m3u8|User-Agent=Kodi%2F20&Referer=https://spacetoon.example/
        #EXTINF:-1,Bozuk satır
        #yorum
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun kanallarVeOzniteliklerOkunur() {
        val k = M3uOkuyucu.oku(ornek)
        assertEquals(4, k.size)
        assertEquals("TRT 1", k[0].ad)
        assertEquals("https://tv-trt1.medya.trt.com.tr/master.m3u8", k[0].url)
        assertEquals("https://i.imgur.com/trt1.png", k[0].logo)
        assertEquals("General", k[0].grup)
        assertEquals("Kanal D", k[1].ad)
        assertEquals(listOf("Turkiye", "1080p"), k[1].etiketler)
        assertEquals("Haberturk TV", k[2].ad)
        assertEquals("Mozilla/5.0 Test", k[2].basliklar["User-Agent"])
        assertEquals("https://haberturk.example/", k[2].basliklar["Referer"])
        assertTrue("Not 24/7" in k[2].etiketler)
        assertEquals("Kids, Çocuk", k[3].grup)
        assertEquals("https://st.example/a.m3u8", k[3].url)
        assertEquals("Kodi/20", k[3].basliklar["User-Agent"])
    }
}

class AdEslestiriciTest {
    private val radyolar = listOf("Power POP", "Süper FM", "Virgin Radio Türkiye", "KRAL FM", "JOY TÜRK TURKEY", "Joy FM", "Power FM", "Metro FM", "Radyo Fenomen")
    private val kanallar = listOf("TRT 1", "TRT 3", "TRT Haber", "Kanal D", "Show TV", "TV8", "Star TV", "ATV", "A Haber")

    private fun r(s: String) = AdEslestirici.enIyi(s, radyolar)?.let { radyolar[it.first] }
    private fun k(s: String) = AdEslestirici.enIyi(s, kanallar, 0.8)?.let { kanallar[it.first] }

    @Test
    fun radyolar() {
        assertEquals("KRAL FM", r("kral fm"))
        assertEquals("KRAL FM", r("kral"))
        assertEquals("JOY TÜRK TURKEY", r("joy türk"))
        assertEquals("Power FM", r("power fm"))
        assertEquals("Virgin Radio Türkiye", r("virgin radio"))
        assertEquals("Süper FM", r("super fm"))
        assertEquals("Radyo Fenomen", r("fenomen"))
        assertNull(r("tamamen alakasız"))
    }

    @Test
    fun kanallar() {
        assertEquals("TRT 1", k("trt 1"))
        assertEquals("TRT 1", k("trt bir"))
        assertEquals("TV8", k("tv 8"))
        assertEquals("TV8", k("tv sekiz"))
        assertEquals("Show TV", k("show tv"))
        assertEquals("Show TV", k("show"))
        assertEquals("Kanal D", k("kanal d"))
        assertEquals("TRT Haber", k("trt haber"))
        assertNull(k("trt 5"))
    }
}

/** Sentetik "kelime": art arda iki tonlu bölümler (formant benzeri) + gürültü. */
private object Sentez {
    fun kelime(
        bolumler: List<Triple<Double, Double, Double>>,
        uzatma: Double = 1.0,
        tohum: Int = 1,
        onSessizlik: Double = 0.5,
        sonSessizlik: Double = 0.6
    ): ShortArray {
        val sr = 16000
        val rnd = Random(tohum)
        val ornekler = ArrayList<Short>()
        fun gurultu(sn: Double) {
            repeat((sn * sr).toInt()) { ornekler += ((rnd.nextDouble() - 0.5) * 120).toInt().toShort() }
        }
        gurultu(onSessizlik)
        var faz1 = 0.0
        var faz2 = 0.0
        for ((f1, f2, sure) in bolumler) {
            val n = (sure * uzatma * sr).toInt()
            for (i in 0 until n) {
                val zarf = sin(PI * i / n).coerceAtLeast(0.25)
                faz1 += 2 * PI * f1 / sr
                faz2 += 2 * PI * f2 / sr
                val v = (sin(faz1) * 0.6 + sin(faz2) * 0.35) * zarf * 9000 + (rnd.nextDouble() - 0.5) * 120
                ornekler += v.toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        gurultu(sonSessizlik)
        return ornekler.toShortArray()
    }

    val HEY_EGEA = listOf(Triple(300.0, 2300.0, 0.18), Triple(500.0, 1800.0, 0.16), Triple(420.0, 2200.0, 0.2), Triple(700.0, 1200.0, 0.22))
    val BASKA = listOf(Triple(900.0, 1100.0, 0.2), Triple(250.0, 600.0, 0.2), Triple(1200.0, 2800.0, 0.2), Triple(350.0, 900.0, 0.2))
}

class UyandirmaTest {
    @Test
    fun bolucuTekParcaBulur() {
        val pcm = Sentez.kelime(Sentez.HEY_EGEA)
        val s = UyandirmaDedektoru.sablonCikar(pcm)
        assertNotNull(s)
        // Kelime ~0.76 sn → ~76 çerçeve
        assertTrue("çerçeve sayısı ${s!!.size}", s.size in 55..110)
    }

    @Test
    fun basindakiSayisalSessizlikSablonuBozmaz() {
        // AudioRecord kaydın başında sıfır verebilir. Eskiden gürültü tabanı ilk çerçeveden (-100 dB) alınıyor,
        // ardından gelen gürültü "konuşma" sanılıyor ve şablon kaydın tamamı oluyordu.
        val pcm = ShortArray(3200) + Sentez.kelime(Sentez.HEY_EGEA)
        val s = UyandirmaDedektoru.sablonCikar(pcm)
        assertNotNull(s)
        assertTrue("çerçeve sayısı ${s!!.size}", s.size in 55..110)
    }

    @Test
    fun bozukUzunSablonAyiklanir() {
        assertEquals(listOf(0, 1, 3), UyandirmaDedektoru.saglamSablonlar(listOf(84, 93, 147, 88)))
        assertEquals(listOf(0, 1, 2, 3), UyandirmaDedektoru.saglamSablonlar(listOf(70, 100, 82, 95)))
        assertEquals(listOf(0, 1, 3), UyandirmaDedektoru.saglamSablonlar(listOf(80, 85, 300, 90)))
        // En az iki şablon kalır.
        assertEquals(listOf(0, 1), UyandirmaDedektoru.saglamSablonlar(listOf(80, 300)))
    }

    @Test
    fun ayniKelimeYakinFarkliKelimeUzak() {
        val a = UyandirmaDedektoru.sablonCikar(Sentez.kelime(Sentez.HEY_EGEA, 1.0, 1))!!
        val b = UyandirmaDedektoru.sablonCikar(Sentez.kelime(Sentez.HEY_EGEA, 1.15, 2))!!
        val c = UyandirmaDedektoru.sablonCikar(Sentez.kelime(Sentez.BASKA, 1.0, 3))!!
        val ayni = Dtw.mesafe(a, b)
        val farkli = Dtw.mesafe(a, c)
        assertTrue("aynı=$ayni farklı=$farkli", ayni < farkli * 0.6f)
    }

    @Test
    fun dedektorAkistaAlgilar() {
        val sablonlar = listOf(0.9, 1.0, 1.1).mapIndexed { i, u ->
            UyandirmaDedektoru.sablonCikar(Sentez.kelime(Sentez.HEY_EGEA, u, 10 + i))!!
        }
        // Üç kayıt da aynı söz (grup 0).
        val dedektor = UyandirmaDedektoru(sablonlar, IntArray(sablonlar.size))

        fun besle(pcm: ShortArray): Boolean {
            var bulundu = false
            var i = 0
            while (i < pcm.size) {
                val n = minOf(1600, pcm.size - i)
                if (dedektor.isle(pcm.copyOfRange(i, i + n), n)) bulundu = true
                i += n
            }
            return bulundu
        }
        assertTrue("uyandırma kelimesi algılanmalı (oran ${dedektor.sonOran}, eşik ${dedektor.esikler.toList()})",
            besle(Sentez.kelime(Sentez.HEY_EGEA, 1.05, 42)))
        assertTrue("başka kelime algılanmamalı (oran ${dedektor.sonOran})", !besle(Sentez.kelime(Sentez.BASKA, 1.0, 43)))
    }
}
