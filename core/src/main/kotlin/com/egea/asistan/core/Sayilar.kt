package com.egea.asistan.core

/**
 * Yazıyla ya da rakamla söylenen sayıları çözer ("on beş" → 15, "sıfır beş yüz otuz iki" → 0 532)
 * ve sayıları Türkçe okunuşa çevirir.
 */
object Sayilar {
    private val BIRLER = mapOf(
        "sıfır" to 0, "bir" to 1, "iki" to 2, "üç" to 3, "dört" to 4,
        "beş" to 5, "altı" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9
    )
    private val ONLAR = mapOf(
        "on" to 10, "yirmi" to 20, "otuz" to 30, "kırk" to 40, "elli" to 50,
        "altmış" to 60, "yetmiş" to 70, "seksen" to 80, "doksan" to 90
    )
    private val BIRLER_AD = arrayOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val ONLAR_AD = arrayOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")

    /** Sayı kelimelerine gelebilecek ekler (uzundan kısaya). */
    private val EKLER = listOf(
        "nın", "nin", "nun", "nün", "dan", "den", "tan", "ten",
        "ya", "ye", "yı", "yi", "yu", "yü", "da", "de", "ta", "te", "ın", "in", "un", "ün",
        "a", "e", "ı", "i", "u", "ü"
    )

    sealed class Parca {
        data class Rakam(val s: String) : Parca()
        data class Birler(val d: Int) : Parca()
        data class Onlar(val d: Int) : Parca()
        data object Yuz : Parca()
        data object Bin : Parca()
    }

    /** Sayı grubu: [metin] baştaki sıfırları korur ("0532"), [deger] sayısal değer. */
    data class Grup(val metin: String, val deger: Long)

    private fun kok(s: String): Parca? = BIRLER[s]?.let { Parca.Birler(it) }
        ?: ONLAR[s]?.let { Parca.Onlar(it) }
        ?: when (s) {
            "yüz" -> Parca.Yuz
            "bin" -> Parca.Bin
            else -> null
        }

    /** Tek bir kelimeyi sayı parçasına çevirir: "15'e" → Rakam(15), "beşe" → Birler(5), "dörde" → Birler(4). */
    fun parca(kelime: String): Parca? {
        val t = TurkceMetin.kucukHarf(kelime)
        if (t == "yüzde") return null
        val govde = t.substringBefore('\'')
        val ekKesme = if (t.contains('\'')) t.substringAfter('\'') else ""
        val rakamlar = govde.takeWhile { it.isDigit() }
        if (rakamlar.isNotEmpty()) {
            val kalan = govde.substring(rakamlar.length)
            return if (kalan.isEmpty() || kalan in EKLER) Parca.Rakam(rakamlar) else null
        }
        if (ekKesme.isNotEmpty() && ekKesme !in EKLER) return null
        kok(govde)?.let { return it }
        for (ek in EKLER) {
            if (govde.length > ek.length + 1 && govde.endsWith(ek)) {
                val s = govde.dropLast(ek.length)
                kok(s)?.let { return it }
                if (s.endsWith("d")) kok(s.dropLast(1) + "t")?.let { return it } // dörde → dört
            }
        }
        return null
    }

    fun sayiMi(kelime: String): Boolean = parca(kelime) != null

    private class Toplayici {
        var bin: Long? = null
        var yuz: Int? = null
        var on: Int? = null
        var bir: Int? = null
        fun bos() = bin == null && yuz == null && on == null && bir == null
        fun deger(): Long = (bin ?: 0L) * 1000L + (yuz ?: 0) * 100L + (on ?: 0) + (bir ?: 0)
    }

