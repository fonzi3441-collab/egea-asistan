package com.egea.asistan.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * "Hey Egea" uyandırma kelimesi algılama — saf Kotlin, internetsiz, native kütüphanesiz.
 * Yöntem: 16 kHz ses → MFCC öznitelikleri → enerji tabanlı konuşma bölütleme (VAD) →
 * kullanıcının kaydettiği örneklerle DTW (dinamik zaman bükme) karşılaştırması.
 */

/** MFCC öznitelik çıkarıcı (25 ms pencere, 10 ms adım, 512 FFT, 26 mel filtre, c1..c12). */
class Mfcc(
    val ornekHizi: Int = 16000,
    val pencere: Int = 400,
    val adim: Int = 160,
    private val fftBoyu: Int = 512,
    private val filtreSayisi: Int = 26,
    private val katsayiSayisi: Int = 13
) {
    private val hamming = FloatArray(pencere) { (0.54 - 0.46 * cos(2.0 * PI * it / (pencere - 1))).toFloat() }
    private val re = FloatArray(fftBoyu)
    private val im = FloatArray(fftBoyu)
    private val guc = FloatArray(fftBoyu / 2 + 1)
    private val melEnerji = FloatArray(filtreSayisi)
    private val bitTers = IntArray(fftBoyu)
    private val cosT = FloatArray(fftBoyu / 2)
    private val sinT = FloatArray(fftBoyu / 2)
    private val filtreBas = IntArray(filtreSayisi)
    private val filtreAgirlik: Array<FloatArray>
    private val dct: Array<FloatArray>

    /** Çıktı boyutu (c0 hariç). */
    val boyut: Int get() = katsayiSayisi - 1

    init {
        val bitler = Integer.numberOfTrailingZeros(fftBoyu)
        for (i in 0 until fftBoyu) bitTers[i] = Integer.reverse(i) ushr (32 - bitler)
        for (i in 0 until fftBoyu / 2) {
            cosT[i] = cos(2.0 * PI * i / fftBoyu).toFloat()
            sinT[i] = -sin(2.0 * PI * i / fftBoyu).toFloat()
        }
        fun mel(f: Double) = 2595.0 * log10(1.0 + f / 700.0)
        fun hz(m: Double) = 700.0 * (Math.pow(10.0, m / 2595.0) - 1.0)
        val melAlt = mel(60.0)
        val melUst = mel(min(7600.0, ornekHizi / 2.0))
        val noktalar = DoubleArray(filtreSayisi + 2) { melAlt + (melUst - melAlt) * it / (filtreSayisi + 1) }
        val binler = IntArray(filtreSayisi + 2) { ((fftBoyu + 1) * hz(noktalar[it]) / ornekHizi).toInt() }
        filtreAgirlik = Array(filtreSayisi) { f ->
            val sol = binler[f]
            val orta = max(binler[f + 1], sol + 1)
            val sag = max(binler[f + 2], orta + 1)
            filtreBas[f] = sol
            FloatArray(sag - sol + 1) { k ->
                val b = sol + k
                when {
                    b < orta -> (b - sol).toFloat() / (orta - sol)
                    else -> (sag - b).toFloat() / (sag - orta)
                }
            }
        }
        dct = Array(katsayiSayisi) { n ->
            FloatArray(filtreSayisi) { f -> cos(PI * n * (f + 0.5) / filtreSayisi).toFloat() }
        }
    }

    private fun fft() {
        for (i in 0 until fftBoyu) {
            val j = bitTers[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var boy = 2
        while (boy <= fftBoyu) {
            val yarim = boy / 2
            val adimT = fftBoyu / boy
            var i = 0
            while (i < fftBoyu) {
                var k = 0
                for (j in i until i + yarim) {
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val xr = re[j + yarim] * wr - im[j + yarim] * wi
                    val xi = re[j + yarim] * wi + im[j + yarim] * wr
                    re[j + yarim] = re[j] - xr
                    im[j + yarim] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += adimT
                }
                i += boy
            }
            boy *= 2
        }
    }

    /**
     * [kaynak] içinde [bas]'tan başlayan [pencere] örneklik çerçevenin MFCC'si (c1..c12) ve
     * çerçeve enerjisi (dB). Örnekler -1..1 aralığında.
     */
    fun cerceve(kaynak: FloatArray, bas: Int, cikti: FloatArray): Float {
        var enerji = 0.0
        for (i in 0 until pencere) {
            val x = kaynak[bas + i]
            enerji += x * x
        }
        val enerjiDb = (10.0 * log10(enerji / pencere + 1e-10)).toFloat()
        var onceki = kaynak[bas]
        for (i in 0 until pencere) {
            val x = kaynak[bas + i]
            re[i] = (x - 0.97f * onceki) * hamming[i]
            onceki = x
            im[i] = 0f
        }
        for (i in pencere until fftBoyu) {
            re[i] = 0f
            im[i] = 0f
        }
        fft()
        for (k in guc.indices) guc[k] = re[k] * re[k] + im[k] * im[k]
        for (f in 0 until filtreSayisi) {
            val w = filtreAgirlik[f]
            val b0 = filtreBas[f]
            var t = 0f
            for (k in w.indices) {
                val b = b0 + k
                if (b < guc.size) t += w[k] * guc[b]
            }
            melEnerji[f] = ln(max(t, 1e-10f))
        }
        for (n in 1 until katsayiSayisi) {
            val d = dct[n]
            var t = 0f
            for (f in 0 until filtreSayisi) t += d[f] * melEnerji[f]
            cikti[n - 1] = t
        }
        return enerjiDb
    }
}

/**
 * Enerjiye dayalı konuşma bölütleyici. Gürültü tabanını izler; tabanın [esikDb] üstünde başlayan ve
 * [sessizlikCerceve] boyunca sessizlikle biten parçaları döndürür. Çok uzun parçalar (müzik vb.) atılır.
 */
class KonusmaBolucu(
    var esikDb: Float = 10f,
    private val minCerceve: Int = 25,
    private val maxCerceve: Int = 260,
    private val sessizlikCerceve: Int = 22,
    private val onCerceve: Int = 8
) {
    private var taban = Float.NaN
    private val onBellek = ArrayDeque<FloatArray>()
    private var konusma: MutableList<FloatArray>? = null
    private var sessizSayac = 0
    private var yuksekSayac = 0

    val gurultuTabani: Float get() = taban

    /** Bir çerçeve ekler; biten bir konuşma parçası varsa onu döndürür. */
    fun ekle(ozellik: FloatArray, enerjiDb: Float): List<FloatArray>? {
        if (taban.isNaN()) taban = enerjiDb
        val aktif = konusma
        if (aktif == null) {
            taban = if (enerjiDb < taban) 0.9f * taban + 0.1f * enerjiDb else 0.995f * taban + 0.005f * enerjiDb
            onBellek.addLast(ozellik)
            while (onBellek.size > onCerceve) onBellek.removeFirst()
            if (enerjiDb > taban + esikDb) {
                yuksekSayac++
                if (yuksekSayac >= 3) {
                    konusma = ArrayList(onBellek)
                    onBellek.clear()
                    sessizSayac = 0
                }
            } else {
                yuksekSayac = 0
            }
            return null
        }
        aktif.add(ozellik)
        if (enerjiDb < taban + esikDb - 3f) sessizSayac++ else sessizSayac = 0
        if (aktif.size > maxCerceve) {
            // Çok uzun: büyük olasılıkla müzik/sürekli konuşma. Tabanı yükselt, parçayı at.
            konusma = null
            yuksekSayac = 0
            taban = 0.7f * taban + 0.3f * enerjiDb
            return null
        }
        if (sessizSayac >= sessizlikCerceve) {
            konusma = null
            yuksekSayac = 0
            val son = max(0, aktif.size - sessizlikCerceve + 4)
            val parca = aktif.subList(0, son)
            return if (parca.size >= minCerceve) ArrayList(parca) else null
        }
        return null
    }

    /** Kayıt bittiğinde yarım kalan parçayı döndürür (öğretme kayıtları için). */
    fun bitir(): List<FloatArray>? {
        val aktif = konusma ?: return null
        konusma = null
        val son = max(0, aktif.size - min(sessizSayac, aktif.size) + 4).coerceAtMost(aktif.size)
        val parca = aktif.subList(0, son)
        return if (parca.size >= minCerceve) ArrayList(parca) else null
    }
}

/** Dinamik zaman bükme (Sakoe-Chiba bandıyla), uzunluğa göre normalize mesafe. */
object Dtw {
    private fun oklid(a: FloatArray, b: FloatArray): Float {
        var t = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            t += d * d
        }
        return sqrt(t)
    }

    fun mesafe(a: List<FloatArray>, b: List<FloatArray>, bantOrani: Float = 0.35f): Float {
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0) return Float.MAX_VALUE
        val bant = max(abs(n - m), (max(n, m) * bantOrani).toInt()) + 2
        var onceki = FloatArray(m + 1) { Float.MAX_VALUE }
        var simdi = FloatArray(m + 1) { Float.MAX_VALUE }
        onceki[0] = 0f
        for (i in 1..n) {
            simdi.fill(Float.MAX_VALUE)
            val merkez = (i.toLong() * m / n).toInt()
            val jBas = max(1, merkez - bant)
            val jSon = min(m, merkez + bant)
            for (j in jBas..jSon) {
                val enIyi = min(onceki[j], min(simdi[j - 1], onceki[j - 1]))
                if (enIyi != Float.MAX_VALUE) simdi[j] = enIyi + oklid(a[i - 1], b[j - 1])
            }
            val g = onceki
            onceki = simdi
            simdi = g
        }
        val toplam = onceki[m]
        return if (toplam == Float.MAX_VALUE) Float.MAX_VALUE else toplam / (n + m)
    }
}

