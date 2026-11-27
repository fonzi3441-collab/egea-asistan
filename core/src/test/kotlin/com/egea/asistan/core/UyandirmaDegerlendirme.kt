package com.egea.asistan.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Gerçekçi sesle "Hey Egea" değerlendirmesi (geliştirici aracı; CI'da atlanır).
 *
 * EGEA_SES_VERISI=<kök1>[:<kök2>...] verilirse her kökte <konuşmacı>/{ogret,pozitif,negatif}/x.wav dosyalarıyla
 * (ogret: dosya adı sırasıyla 2 × "Hey Egea", 2 × "Hey Ege" — uygulamadaki sıra) uygulamadaki dedektörü aynı
 * akışla (1600 örneklik parçalarla isle) çalıştırır; farklı duyarlılık çarpanları için doğru kabul (pozitif) ve
 * yanlış kabul (negatif) oranlarını <ilk kök>/rapor.txt dosyasına yazar. Veri üretimi: scripts/uyandirma_verisi.py
 */
class UyandirmaDegerlendirme {
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

    /** Önceki sürümün kuralı (karşılaştırma için): CMN, eşik = tüm kayıtlar arası en büyük mesafe × 1.25 (en az 1). */
    private class EskiDedektor(sablonlar: List<List<FloatArray>>) {
        private val sab = sablonlar.map { UyandirmaDedektoru.cmn(it) }
        private val esik: Float
        private val mfcc = Mfcc()
        private val bolucu = KonusmaBolucu()

        init {
            var enBuyuk = 0f
            for (i in sab.indices) for (j in i + 1 until sab.size) enBuyuk = maxOf(enBuyuk, Dtw.mesafe(sab[i], sab[j]))
            esik = maxOf(enBuyuk * 1.25f, 1f)
        }

        fun besle(pcm: ShortArray): Boolean {
            val c = FloatArray(mfcc.pencere)
            var bas = 0
            var bulundu = false
            while (bas + mfcc.pencere <= pcm.size) {
                for (k in 0 until mfcc.pencere) c[k] = pcm[bas + k] / 32768f
                val oz = FloatArray(mfcc.boyut)
                val e = mfcc.cerceve(c, 0, oz)
                bolucu.ekle(oz, e)?.let { p ->
                    val q = UyandirmaDedektoru.cmn(p)
                    var enIyi = Float.MAX_VALUE
                    for (t in sab) {
                        val oran = q.size.toFloat() / t.size
                        if (oran < 0.55f || oran > 1.8f) continue
                        enIyi = minOf(enIyi, Dtw.mesafe(q, t))
                    }
                    if (enIyi <= esik) bulundu = true
                }
                bas += mfcc.adim
            }
            return bulundu
        }
    }

    @Test
    fun degerlendir() {
        val yollar = System.getenv("EGEA_SES_VERISI")
        assumeTrue("EGEA_SES_VERISI verilmedi", yollar != null)
        val kokler = yollar!!.split(':').map(::File)
        val rapor = StringBuilder()
        val carpanlar = listOf(1.4f, 1.5f, 1.6f, 1.8f)
        for (kok in kokler) {
            val konusmacilar = kok.listFiles { f -> f.isDirectory }!!.sortedBy { it.name }
            class Kayit(val sablonlar: List<List<FloatArray>>, val poz: List<Pair<String, ShortArray>>, val neg: List<Pair<String, ShortArray>>)
            val kayitlar = konusmacilar.map { k ->
                fun dosyalar(alt: String) = File(k, alt).listFiles { f -> f.name.endsWith(".wav") }!!.sortedBy { it.name }
                Kayit(
                    dosyalar("ogret").map { UyandirmaDedektoru.sablonCikar(Wav.oku(it)) ?: error("sessiz: $it") },
                    dosyalar("pozitif").map { it.nameWithoutExtension to Wav.oku(it) },
                    dosyalar("negatif").map { it.nameWithoutExtension to Wav.oku(it) }
                )
            }
            rapor.append("\n== ${kok.name}\n")
            run {
                var dk = 0; var pz = 0; var yk = 0; var ng = 0
                for (kayit in kayitlar) {
                    for ((_, pcm) in kayit.poz) { pz++; if (EskiDedektor(kayit.sablonlar).besle(pcm)) dk++ }
                    for ((_, pcm) in kayit.neg) { ng++; if (EskiDedektor(kayit.sablonlar).besle(pcm)) yk++ }
                }
                rapor.append(String.format("  ESKİ    doğru kabul %3d/%-3d (%3.0f%%)  yanlış kabul %3d/%-3d (%4.1f%%)\n",
                    dk, pz, 100.0 * dk / pz, yk, ng, 100.0 * yk / ng))
            }
            for (c in carpanlar) {
                var dk = 0
                var pz = 0
                var yk = 0
                var ng = 0
                val ykAd = HashMap<String, Int>()
                for (kayit in kayitlar) {
                    for ((_, pcm) in kayit.poz) {
                        pz++
                        if (besle(UyandirmaDedektoru(kayit.sablonlar, carpan = c), pcm)) dk++
                    }
                    for ((ad, pcm) in kayit.neg) {
                        ng++
                        if (besle(UyandirmaDedektoru(kayit.sablonlar, carpan = c), pcm)) {
                            yk++
                            val a = ad.substringAfter('_')
                            ykAd[a] = (ykAd[a] ?: 0) + 1
                        }
                    }
                }
                val enCok = ykAd.entries.sortedByDescending { it.value }.take(4).joinToString(",") { "${it.key}:${it.value}" }
                rapor.append(String.format("  ×%.2f  doğru kabul %3d/%-3d (%3.0f%%)  yanlış kabul %3d/%-3d (%4.1f%%) [%s]\n",
                    c, dk, pz, 100.0 * dk / pz, yk, ng, 100.0 * yk / ng, enCok))
            }
        }
        File(kokler[0], "rapor.txt").writeText(rapor.toString())
    }
}
