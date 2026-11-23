package com.egea.asistan.core

enum class NiyetTuru { ARA, NAVIGASYON, MEDYA, SARKI_CAL, UYGULAMA_AC, SAAT, IPTAL, BILINMIYOR }

enum class MedyaEylemi { SONRAKI, ONCEKI, DURDUR, DEVAM, SES_AC, SES_KIS, SES_AYARLA, SES_KAPAT, SES_MAKS }

enum class Miktar { AZ, NORMAL, COK }

/** Şarkı/arama için açıkça söylenen uygulama ("YouTube'da", "Spotify'dan"). */
enum class HedefUygulama { SPOTIFY, YOUTUBE_MUSIC, YOUTUBE, GOOGLE }

/** Rehberde aranacak kişi: [adaylar] ekleri atılmış olası yazılışlar (en olası önce değil, orijinal önce). */
data class KisiSorgusu(val gorunen: String, val adaylar: List<String>, val kesin: Boolean)

sealed class Hedef {
    data object Ev : Hedef()
    data object Is : Hedef()
    data class Adres(val sorgu: String, val gorunen: String) : Hedef()
}

/** Metinden çıkarılan niyet. */
sealed class Niyet(val tur: NiyetTuru) {
    /** Telefonla arama. [numara] ve [kisi] ikisi de null ise "Kimi arayayım?" diye sorulur. */
    data class Ara(val numara: String? = null, val kisi: KisiSorgusu? = null) : Niyet(NiyetTuru.ARA)

    /** [rotaBaslat] false ise sadece haritada arama yapılır ("haritada X'i göster"). */
    data class Navigasyon(val hedef: Hedef, val rotaBaslat: Boolean = true) : Niyet(NiyetTuru.NAVIGASYON)

    data class Medya(
        val eylem: MedyaEylemi,
        val deger: Int? = null,
        val yuzde: Boolean = false,
        val miktar: Miktar = Miktar.NORMAL
    ) : Niyet(NiyetTuru.MEDYA)

    /** [sadeceAra] true ise sonuçlar gösterilir ("YouTube'da Tarkan ara"), false ise çalınır. */
    data class SarkiCal(
        val sorgu: String,
        val gorunen: String,
        val uygulama: HedefUygulama? = null,
        val sadeceAra: Boolean = false
    ) : Niyet(NiyetTuru.SARKI_CAL)

    /** [sarkiOlabilir] true ise uygulama bulunamazsa şarkı olarak aranır. */
    data class UygulamaAc(val ad: String, val gorunen: String, val sarkiOlabilir: Boolean = false) :
        Niyet(NiyetTuru.UYGULAMA_AC)

    data class Saat(val tarih: Boolean = false) : Niyet(NiyetTuru.SAAT)

    data object Iptal : Niyet(NiyetTuru.IPTAL)

    data class Bilinmiyor(val metin: String) : Niyet(NiyetTuru.BILINMIYOR)

    /** Log ve tanı ekranı için kısa özet. */
    fun ozet(): String = when (this) {
        is Ara -> when {
            numara != null -> "ARA(numara=$numara)"
            kisi != null -> "ARA(kişi=${kisi.gorunen})"
            else -> "ARA(?)"
        }
        is Navigasyon -> {
            val h = when (hedef) {
                Hedef.Ev -> "EV"
                Hedef.Is -> "İŞ"
                is Hedef.Adres -> hedef.gorunen
            }
            if (rotaBaslat) "NAVIGASYON($h)" else "NAVIGASYON(ara: $h)"
        }
        is Medya -> "MEDYA($eylem" + (deger?.let { if (yuzde) " %$it" else " $it" } ?: "") +
            (if (miktar != Miktar.NORMAL) " $miktar" else "") + ")"
        is SarkiCal -> "SARKI_CAL($gorunen" + (uygulama?.let { " @$it" } ?: "") + (if (sadeceAra) " ara" else "") + ")"
        is UygulamaAc -> "UYGULAMA_AC($gorunen)"
        is Saat -> if (tarih) "SAAT(tarih)" else "SAAT"
        Iptal -> "IPTAL"
        is Bilinmiyor -> "BILINMIYOR"
    }
}
