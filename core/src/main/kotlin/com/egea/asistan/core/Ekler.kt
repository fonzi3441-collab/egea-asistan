package com.egea.asistan.core

/**
 * Türkçe hal eklerini atma. Konuşma tanıma bazen kesme işareti koyar ("Park'a"), bazen koymaz
 * ("Parka"); ikisi de desteklenir. Belirsiz durumlarda birden fazla aday üretilir ve
 * eşleştirme (rehber vb.) en uygun olanı seçer.
 */
object Ekler {

    /** Atılan ekten sonra kalan kök ve sondan kaç karakter silindiği (görünen metne de uygulamak için). */
    data class Kesim(val kok: String, val silinen: Int, val kDonusumu: Boolean = false) {
        /** Aynı kesimi büyük/küçük harfi korunmuş ham kelimeye uygular. */
        fun hamaUygula(ham: String): String {
            if (silinen == 0 && !kDonusumu) return ham
            if (ham.length < silinen) return kok
            var s = ham.dropLast(silinen)
            if (kDonusumu && s.isNotEmpty()) {
                val son = s.last()
                s = s.dropLast(1) + (if (son.isUpperCase()) 'K' else 'k')
            }
            return s
        }
    }

    enum class Hal { BULUNMA, AYRILMA }

    private fun unlu(c: Char) = TurkceMetin.unluMu(c)
    private const val DAR_UNLULER = "ıiuü"

    /** "park'a" → "park" (kesmeden sonrasını atar). Kesme yoksa null. */
    fun kesmeyiAt(k: String): Kesim? {
        val i = k.indexOf('\'')
        return if (i > 0) Kesim(k.substring(0, i), k.length - i) else null
    }

    /**
     * Yönelme hali (-a/-e, -ya/-ye, iyelikten sonra -na/-ne) ekini atar.
     * [guvenli] true ise sadece kesmeli ve kalıbı belirgin ekler atılır; çıplak "-a/-e" atılmaz
     * ("rota Bursa" → "Bursa" kalır, "Burs" olmaz).
     */
    fun yonelme(k: String, guvenli: Boolean = false): Kesim {
        kesmeyiAt(k)?.let { return it }
        val n = k.length
        // Marmara Parkına → Marmara Parkı, Bağdat Caddesine → Caddesi, Beyoğluna → Beyoğlu
        if (n >= 5 && (k.endsWith("na") || k.endsWith("ne")) && DAR_UNLULER.indexOf(k[n - 3]) >= 0) {
            return Kesim(k.dropLast(2), 2)
        }
        // Kadıköye → Kadıköy (yoksa "Kadıkö+ye" diye yanlış ayrılırdı)
        if (k.endsWith("köye") || k.endsWith("koye")) return Kesim(k.dropLast(1), 1)
        // Ankaraya → Ankara, Hastaneye → Hastane
        if (n >= 4 && (k.endsWith("ya") || k.endsWith("ye")) && unlu(k[n - 3])) return Kesim(k.dropLast(2), 2)
        if (guvenli) return Kesim(k, 0)
        // Parka → Park, Kadıköye, İzmire → İzmir, benzinliğe → benzinlik
        if (n >= 3 && (k.last() == 'a' || k.last() == 'e') && !unlu(k[n - 2])) {
            val kok = k.dropLast(1)
            return if (kok.endsWith("ğ")) Kesim(kok.dropLast(1) + "k", 1, kDonusumu = true) else Kesim(kok, 1)
        }
        return Kesim(k, 0)
    }

