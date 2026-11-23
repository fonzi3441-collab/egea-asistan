package com.egea.asistan.core

/** Bulanık eşleştirme için basit metin benzerliği. */
object Benzerlik {
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var onceki = IntArray(b.length + 1) { it }
        var simdiki = IntArray(b.length + 1)
        for (i in 1..a.length) {
            simdiki[0] = i
            for (j in 1..b.length) {
                val maliyet = if (a[i - 1] == b[j - 1]) 0 else 1
                simdiki[j] = minOf(simdiki[j - 1] + 1, onceki[j] + 1, onceki[j - 1] + maliyet)
            }
            val gecici = onceki
            onceki = simdiki
            simdiki = gecici
        }
        return onceki[b.length]
    }

    /** 0..1 arası benzerlik oranı (1 = aynı). */
    fun oran(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val enUzun = maxOf(a.length, b.length)
        return 1.0 - levenshtein(a, b).toDouble() / enUzun
    }
}
