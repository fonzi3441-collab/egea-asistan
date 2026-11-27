package com.egea.asistan.core

/**
 * Radyo istasyonu / TV kanalı adlarını söylenen sözle eşleştirir:
 * "kral fm" → "KRAL FM", "trt bir" → "TRT 1", "joy türk" → "JOY TÜRK TURKEY", "show" → "Show TV".
 */
object AdEslestirici {
    /** Karşılaştırmada atlanabilen genel kelimeler (katlanmış). */
    private val GENEL = setOf(
        "tv", "televizyon", "televizyonu", "kanal", "kanali", "radyo", "radio", "radyosu", "fm", "turkiye", "turkey",
        "hd", "fhd", "sd", "canli", "live", "official", "resmi"
    )
    private val SAYI_KELIMELERI = mapOf(
        "sifir" to "0", "bir" to "1", "iki" to "2", "uc" to "3", "dort" to "4", "bes" to "5",
        "alti" to "6", "yedi" to "7", "sekiz" to "8", "dokuz" to "9", "on" to "10"
    )

    private fun parcalar(s: String): List<String> =
        TurkceMetin.katla(s).replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.isNotEmpty() }
            .map { SAYI_KELIMELERI[it] ?: it }

    /** "trt1" ile "trt 1" aynı olsun diye harf/rakam sınırlarını ayırır. */
    private fun ayir(t: List<String>): List<String> = t.flatMap { w ->
        Regex("[a-z]+|[0-9]+").findAll(w).map { it.value }.toList()
    }

    /** 0..1 arası puan. */
    fun puan(sorgu: String, ad: String): Double {
        val q = ayir(parcalar(sorgu))
        val a = ayir(parcalar(ad))
        if (q.isEmpty() || a.isEmpty()) return 0.0
        if (q == a) return 1.0
        val qs = q.joinToString("")
        val asz = a.joinToString("")
        if (qs == asz) return 0.98
        val qo = q.filterNot { it in GENEL }
        val ao = a.filterNot { it in GENEL }
        if (qo.isNotEmpty() && qo == ao) return 0.95
        if (qo.isNotEmpty() && ao.isNotEmpty() && qo.joinToString("") == ao.joinToString("")) return 0.94
        if (qs.length >= 3 && asz.startsWith(qs)) return 0.9
        if (qo.isNotEmpty() && ao.containsAll(qo) && qo.joinToString("").length >= 2) return 0.88
        if (ao.isNotEmpty() && qo.containsAll(ao)) return 0.85
        val qk = qo.ifEmpty { q }.joinToString("")
        val ak = ao.ifEmpty { a }.joinToString("")
        var oran = Benzerlik.oran(qk, ak)
        if (qk.length >= 4 && ak.startsWith(qk.take(4))) oran += 0.05
        return oran.coerceAtMost(0.84)
    }

    /** En iyi eşleşmenin indeksi ve puanı; [esik] altındaysa null. Eşitlikte listede önce gelen (popüler) seçilir. */
    fun enIyi(sorgu: String, adlar: List<String>, esik: Double = 0.75): Pair<Int, Double>? {
        var enIyiIndeks = -1
        var enIyiPuan = 0.0
        for ((i, ad) in adlar.withIndex()) {
            val p = puan(sorgu, ad)
            if (p > enIyiPuan + 1e-9) {
                enIyiPuan = p
                enIyiIndeks = i
            }
        }
        return if (enIyiIndeks >= 0 && enIyiPuan >= esik) enIyiIndeks to enIyiPuan else null
    }
}