/**
 * Uyandırma kelimesi dedektörü. PCM 16-bit örnekleri parça parça besleyin; kelime algılanınca true döner.
 * [sablonlar]: kullanıcının kaydettiği örneklerin (CMN uygulanmış) MFCC dizileri.
 */
class UyandirmaDedektoru(
    var sablonlar: List<List<FloatArray>>,
    var esik: Float,
    esikDb: Float = 10f
) {
    private val mfcc = Mfcc()
    private val bolucu = KonusmaBolucu(esikDb)
    private val halka = FloatArray(mfcc.pencere)
    private var yaz = 0
    private var dolu = 0
    private var yeni = 0
    private val cerceve = FloatArray(mfcc.pencere)

    /** Son değerlendirilen parçanın en küçük DTW mesafesi (tanı/öğretme ekranı için). */
    @Volatile
    var sonMesafe: Float = Float.NaN
        private set

    @Volatile
    var sonSeviyeDb: Float = -100f
        private set

    val gurultuTabani: Float get() = bolucu.gurultuTabani

    var duyarlilikDb: Float
        get() = bolucu.esikDb
        set(v) {
            bolucu.esikDb = v
        }

    fun isle(ornekler: ShortArray, adet: Int): Boolean {
        var bulundu = false
        for (i in 0 until adet) {
            halka[yaz] = ornekler[i] / 32768f
            yaz = (yaz + 1) % halka.size
            if (dolu < halka.size) dolu++
            yeni++
            if (dolu == halka.size && yeni >= mfcc.adim) {
                yeni = 0
                for (k in 0 until halka.size) cerceve[k] = halka[(yaz + k) % halka.size]
                val oz = FloatArray(mfcc.boyut)
                val e = mfcc.cerceve(cerceve, 0, oz)
                sonSeviyeDb = e
                val parca = bolucu.ekle(oz, e)
                if (parca != null && eslesir(parca)) bulundu = true
            }
        }
        return bulundu
    }

    fun eslesir(parca: List<FloatArray>): Boolean {
        val p = cmn(parca)
        var enIyi = Float.MAX_VALUE
        for (s in sablonlar) {
            val oran = p.size.toFloat() / s.size
            if (oran < 0.55f || oran > 1.8f) continue
            enIyi = min(enIyi, Dtw.mesafe(p, s))
        }
        sonMesafe = enIyi
        return enIyi <= esik
    }

    companion object {
        /** Ortalama çıkarma (cepstral mean normalization). */
        fun cmn(dizi: List<FloatArray>): List<FloatArray> {
            if (dizi.isEmpty()) return dizi
            val b = dizi[0].size
            val ort = FloatArray(b)
            for (v in dizi) for (i in 0 until b) ort[i] += v[i]
            for (i in 0 until b) ort[i] /= dizi.size
            return dizi.map { v -> FloatArray(b) { i -> v[i] - ort[i] } }
        }

        /** Bir öğretme kaydından (PCM 16 kHz) şablon çıkarır: en uzun konuşma parçası. Ses yoksa null. */
        fun sablonCikar(pcm: ShortArray, esikDb: Float = 10f): List<FloatArray>? {
            val m = Mfcc()
            val bolucu = KonusmaBolucu(esikDb, minCerceve = 20, maxCerceve = 400, sessizlikCerceve = 20)
            val cerceve = FloatArray(m.pencere)
            var enUzun: List<FloatArray>? = null
            var bas = 0
            while (bas + m.pencere <= pcm.size) {
                for (k in 0 until m.pencere) cerceve[k] = pcm[bas + k] / 32768f
                val oz = FloatArray(m.boyut)
                val e = m.cerceve(cerceve, 0, oz)
                bolucu.ekle(oz, e)?.let { if (it.size > (enUzun?.size ?: 0)) enUzun = it }
                bas += m.adim
            }
            bolucu.bitir()?.let { if (it.size > (enUzun?.size ?: 0)) enUzun = it }
            return enUzun?.let { cmn(it) }
        }

        /** Şablonlar arası en büyük DTW mesafesi (eşik bunun bir katı alınır). */
        fun tabanMesafe(sablonlar: List<List<FloatArray>>): Float {
            var enBuyuk = 0f
            for (i in sablonlar.indices) for (j in i + 1 until sablonlar.size) {
                val d = Dtw.mesafe(sablonlar[i], sablonlar[j])
                if (d != Float.MAX_VALUE) enBuyuk = max(enBuyuk, d)
            }
            return enBuyuk
        }
    }
}
