package com.egea.asistan.core

/** Sesli cevap cümleleri. */
object Cevaplar {
    const val ANLAMADIM_TEKRAR = "Anlayamadım, tekrar söyler misin?"
    const val ANLAMADIM = "Anlayamadım."
    const val KIMI_ARAYAYIM = "Kimi arayayım?"
    const val TAMAM = "Tamam."

    private val AYLAR = arrayOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )
    private val GUNLER = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    fun araniyor(ad: String) = "$ad aranıyor"

    fun numaraHazir(ad: String?) = if (ad.isNullOrBlank()) "Numara hazır, yeşil tuşa bas" else "$ad için numara hazır, yeşil tuşa bas"

    /** "Ahmet Yılmaz mı, Ahmet Kaya mı?" */
    fun secimSorusu(secenekler: List<String>): String =
        secenekler.joinToString(", ") { "$it ${TurkceMetin.soruEki(it)}" } + "?"

    fun rota(hedef: Hedef): String = when (hedef) {
        Hedef.Ev -> "Eve rota başlatıyorum"
        Hedef.Is -> "İşe rota başlatıyorum"
        is Hedef.Adres -> "${TurkceMetin.yonelmeEkiEkle(hedef.gorunen)} rota başlatıyorum"
    }

    fun haritadaAra(gorunen: String) = "Haritada $gorunen aranıyor"

    /** "Saat on dört otuz beş", "Saat dokuz sıfır beş", "Saat on yedi". */
    fun saat(saat: Int, dakika: Int): String {
        val s = "Saat " + Sayilar.yaziyla(saat.toLong())
        return when {
            dakika == 0 -> s
            dakika < 10 -> s + " sıfır " + Sayilar.yaziyla(dakika.toLong())
            else -> s + " " + Sayilar.yaziyla(dakika.toLong())
        }
    }

    /** [ay] 1..12, [haftaGunu] 1=Pazartesi..7=Pazar. */
    fun tarih(gun: Int, ay: Int, haftaGunu: Int): String =
        "Bugün ${Sayilar.yaziyla(gun.toLong())} ${AYLAR[(ay - 1).coerceIn(0, 11)]}, ${GUNLER[(haftaGunu - 1).coerceIn(0, 6)]}"

    /** Telefon numarasını seslendirmeye uygun gruplar: "05321234567" → "0532, 123, 45, 67". */
    fun numaraSeslendir(numara: String): String {
        val arti = numara.startsWith("+")
        val r = numara.filter { it.isDigit() }
        val gruplar: List<String> = when {
            r.length == 11 && r.startsWith("0") -> listOf(r.substring(0, 4), r.substring(4, 7), r.substring(7, 9), r.substring(9))
            r.length == 10 && !r.startsWith("0") -> listOf(r.substring(0, 3), r.substring(3, 6), r.substring(6, 8), r.substring(8))
            r.length == 12 && r.startsWith("90") -> listOf(r.substring(0, 2), r.substring(2, 5), r.substring(5, 8), r.substring(8, 10), r.substring(10))
            r.length <= 4 -> listOf(r)
            r.length <= 8 -> r.map { it.toString() }
            else -> r.chunked(3)
        }
        return (if (arti) "artı " else "") + gruplar.joinToString(", ")
    }

    fun sesSeviyesi(seviye: Int, yuzde: Boolean = false) = if (yuzde) "Ses yüzde $seviye" else "Ses $seviye"
}
