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
 *
 * Gürültü tabanı ilk [isinmaCerceve] çerçevenin ortalamasıyla başlar (tek çerçeveyle değil) ve sayısal sessizlik
 * (AudioRecord'un başta verdiği sıfırlar) tahmine katılmaz. Eskiden taban ilk çerçeveden alınıp çok yavaş
 * yükseliyordu; ilk çerçeve sessizse gürültü "konuşma" sanılıyor, öğretme şablonu kaydın tamamı oluyordu.
 */
class KonusmaBolucu(
    var esikDb: Float = 10f,
    private val minCerceve: Int = 25,
    private val maxCerceve: Int = 260,
    private val sessizlikCerceve: Int = 22,
    private val onCerceve: Int = 8,
    private val isinmaCerceve: Int = 10
) {
    private var taban = Float.NaN
    private var isinma = 0
    private var isinmaToplam = 0f
    private val onBellek = ArrayDeque<FloatArray>()
    private var konusma: MutableList<FloatArray>? = null
    private var sessizSayac = 0
    private var yuksekSayac = 0
    private var enerjiToplam = 0f
    private var enerjiAdet = 0

    val gurultuTabani: Float get() = taban

    /** Son döndürülen parçadaki konuşma çerçevelerinin ortalama enerjisinin gürültü tabanından farkı (dB). */
    var sonSnrDb: Float = Float.NaN
        private set

    /** Bir çerçeve ekler; biten bir konuşma parçası varsa onu döndürür. */
    fun ekle(ozellik: FloatArray, enerjiDb: Float): List<FloatArray>? {
        val sayisalSessizlik = enerjiDb < SAYISAL_SESSIZLIK_DB
        if (isinma < isinmaCerceve) {
            // Isınma: yalnız gürültü tabanını öğren, konuşma başlatma.
            onBellek.addLast(ozellik)
            while (onBellek.size > onCerceve) onBellek.removeFirst()
            if (!sayisalSessizlik) {
                isinmaToplam += enerjiDb
                isinma++
                taban = isinmaToplam / isinma
            }
            return null
        }
        val aktif = konusma
        if (aktif == null) {
            if (!sayisalSessizlik) {
                taban = if (enerjiDb < taban) 0.9f * taban + 0.1f * enerjiDb else 0.98f * taban + 0.02f * enerjiDb
            }
            onBellek.addLast(ozellik)
            while (onBellek.size > onCerceve) onBellek.removeFirst()
            if (enerjiDb > taban + esikDb) {
                yuksekSayac++
                if (yuksekSayac >= 3) {
                    konusma = ArrayList(onBellek)
                    onBellek.clear()
                    sessizSayac = 0
                    enerjiToplam = 0f
                    enerjiAdet = 0
                }
            } else {
                yuksekSayac = 0
            }
            return null
        }
        aktif.add(ozellik)
        if (enerjiDb > taban + esikDb) {
            enerjiToplam += enerjiDb
            enerjiAdet++
        }
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
            sonSnrDb = if (enerjiAdet > 0) enerjiToplam / enerjiAdet - taban else Float.NaN
            return if (parca.size >= minCerceve) ArrayList(parca) else null
        }
        return null
    }

    companion object {
        /** Bunun altı (tam sıfır örnekler) sayısal sessizliktir; gürültü tabanı tahminine katılmaz. */
        const val SAYISAL_SESSIZLIK_DB = -90f
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
 * Uyandırma öznitelikleri: c1..c12 + delta (hız) katsayıları, ifade başına ortalama ve varyans normalizasyonu
 * (CMVN). Ham ya da CMN uygulanmış MFCC dizisi için aynı sonucu verir; eski sürümün kayıtları dönüştürülebilir.
 * Gerçekçi sesle ölçümde (8 konuşmacı, araç içi gürültü) yalnız CMN'ye göre aynı algılama oranında yanlış
 * kabulü yarıdan fazla azalttı.
 */
object UyandirmaOzniteligi {
    fun donustur(d: List<FloatArray>): List<FloatArray> = if (d.isEmpty()) d else cmvn(delta(d))

    fun delta(d: List<FloatArray>): List<FloatArray> {
        val n = d[0].size
        return d.indices.map { t ->
            val a = d[max(0, t - 1)]
            val b = d[min(d.lastIndex, t + 1)]
            FloatArray(2 * n) { i -> if (i < n) d[t][i] else (b[i - n] - a[i - n]) * 0.5f }
        }
    }

    fun cmvn(d: List<FloatArray>): List<FloatArray> {
        val n = d[0].size
        val ort = FloatArray(n)
        val kare = FloatArray(n)
        for (v in d) for (i in 0 until n) ort[i] += v[i]
        for (i in 0 until n) ort[i] /= d.size
        for (v in d) for (i in 0 until n) {
            val x = v[i] - ort[i]
            kare[i] += x * x
        }
        val sapma = FloatArray(n) { sqrt(kare[it] / d.size + 1e-6f) }
        return d.map { v -> FloatArray(n) { i -> (v[i] - ort[i]) / sapma[i] } }
    }
}

/**
 * Uyandırma kelimesi dedektörü. PCM 16-bit örnekleri parça parça besleyin; kelime algılanınca true döner.
 *
 * [sablonlar]: kullanıcının öğretme kayıtlarının MFCC dizileri ([sablonCikar] çıktısı); [gruplar]: her şablonun
 * hangi söz olduğu (0 = "Hey Egea", 1 = "Hey Ege"). Bir konuşma parçası, bir sözün şablonlarına ortalama DTW
 * mesafesi o sözün eşiğinin altındaysa kabul edilir. Eşik = [carpan] × o sözün kendi kayıtları arasındaki mesafe
 * (en az tüm sözlerin ortalamasının yarısı): kayıtlar birbirine ne kadar benziyorsa eşik o kadar sıkıdır.
 * Eski sürüm eşiği tüm kayıtlar arası en büyük mesafeden ("Hey Egea" ile "Hey Ege" arası) alıyordu; bu, benzer
 * sözlerin çoğunu ("Ece", "Hey Emre"…) kabul ettiriyordu.
 */
class UyandirmaDedektoru(
    sablonlar: List<List<FloatArray>>,
    gruplar: IntArray = varsayilanGruplar(sablonlar.size),
    val carpan: Float = VARSAYILAN_CARPAN,
    esikDb: Float = 10f
) {
    private val kullanilan = saglamSablonlar(sablonlar.map { it.size })

    /** Diğerlerinden çok uzun olduğu için kullanılmayan (bozuk) kayıt sayısı. */
    val ayiklananSayisi: Int = sablonlar.size - kullanilan.size

    private val hazir: List<List<FloatArray>> = kullanilan.map { UyandirmaOzniteligi.donustur(sablonlar[it]) }
    private val grup = IntArray(hazir.size) { val i = kullanilan[it]; if (i < gruplar.size) gruplar[i] else 0 }
    private val grupSayisi = (grup.maxOrNull() ?: 0) + 1

    /** Her sözün kendi kayıtları arasındaki ortalama DTW mesafesi (tek kayıtlı sözde NaN). */
    val icMesafeler: FloatArray

    /** Her sözün kabul eşiği. */
    val esikler: FloatArray

    private val mfcc = Mfcc()
    private val bolucu = KonusmaBolucu(esikDb)
    private val halka = FloatArray(mfcc.pencere)
    private var yaz = 0
    private var dolu = 0
    private var yeni = 0
    private val cerceve = FloatArray(mfcc.pencere)

    /** Son değerlendirilen parçanın en iyi puanının eşiğe oranı (1 ve altı algılanır; tanı/öğretme ekranı için). */
    @Volatile
    var sonOran: Float = Float.NaN
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

    init {
        val ic = FloatArray(grupSayisi) { Float.NaN }
        for (g in 0 until grupSayisi) {
            var toplam = 0f
            var adet = 0
            for (i in hazir.indices) for (j in i + 1 until hazir.size) {
                if (grup[i] != g || grup[j] != g) continue
                val d = Dtw.mesafe(hazir[i], hazir[j])
                if (d != Float.MAX_VALUE) {
                    toplam += d
                    adet++
                }
            }
            if (adet > 0) ic[g] = toplam / adet
        }
        icMesafeler = ic
        val gecerli = ic.filter { !it.isNaN() }
        val ortalama = if (gecerli.isNotEmpty()) {
            gecerli.average().toFloat()
        } else {
            // Her sözden tek kayıt var: sözler arası mesafenin üçte biri (ölçümde iç mesafe ≈ çapraz mesafe / 3).
            var toplam = 0f
            var adet = 0
            for (i in hazir.indices) for (j in i + 1 until hazir.size) {
                val d = Dtw.mesafe(hazir[i], hazir[j])
                if (d != Float.MAX_VALUE) {
                    toplam += d
                    adet++
                }
            }
            if (adet > 0) toplam / adet / 3f else 0.6f
        }
        esikler = FloatArray(grupSayisi) { g -> carpan * max(if (ic[g].isNaN()) ortalama else ic[g], 0.5f * ortalama) }
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
                if (parca != null && eslesir(parca, bolucu.sonSnrDb)) bulundu = true
            }
        }
        return bulundu
    }

    /**
     * Bir konuşma parçası (ham MFCC dizisi) uyandırma sözü mü? [snrDb]: parçanın gürültüye oranı; gürültülü
     * ortamda (yolda) öznitelikler öğretme kaydından biraz uzaklaştığı için eşik bu orana göre gevşetilir.
     */
    fun eslesir(parca: List<FloatArray>, snrDb: Float = Float.NaN): Boolean {
        val gevsetme = gurultuGevsetmesi(snrDb)
        val p = UyandirmaOzniteligi.donustur(parca)
        var enIyi = Float.MAX_VALUE
        for (g in 0 until grupSayisi) {
            var uygun = false
            for (i in hazir.indices) {
                if (grup[i] != g) continue
                val oran = p.size.toFloat() / hazir[i].size
                if (oran >= MIN_UZUNLUK_ORANI && oran <= MAX_UZUNLUK_ORANI) {
                    uygun = true
                    break
                }
            }
            if (!uygun) continue
            var toplam = 0f
            var adet = 0
            for (i in hazir.indices) {
                if (grup[i] != g) continue
                val d = Dtw.mesafe(p, hazir[i])
                if (d != Float.MAX_VALUE) {
                    toplam += d
                    adet++
                }
            }
            if (adet > 0) enIyi = min(enIyi, toplam / adet / (esikler[g] * gevsetme))
        }
        sonOran = if (enIyi == Float.MAX_VALUE) Float.NaN else enIyi
        return enIyi <= 1f
    }

    companion object {
        /** Duyarlılık çarpanı: varsayılan ve ayar çubuğunun uçları (küçük = sıkı, büyük = kolay algılar). */
        const val VARSAYILAN_CARPAN = 1.6f
        const val EN_SIKI_CARPAN = 1.1f
        const val EN_GEVSEK_CARPAN = 2.0f

        /** Parça / şablon uzunluk oranı bu aralıkta değilse o söz denenmez. */
        const val MIN_UZUNLUK_ORANI = 0.65f
        const val MAX_UZUNLUK_ORANI = 1.8f

        /**
         * Gürültüde eşik gevşetme: parçanın SNR'si [TEMIZ_SNR_DB]'nin her 10 dB altı için eşik %15 gevşer (en çok
         * %35). Ölçümde (10 dB araç gürültüsü) algılamayı %42'den %75'e çıkardı; sessiz ortamı etkilemez.
         */
        const val GURULTU_EGIMI = 0.15f
        const val TEMIZ_SNR_DB = 20f

        /** Gürültülü parçada eşik çarpanı (1 = gevşetme yok). */
        fun gurultuGevsetmesi(snrDb: Float): Float =
            if (snrDb.isNaN()) 1f else min(1.35f, 1f + GURULTU_EGIMI * max(0f, TEMIZ_SNR_DB - snrDb) / 10f)

        /**
         * Uzunluğu medyanın 1.5 katını aşan şablonları ayıklar ve kalanların indislerini döndürür (en az 2 bırakır).
         * Önceki sürümde kaydın başındaki sessizlik yüzünden kaydın bir kısmı ya da tamamı şablon olabiliyordu; böyle
         * bir şablon eşiği şişirip benzer sözleri kabul ettiriyordu.
         */
        fun saglamSablonlar(boylar: List<Int>): List<Int> {
            if (boylar.size <= 2) return boylar.indices.toList()
            val s = boylar.sorted()
            val medyan = if (s.size % 2 == 1) s[s.size / 2].toFloat() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
            val kalan = boylar.indices.filter { boylar[it] <= medyan * 1.5f }
            return if (kalan.size >= 2) kalan else boylar.indices.toList()
        }

        /** Öğretme ekranındaki sıra: 2 × "Hey Egea" (0), 2 × "Hey Ege" (1). */
        fun varsayilanGruplar(n: Int): IntArray = IntArray(n) { if (it < 2) 0 else 1 }

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
    }
}
