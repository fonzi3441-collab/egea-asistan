package com.egea.asistan.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Hey Egea" algılayıcısının gerçekçi sesle regresyon testi. Veri: resources/uyandirma (scripts/uyandirma_verisi.py
 * --regresyon ile üretildi): 2 sentetik konuşmacı (espeak-ng), her biri 4 öğretme kaydı, 9 "Hey Egea"/"Hey Ege"
 * (farklı hız/perde, 20 dB araç gürültüsü) ve en çok karışan 10 benzer söz ("Egea", "Ece", "Hey Emre"…).
 * Uygulamadaki akışın aynısı kullanılır: öğretme → sablonCikar, dinleme → 1600 örneklik parçalarla isle.
 */
class UyandirmaRegresyonTest {
    private val kok = File(javaClass.getResource("/uyandirma")!!.toURI())

    private fun dosyalar(k: File, alt: String) =
        File(k, alt).listFiles { f -> f.name.endsWith(".wav") }!!.sortedBy { it.name }

    private fun besle(d: UyandirmaDedektoru, pcm: ShortArray): Boolean {
        var bulundu = false
        var i = 0
        while (i < pcm.size) {
            val n = minOf(1600, pcm.size - i)
            if (d.isle(pcm.copyOfRange(i, i + n), n)) bulundu = true
            i += n
        }
        return bulundu
    }

    private class Sonuc(val dogru: Int, val pozitif: Int, val yanlis: Int, val negatif: Int, val ayrinti: String)

    private fun calistir(carpan: Float = UyandirmaDedektoru.VARSAYILAN_CARPAN): Sonuc {
        var dk = 0
        var pz = 0
        var yk = 0
        var ng = 0
        val ayrinti = StringBuilder()
        for (k in kok.listFiles { f -> f.isDirectory }!!.sortedBy { it.name }) {
            val sab = dosyalar(k, "ogret").map { UyandirmaDedektoru.sablonCikar(Wav.oku(it))!! }
            for (f in dosyalar(k, "pozitif")) {
                pz++
                val d = UyandirmaDedektoru(sab, carpan = carpan)
                if (besle(d, Wav.oku(f))) dk++ else ayrinti.append("kaçırıldı: ${k.name}/${f.name} oran=${d.sonOran}\n")
            }
            for (f in dosyalar(k, "negatif")) {
                ng++
                val d = UyandirmaDedektoru(sab, carpan = carpan)
                if (besle(d, Wav.oku(f))) {
                    yk++
                    ayrinti.append("yanlış kabul: ${k.name}/${f.name} oran=${d.sonOran}\n")
                }
            }
        }
        return Sonuc(dk, pz, yk, ng, ayrinti.toString())
    }

    @Test
    fun varsayilanDuyarlilik() {
        // Ölçülen (×1.6): 18/18 doğru kabul, 1/20 yanlış kabul ("Egea" tek başına). Önceki sürümün kuralı aynı
        // veride benzer sözlerin çoğunu kabul ediyordu.
        val s = calistir()
        println("Hey Egea regresyon: doğru kabul ${s.dogru}/${s.pozitif}, yanlış kabul ${s.yanlis}/${s.negatif}\n${s.ayrinti}")
        assertTrue("doğru kabul ${s.dogru}/${s.pozitif}\n${s.ayrinti}", s.dogru >= 16)
        assertTrue("yanlış kabul ${s.yanlis}/${s.negatif}\n${s.ayrinti}", s.yanlis <= 2)
    }

    @Test
    fun enSikiAyardaYanlisKabulYok() {
        val s = calistir(UyandirmaDedektoru.EN_SIKI_CARPAN)
        assertTrue("yanlış kabul ${s.yanlis}/${s.negatif}\n${s.ayrinti}", s.yanlis == 0)
    }

    @Test
    fun enGevsekAyardaHepsiAlgilanir() {
        val s = calistir(UyandirmaDedektoru.EN_GEVSEK_CARPAN)
        assertTrue("doğru kabul ${s.dogru}/${s.pozitif}\n${s.ayrinti}", s.dogru == s.pozitif)
    }
}