    /**
     * Belirtme hali (-ı/-i/-u/-ü, -yı/-yi..., iyelikten sonra -nı/-ni) için aday kökler.
     * İlk aday her zaman kelimenin kendisidir ("Ali" gibi i ile biten isimler bozulmasın).
     * Kesme varsa tek aday: kesmeden önceki kısım.
     */
    fun belirtmeAdaylari(k: String): List<Kesim> {
        kesmeyiAt(k)?.let { return listOf(it) }
        val l = mutableListOf(Kesim(k, 0))
        val n = k.length
        if (n >= 4 && DAR_UNLULER.indexOf(k.last()) >= 0) {
            val onceki = k[n - 2]
            val ikiOnce = k[n - 3]
            // Ayşeyi → Ayşe, Aliyi → Ali
            if (onceki == 'y' && unlu(ikiOnce)) l += Kesim(k.dropLast(2), 2)
            // işyerini → işyeri (Hüseyini → Hüseyin de aşağıda ayrıca denenir)
            if (onceki == 'n' && DAR_UNLULER.indexOf(ikiOnce) >= 0) l += Kesim(k.dropLast(2), 2)
            // Ahmeti → Ahmet, annemi → annem, benzinliği → benzinlik
            if (!unlu(onceki) && !(onceki == 'y' && unlu(ikiOnce))) {
                val kok = k.dropLast(1)
                l += if (kok.endsWith("ğ")) Kesim(kok.dropLast(1) + "k", 1, kDonusumu = true) else Kesim(kok, 1)
            }
        }
        return l.distinctBy { it.kok }
    }

    /** İlgi hali (-ın/-in/-nın/-nin) için aday kökler: "Ahmetin" → "Ahmet", "Ayşenin" → "Ayşe". */
    fun ilgiAdaylari(k: String): List<Kesim> {
        kesmeyiAt(k)?.let { return listOf(it) }
        val l = mutableListOf(Kesim(k, 0))
        val n = k.length
        if (n >= 5 && k.last() == 'n' && DAR_UNLULER.indexOf(k[n - 2]) >= 0) {
            if (k[n - 3] == 'n' && unlu(k[n - 4])) l += Kesim(k.dropLast(3), 3)
            if (!unlu(k[n - 3])) l += Kesim(k.dropLast(2), 2)
        }
        return l.distinctBy { it.kok }
    }

    /** Yönelme hali için aday kökler (rehber araması için): "Ahmete" → ["ahmete", "ahmet"]. */
    fun yonelmeAdaylari(k: String): List<Kesim> {
        kesmeyiAt(k)?.let { return listOf(it) }
        val kesim = yonelme(k)
        return if (kesim.silinen == 0) listOf(Kesim(k, 0)) else listOf(Kesim(k, 0), kesim)
    }

    /**
     * Akrabalık/iyelik tabanı: "annem" → "anne", "babam" → "baba", "kardeşim" → "kardeş".
     * Rehberde "Anne", "Baba" diye kayıtlı kişileri de bulmak için ek aday.
     */
    fun iyelikTabanlari(k: String): List<String> {
        val n = k.length
        if (n < 4 || k.last() != 'm' || k.contains('\'')) return emptyList()
        val l = mutableListOf<String>()
        if (unlu(k[n - 2])) l += k.dropLast(1)
        if (DAR_UNLULER.indexOf(k[n - 2]) >= 0 && !unlu(k[n - 3])) l += k.dropLast(2)
        return l.filter { it.length >= 2 }
    }

    /** Bulunma (-da/-de/-ta/-te) veya ayrılma (-dan/-den/-tan/-ten) eki varsa kök ve hal. */
    fun bulunmaAyrilma(k: String): Pair<String, Hal>? {
        val i = k.indexOf('\'')
        if (i > 0) {
            val kok = k.substring(0, i)
            return when (k.substring(i + 1)) {
                "da", "de", "ta", "te" -> kok to Hal.BULUNMA
                "dan", "den", "tan", "ten" -> kok to Hal.AYRILMA
                else -> null
            }
        }
        for (ek in arrayOf("dan", "den", "tan", "ten")) {
            if (k.length > ek.length + 2 && k.endsWith(ek)) return k.dropLast(ek.length) to Hal.AYRILMA
        }
        for (ek in arrayOf("da", "de", "ta", "te")) {
            if (k.length > ek.length + 2 && k.endsWith(ek)) return k.dropLast(ek.length) to Hal.BULUNMA
        }
        return null
    }
}
