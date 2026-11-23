package com.egea.asistan.core

import java.text.Normalizer
import java.util.Locale

/**
 * Türkçe metin yardımcıları: doğru küçük harf (İ/ı), tek tip kesme işareti,
 * noktalama temizliği, ASCII katlama, ünlü uyumu ve ek üretimi.
 */
object TurkceMetin {
    val TR: Locale = Locale.forLanguageTag("tr-TR")

    private const val KESMELER = "'’‘‛`´ʼʹ′＇"
    private const val UNLULER = "aeıioöuüâîû"
    private const val KALIN_UNLULER = "aıouâû"

    /** Bir kelime: [ham] konuşma tanımanın yazdığı hali (büyük/küçük harf korunur), [n] normalize hali. */
    data class Kelime(val ham: String, val n: String)

    /** Türkçe kurallarıyla küçük harf: I→ı, İ→i; birleşik noktayı (U+0307) atar. */
    fun kucukHarf(s: String): String =
        s.replace('İ', 'i').replace('I', 'ı').lowercase(TR).replace("̇", "")

    /** Kelimenin baş harfini Türkçe kurallarıyla büyütür (i→İ). */
    fun basHarfBuyuk(s: String): String =
        if (s.isEmpty()) s else s.substring(0, 1).uppercase(TR) + s.substring(1)

    /** Metni kelimelere böler. Kesme işaretleri tek tip ('), noktalama boşluk olur, % → "yüzde". */
    fun kelimeler(metin: String): List<Kelime> {
        val nfc = Normalizer.normalize(metin, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length + 8)
        for (ch in nfc) {
            when {
                KESMELER.indexOf(ch) >= 0 -> sb.append('\'')
                ch == '%' -> sb.append(" yüzde ")
                ch == '̇' -> Unit
                ch.isLetterOrDigit() || ch == '+' -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        val sonuc = ArrayList<Kelime>()
        for (parca in sb.split(' ')) {
            val temiz = parca.trim('\'')
            if (temiz.isEmpty()) continue
            sonuc += Kelime(ham = temiz, n = kucukHarf(temiz))
        }
        return sonuc
    }

    /** Komut çözümleme için normalize metin: küçük harf, tek tip kesme, noktalama yok, tek boşluk. */
    fun normalize(metin: String): String = kelimeler(metin).joinToString(" ") { it.n }

    /** Karşılaştırma için ASCII'ye katlar: ç→c, ğ→g, ı→i, ö→o, ş→s, ü→u; kesmeyi atar. */
    fun katla(s: String): String {
        val k = kucukHarf(s)
        val sb = StringBuilder(k.length)
        for (c in k) {
            when (c) {
                'ç' -> sb.append('c')
                'ğ' -> sb.append('g')
                'ı' -> sb.append('i')
                'ö' -> sb.append('o')
                'ş' -> sb.append('s')
                'ü' -> sb.append('u')
                'â' -> sb.append('a')
                'î' -> sb.append('i')
                'û' -> sb.append('u')
                '\'' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun unluMu(c: Char): Boolean = UNLULER.indexOf(c) >= 0

    private val HARF_ADLARI = mapOf(
        'a' to "a", 'b' to "be", 'c' to "ce", 'ç' to "çe", 'd' to "de", 'e' to "e", 'f' to "fe",
        'g' to "ge", 'ğ' to "ge", 'h' to "he", 'ı' to "ı", 'i' to "i", 'j' to "je", 'k' to "ke",
        'l' to "le", 'm' to "me", 'n' to "ne", 'o' to "o", 'ö' to "ö", 'p' to "pe", 'q' to "kü",
        'r' to "re", 's' to "se", 'ş' to "şe", 't' to "te", 'u' to "u", 'ü' to "ü", 'v' to "ve",
        'w' to "ve", 'x' to "iks", 'y' to "ye", 'z' to "ze"
    )

    /**
     * Kelimenin okunuşuna göre son ünlüsü ve ünlüyle bitip bitmediği.
     * Sayılar okunuşlarına çevrilir (5 → beş), kısaltmalar harf harf okunur (AVM → ave-me).
     */
    private fun okunus(kelime: String): Pair<Char?, Boolean> {
        val ham = kelime.trim().trimEnd('\'', '.')
        if (ham.isEmpty()) return null to false
        val okunan: String = when {
            ham.last().isDigit() -> {
                val rakamlar = ham.takeLastWhile { it.isDigit() }.take(12)
                Sayilar.yaziyla(rakamlar.toLongOrNull() ?: 0L).split(' ').last()
            }
            ham.length in 2..5 && ham.all { it.isLetter() && it.isUpperCase() } ->
                HARF_ADLARI[kucukHarf(ham.last().toString()).first()] ?: kucukHarf(ham)
            else -> kucukHarf(ham)
        }
        val sonUnlu = okunan.lastOrNull { unluMu(it) }?.let {
            when (it) {
                'â' -> 'a'
                'î' -> 'i'
                'û' -> 'u'
                else -> it
            }
        }
        return sonUnlu to unluMu(okunan.last())
    }

    /** Soru eki: "Yılmaz mı", "Demir mi", "Uğur mu", "Gül mü". */
    fun soruEki(kelime: String): String = when (okunus(kelime.split(' ').last()).first) {
        'a', 'ı' -> "mı"
        'o', 'u' -> "mu"
        'ö', 'ü' -> "mü"
        else -> "mi"
    }

    /**
     * Yönelme hali eki ekler (cevap cümleleri için): "Marmara Park" → "Marmara Park'a",
     * "Kadıköy" → "Kadıköy'e", "Ankara" → "Ankara'ya".
     */
    fun yonelmeEkiEkle(ad: String): String {
        val temiz = ad.trim()
        if (temiz.isEmpty()) return temiz
        val (unlu, unluyleBiter) = okunus(temiz.split(' ').last())
        val ek = if (unlu != null && KALIN_UNLULER.indexOf(unlu) >= 0) "a" else "e"
        return temiz + "'" + (if (unluyleBiter) "y" else "") + ek
    }
}
