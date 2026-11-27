package com.egea.asistan.core

/**
 * Radio Browser'daki istasyon adlarını sadeleştirir ve aynı istasyonun kopyalarını bulmak için anahtar üretir.
 * Veritabanında aynı yayın "JOY Türk", "JOY Türk Turkey", "Joy Türk (Türkiye)" gibi adlarla birden çok kez geçer.
 */
object RadyoAdi {
    /** Adın sonunda (ya da parantez içinde) ülke belirten, atılabilecek sözcükler (katlanmış). */
    private val ULKE = setOf("turkey", "turkiye", "tr")

    /** Tek başına kalırsa ad olmayan genel sözcükler: "Radyo Türkiye" → "Radyo" yapılmaz. */
    private val GENEL = setOf("radyo", "radio", "fm", "tv", "online", "canli")

    private val PARANTEZ = Regex("""\s*[(\[]([^)\]]*)[)\]]\s*""")
    private val AYRAC_SONDA = Regex("""[\s\-–—|/,:.]+$""")
    private val BOSLUK = Regex("""\s+""")

    /** "JOY Türk Turkey" → "JOY Türk", "Kral FM (Türkiye)" → "Kral FM", "Radyo Türkiye" olduğu gibi kalır. */
    fun sadelestir(ad: String): String {
        var s = ad.trim().replace(BOSLUK, " ")
        // Ülke adı parantez içindeyse at: "Kral FM (Türkiye)", "Power [TR]".
        s = PARANTEZ.replace(s) { m ->
            if (TurkceMetin.katla(m.groupValues[1].trim()) in ULKE) " " else m.value
        }.trim()
        // Sondaki ülke sözcüklerini at (ayraçlarla birlikte), ama geriye anlamlı bir ad kalsın.
        while (true) {
            val temiz = s.replace(AYRAC_SONDA, "")
            val kelimeler = temiz.split(' ').filter { it.isNotEmpty() }
            if (kelimeler.size < 2) {
                s = temiz
                break
            }
            val son = TurkceMetin.katla(kelimeler.last().trim('-', '–', '—', '|', '/', ',', '.', ':'))
            val kalan = kelimeler.dropLast(1)
            val kalanAnlamli = kalan.any { TurkceMetin.katla(it).trim('-', '|', '/', ',', '.', ':') !in GENEL }
            if (son in ULKE && kalanAnlamli) {
                s = kalan.joinToString(" ")
            } else {
                s = temiz
                break
            }
        }
        return s.replace(AYRAC_SONDA, "").trim().ifEmpty { ad.trim() }
    }

    /** Kopya bulma anahtarı: sadeleştirilmiş, katlanmış, harf/rakam dışı karakterleri atılmış ad. */
    fun anahtar(ad: String): String =
        TurkceMetin.katla(sadelestir(ad)).map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").replace(BOSLUK, " ").trim()

    /** Yayın adresi anahtarı: protokol, "www.", sondaki "/" ve büyük/küçük harf farkı yok sayılır. */
    fun adresAnahtari(url: String): String =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
}