    /**
     * Kelime listesindeki sayıları gruplar. Sayı olmayan kelime grubu böler.
     * "sıfır beş yüz otuz iki yüz yirmi üç kırk beş altmış yedi" → [0, 532, 123, 45, 67]
     */
    fun gruplar(kelimeler: List<String>): List<Grup> {
        val sonuc = mutableListOf<Grup>()
        var t = Toplayici()
        fun bosalt() {
            if (!t.bos()) {
                val d = t.deger()
                sonuc += Grup(d.toString(), d)
            }
            t = Toplayici()
        }
        for (k in kelimeler) {
            when (val p = parca(k)) {
                null -> bosalt()
                is Parca.Rakam -> {
                    bosalt()
                    sonuc += Grup(p.s, p.s.take(18).toLongOrNull() ?: 0L)
                }
                is Parca.Birler -> {
                    if (t.bir != null) bosalt()
                    t.bir = p.d
                }
                is Parca.Onlar -> {
                    if (t.on != null || t.bir != null) bosalt()
                    t.on = p.d
                }
                Parca.Yuz -> {
                    // "sıfır yüz" = 0 ve 100 (sıfırla çarpılmaz)
                    if (t.yuz != null || t.on != null || t.bir == 0) bosalt()
                    t.yuz = t.bir ?: 1
                    t.bir = null
                }
                Parca.Bin -> {
                    if (t.bin != null) bosalt()
                    val d = if (t.bos()) 1L else t.deger()
                    t = Toplayici()
                    t.bin = d
                }
            }
        }
        bosalt()
        return sonuc
    }

    /** İlk sayının değeri ("sesi on beş yap" → 15). */
    fun ilkSayi(kelimeler: List<String>): Long? = gruplar(kelimeler).firstOrNull()?.deger

    /**
     * Kelimelerin tamamı sayıysa telefon numarası olarak birleştirir ("0532 123 45 67'yi" → "05321234567").
     * "artı" → "+". En az 3 rakam yoksa null.
     */
    fun telefonNumarasi(kelimeler: List<String>): String? {
        if (kelimeler.isEmpty()) return null
        var arti = false
        val sayiKelimeleri = mutableListOf<String>()
        for ((i, k) in kelimeler.withIndex()) {
            val n = TurkceMetin.kucukHarf(k)
            when {
                i == 0 && (n == "artı" || n == "+") -> arti = true
                n.startsWith("+") && n.length > 1 && n[1].isDigit() -> {
                    if (i == 0) arti = true
                    sayiKelimeleri += n.drop(1)
                }
                parca(n) != null -> sayiKelimeleri += n
                else -> return null
            }
        }
        val rakamlar = gruplar(sayiKelimeleri).joinToString("") { it.metin }
        if (rakamlar.length < 3) return null
        return (if (arti) "+" else "") + rakamlar
    }

    private fun yuzlu(n: Int): String {
        val y = n / 100
        val o = (n % 100) / 10
        val b = n % 10
        val l = mutableListOf<String>()
        if (y > 0) l += if (y == 1) "yüz" else BIRLER_AD[y] + " yüz"
        if (o > 0) l += ONLAR_AD[o]
        if (b > 0) l += BIRLER_AD[b]
        return l.joinToString(" ")
    }

    /** Sayıyı Türkçe okunuşa çevirir: 14 → "on dört", 1250 → "bin iki yüz elli". */
    fun yaziyla(sayi: Long): String {
        if (sayi == 0L) return "sıfır"
        if (sayi < 0L) return "eksi " + yaziyla(-sayi)
        var kalan = sayi
        val milyar = kalan / 1_000_000_000L
        kalan %= 1_000_000_000L
        val milyon = kalan / 1_000_000L
        kalan %= 1_000_000L
        val bin = kalan / 1000L
        kalan %= 1000L
        val l = mutableListOf<String>()
        if (milyar > 0) l += (if (milyar >= 1000) yaziyla(milyar) else yuzlu(milyar.toInt())) + " milyar"
        if (milyon > 0) l += yuzlu(milyon.toInt()) + " milyon"
        if (bin > 0) l += if (bin == 1L) "bin" else yuzlu(bin.toInt()) + " bin"
        if (kalan > 0) l += yuzlu(kalan.toInt())
        return l.joinToString(" ")
    }
}
