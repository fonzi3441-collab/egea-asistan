package com.egea.asistan.core

/**
 * Söylenen ismi rehberdeki isimlerle eşleştirir. Birden fazla eşit derecede uygun kişi varsa
 * hepsini döndürür (çağıran "Ahmet Yılmaz mı, Ahmet Kaya mı?" diye sorar).
 */
object KisiEslestirici {
    const val ESIK = 35

    /** Bir aday yazılış ile bir rehber ismi arasındaki puan (0..100). İkisi de katlanmış olmalı. */
    fun puan(aday: String, isim: String): Int {
        if (aday.isEmpty() || isim.isEmpty()) return 0
        if (aday == isim) return 100
        val at = aday.split(' ')
        val it2 = isim.split(' ')
        if (at.size > 1 && it2.containsAll(at)) return 90
        if (at.size == 1 && aday in it2) return 80
        if (aday.length >= 3 && isim.startsWith(aday)) return 70
        if (aday.length >= 3 && it2.any { it.startsWith(aday) }) return 65
        if (isim.length >= 3 && aday.startsWith(isim) && aday.length - isim.length <= 3) return 60
        var oran = Benzerlik.oran(aday, isim)
        if (at.size == 1) {
            for (t in it2) if (t.length >= 3) oran = maxOf(oran, Benzerlik.oran(aday, t) - 0.02)
        }
        return if (oran >= 0.75) (50 * oran).toInt() else 0
    }

    /**
     * [adaylar]: aynı sözün olası yazılışları (öncelik sırasıyla), [isimler]: rehber isimleri.
     * En yüksek puanlı isimlerin indeksleri; eşleşme yoksa boş liste.
     */
    fun eslestir(adaylar: List<String>, isimler: List<String>): List<Int> {
        val katliAdaylar = adaylar.map { TurkceMetin.katla(it) }.filter { it.isNotEmpty() }.distinct()
        if (katliAdaylar.isEmpty() || isimler.isEmpty()) return emptyList()
        val puanlar = isimler.map { isim ->
            val ki = TurkceMetin.katla(isim)
            var enIyi = 0
            for ((i, a) in katliAdaylar.withIndex()) {
                val p = puan(a, ki)
                if (p >= ESIK) enIyi = maxOf(enIyi, p - 3 * i)
            }
            enIyi
        }
        val enIyi = puanlar.maxOrNull() ?: 0
        if (enIyi <= 0) return emptyList()
        return puanlar.indices.filter { puanlar[it] == enIyi }
    }
}

/** Seçim sorusuna verilen cevap. */
sealed class Secim {
    data class Indeks(val i: Int) : Secim()
    data object Iptal : Secim()
    data object Anlasilmadi : Secim()
}

/** "Ahmet Yılmaz mı, Ahmet Kaya mı?" sorusunun cevabını çözer: "Yılmaz", "ikincisi", "Kaya olan", "iptal". */
object SecimCozucu {
    private val SIRALAR = listOf(
        setOf("birinci", "birincisi", "birinciyi", "ilk", "ilki", "ilkini", "ilkine", "bir", "biri", "1"),
        setOf("ikinci", "ikincisi", "ikinciyi", "ikinciye", "iki", "2"),
        setOf("üçüncü", "üçüncüsü", "üçüncüyü", "üç", "3"),
        setOf("dördüncü", "dördüncüsü", "dördüncüyü", "dört", "4"),
        setOf("beşinci", "beşincisi", "beşinciyi", "beş", "5")
    )
    private val SON = setOf("son", "sonuncu", "sonuncusu", "sonuncuyu", "sondaki", "sonuncuya")
    private val DOLGU = setOf(
        "olan", "olanı", "olanını", "olanına", "ara", "arayın", "arar", "onu", "o", "şu", "bu", "evet", "lütfen",
        "tabii", "tabi", "tamam", "mı", "mi", "mu", "mü", "diyorum", "dedim", "kişi", "kişiyi", "numara",
        "numarayı", "olsun", "hangisi", "yani", "ee", "şey"
    )

    fun coz(cevap: String, secenekler: List<String>): Secim {
        val t = TurkceMetin.kelimeler(cevap).map { it.n }
        if (t.isEmpty()) return Secim.Anlasilmadi
        if (KomutCozucu.iptalMi(t)) return Secim.Iptal
        if (t.size <= 3) {
            for ((i, s) in SIRALAR.withIndex()) {
                if (i < secenekler.size && t.any { it in s }) return Secim.Indeks(i)
            }
            if (t.any { it in SON }) return Secim.Indeks(secenekler.lastIndex)
        }
        val temiz = t.filterNot { it in DOLGU }
        if (temiz.isEmpty()) return Secim.Anlasilmadi
        val bas = temiz.dropLast(1)
        val adaylar = Ekler.belirtmeAdaylari(temiz.last()).map { (bas + it.kok).joinToString(" ") }
        val esler = KisiEslestirici.eslestir(adaylar, secenekler)
        return if (esler.size == 1) Secim.Indeks(esler[0]) else Secim.Anlasilmadi
    }
}
