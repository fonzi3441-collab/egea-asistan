package com.egea.asistan.core

import com.egea.asistan.core.TurkceMetin.Kelime

/**
 * Türkçe konuşma metnini niyete çevirir. Android'den bağımsızdır.
 *
 * Sıra önemlidir: iptal → saat → ses → medya → uygulamada arama ("YouTube'da X ara") →
 * telefon araması → navigasyon → uygulama açma / şarkı çalma.
 */
object KomutCozucu {

    // ---------------------------------------------------------------- dolgu kelimeleri
    private val BAS_DOLGU = setOf(
        "hey", "egea", "asistan", "tamam", "evet", "şey", "hmm", "ee", "eee", "lütfen", "hadi", "haydi",
        "acaba", "şimdi", "hemen", "merhaba", "selam", "yani", "ok", "okey", "peki", "bana"
    )
    private val SON_DOLGU = setOf(
        "lütfen", "hemen", "bakalım", "şimdi", "artık", "acaba", "mısın", "misin", "musun", "müsün",
        "mısınız", "misiniz", "musunuz", "müsünüz", "mı", "mi", "mu", "mü", "miyiz", "mıyız", "muyuz",
        "müyüz", "miyim", "mıyım", "muyum", "müyüm", "istiyorum", "istiyoruz", "isterim", "isterdim",
        "ister", "işte", "be", "tamam", "derhal", "çabuk", "hadi", "rica", "ederim", "etsem", "ya"
    )

    // ---------------------------------------------------------------- iptal
    private val IPTAL_IFADELERI = setOf(
        "iptal", "iptal et", "iptal iptal", "vazgeç", "vazgeçtim", "boş ver", "boşver", "kapat", "kapan",
        "çık", "çıkış", "tamam", "tamamdır", "yok", "hiç", "hiçbir şey", "hiç bir şey", "sus", "dur",
        "istemiyorum", "gerek yok", "önemli değil", "neyse", "kalsın", "unut", "unut gitsin", "hayır",
        "teşekkürler", "teşekkür ederim", "sağ ol", "sağol", "sağolun", "sağ olun", "asistanı kapat",
        "dinlemeyi bırak", "bırak", "kapat lütfen", "iptal lütfen", "yok yok", "tamam tamam",
        "hayır hayır", "boş ver gitsin", "vazgeç lütfen", "kapatabilirsin", "çıkabilirsin"
    )

    /** Söylenen şey bir iptal/kapatma isteği mi? */
    fun iptalMi(t: List<String>): Boolean {
        if (t.isEmpty()) return false
        val s = t.joinToString(" ")
        if (s in IPTAL_IFADELERI) return true
        if (t[0] == "iptal" || t[0] == "vazgeç" || t[0] == "vazgeçtim") return true
        if (t.last() == "iptal") return true
        if (t.size >= 2 && t[t.size - 2] == "iptal" && t.last() == "et") return true
        return false
    }

    // ---------------------------------------------------------------- saat
    private val SAAT_SORU = setOf("kaç", "kaçtır", "söyle", "söyler", "söylesene", "söyleyebilir", "nedir", "ne", "oldu", "kaçı")
    private val TARIH_IFADELERI = listOf(
        "günlerden ne", "bugün ne günü", "ayın kaçı", "tarih ne", "tarih kaç", "bugünün tarihi",
        "hangi gündeyiz", "bugün hangi gün", "tarihi söyle", "bugün günlerden", "tarih nedir", "bugün ayın"
    )

    private fun saatMi(t: List<String>): Niyet? {
        val s = t.joinToString(" ")
        if (TARIH_IFADELERI.any { s.contains(it) } || s == "tarih") return Niyet.Saat(tarih = true)
        val saatVar = t.any { it == "saat" || it == "saati" || it == "saatı" }
        if (saatVar && t.size <= 6 && (t.size == 1 || t.any { it in SAAT_SORU })) return Niyet.Saat()
        return null
    }

    // ---------------------------------------------------------------- ses seviyesi
    private val SES_KELIMELERI = setOf("ses", "sesi", "sesini", "sesin", "sese", "sesı", "volüm", "volume", "sesiyle")
    private fun sesKelimesiMi(k: String) = k in SES_KELIMELERI || k.startsWith("ses'")

    private val SES_ARTIR = setOf(
        "aç", "açar", "açsana", "açın", "açabilir", "açabilirsin", "yükselt", "yükseltir", "yükseltsene",
        "artır", "arttır", "artırır", "arttırır", "artırsana", "arttırsana", "çoğalt", "kökle", "yukarı",
        "art", "fazlalaştır", "çıkar", "çıkart"
    )
    private val SES_AZALT = setOf(
        "kıs", "kısar", "kıssana", "kısın", "kısabilir", "kısabilirsin", "azalt", "azaltır", "azaltsana",
        "düşür", "düşürür", "düşürsene", "alçalt", "alçaltır", "indir", "indirir", "indirsene", "aşağı"
    )
    private val MIKTAR_KELIMELERI = setOf(
        "biraz", "azıcık", "bir", "bi", "tık", "kademe", "daha", "çok", "epey", "baya", "bayağı", "iyice", "lütfen"
    )

    private fun miktar(t: List<String>): Miktar = when {
        t.any { it == "çok" || it == "epey" || it == "baya" || it == "bayağı" || it == "iyice" } -> Miktar.COK
        t.any { it == "biraz" || it == "azıcık" || it == "tık" || it == "kademe" } -> Miktar.AZ
        else -> Miktar.NORMAL
    }

    private fun sesKomutu(t: List<String>): Niyet? {
        if (t.contains("sessize")) return Niyet.Medya(MedyaEylemi.SES_KAPAT)
        val sesIndex = t.indexOfFirst { sesKelimesiMi(it) }
        if (sesIndex < 0) {
            val kalan = t.filterNot { it in MIKTAR_KELIMELERI }
            if (kalan.size == 1) {
                when (kalan[0]) {
                    "kıs", "kısar", "kıssana", "azalt", "alçalt" -> return Niyet.Medya(MedyaEylemi.SES_KIS, miktar = miktar(t))
                    "yükselt", "artır", "arttır" -> return Niyet.Medya(MedyaEylemi.SES_AC, miktar = miktar(t))
                }
            }
            return null
        }
        val diger = t.filterIndexed { i, _ -> i != sesIndex }
        val yonVar = diger.any { it in SES_ARTIR || it in SES_AZALT }
        val sayiKelimeleri = diger.filterNot { yonVar && (it == "bir" || it == "bi") }
        val yuzdeIdx = sayiKelimeleri.indexOf("yüzde")
        if (yuzdeIdx >= 0) {
            val d = Sayilar.ilkSayi(sayiKelimeleri.drop(yuzdeIdx + 1))
            if (d != null) return Niyet.Medya(MedyaEylemi.SES_AYARLA, d.coerceIn(0L, 100L).toInt(), yuzde = true)
        }
        val sayi = Sayilar.ilkSayi(sayiKelimeleri.filterNot { it == "yüzde" })
        if (sayi != null) {
            return if (sayi == 0L) Niyet.Medya(MedyaEylemi.SES_KAPAT)
            else Niyet.Medya(MedyaEylemi.SES_AYARLA, sayi.coerceAtMost(1000L).toInt())
        }
        val s = diger.joinToString(" ")
        if (diger.any { it == "sonuna" || it == "fulle" || it == "full" || it == "maksimum" || it == "maximum" || it == "yükseğe" } ||
            s.contains("en yüksek") || s.contains("son ses")
        ) return Niyet.Medya(MedyaEylemi.SES_MAKS)
        if (s.contains("en düşük") || s.contains("en az") || diger.contains("minimum")) {
            return Niyet.Medya(MedyaEylemi.SES_AYARLA, 1)
        }
        if (diger.any { it == "kapat" || it == "kapa" || it == "kes" || it == "sustur" }) return Niyet.Medya(MedyaEylemi.SES_KAPAT)
        if (diger.any { it in SES_ARTIR }) return Niyet.Medya(MedyaEylemi.SES_AC, miktar = miktar(diger))
        if (diger.any { it in SES_AZALT }) return Niyet.Medya(MedyaEylemi.SES_KIS, miktar = miktar(diger))
        return null
    }

    // ---------------------------------------------------------------- medya (sonraki/önceki/durdur/devam)
    private val OYNAT_FIIL = setOf(
        "aç", "açar", "açsana", "çal", "çalar", "çalsana", "başlat", "başlatır", "oynat", "oynatır",
        "oynatsana", "koy", "koysana"
    )
    private val MEDYA_NESNE = setOf(
        "şarkı", "şarkıyı", "şarkıya", "şarkıdan", "parça", "parçayı", "parçaya", "müzik", "müziği",
        "müziğe", "müzikte", "bunu", "bu", "şu", "şunu", "bir", "bi", "çalanı", "çalan", "albüm", "albümü",
        "video", "videoyu", "lütfen", "hemen", "şimdi", "biraz", "kanal", "kanalı", "kanala", "istasyon",
        "istasyonu", "istasyona", "radyo", "radyoyu", "radyoda", "et", "ettir", "al", "dön", "git", "sar",
        "yap", "ver", "kaldığı", "yerden", "bakalım", "parçaya", "şarkılar", "sıradakini"
    )
    private val RADYO = setOf("radyo", "radyoyu", "radyoda")
    private val TV_NESNE = setOf("televizyon", "televizyonu", "tv", "tv'yi", "tvyi", "tv'yu")

    private fun medyaAnahtari(k: String): MedyaEylemi? = when {
        k.startsWith("durdur") || k.startsWith("duraklat") || k == "beklet" || k == "pause" || k == "sustur" -> MedyaEylemi.DURDUR
        k.startsWith("önceki") || k == "geri" || k == "previous" || k == "başa" || k == "baştan" ||
            k == "tekrar" || k == "yeniden" || k == "evvelki" -> MedyaEylemi.ONCEKI
        k.startsWith("sonraki") || k.startsWith("sıradaki") || k == "ileri" || k.startsWith("atla") ||
            k == "geç" || k == "geçir" || k == "geçsene" || k == "geçelim" || k.startsWith("değiştir") ||
            k == "next" || k == "skip" || k == "başka" || k.startsWith("diğer") || k.startsWith("öbür") -> MedyaEylemi.SONRAKI
        k.startsWith("devam") || k == "play" || k in OYNAT_FIIL -> MedyaEylemi.DEVAM
        else -> null
    }

    private fun medyaKomutu(t: List<String>): Niyet? {
        if (t == listOf("müzik") || t == listOf("müziği")) return Niyet.Medya(MedyaEylemi.DEVAM)
        val kapatVar = t.any { it == "kapat" || it == "kapa" || it == "kes" }
        val nesneVar = t.any { (it in MEDYA_NESNE || it in TV_NESNE) && it !in setOf("bir", "bi", "lütfen", "hemen", "şimdi", "biraz", "bakalım") }
        if (kapatVar && nesneVar && t.all { it in MEDYA_NESNE || it in TV_NESNE || it == "kapat" || it == "kapa" || it == "kes" }) {
            return Niyet.Medya(MedyaEylemi.DURDUR)
        }
        if (!t.all { it in MEDYA_NESNE || it in TV_NESNE || medyaAnahtari(it) != null }) return null
        val anahtarlar = t.mapNotNull { medyaAnahtari(it) }.toSet()
        val eylem = listOf(MedyaEylemi.DURDUR, MedyaEylemi.ONCEKI, MedyaEylemi.SONRAKI, MedyaEylemi.DEVAM)
            .firstOrNull { it in anahtarlar } ?: return null
        if (eylem == MedyaEylemi.DEVAM && t.any { it in RADYO || it in TV_NESNE }) return null // "radyoyu aç" → radyo
        return Niyet.Medya(eylem)
    }

    // ---------------------------------------------------------------- uygulamada arama ("YouTube'da Tarkan ara")
    private val ARAMA_HEDEFLERI = mapOf(
        "youtube" to HedefUygulama.YOUTUBE, "yutup" to HedefUygulama.YOUTUBE, "yutub" to HedefUygulama.YOUTUBE,
        "youtub" to HedefUygulama.YOUTUBE, "yotube" to HedefUygulama.YOUTUBE,
        "spotify" to HedefUygulama.SPOTIFY, "spotifay" to HedefUygulama.SPOTIFY, "spotfy" to HedefUygulama.SPOTIFY,
        "google" to HedefUygulama.GOOGLE, "gugil" to HedefUygulama.GOOGLE, "gugul" to HedefUygulama.GOOGLE,
        "internet" to HedefUygulama.GOOGLE, "web" to HedefUygulama.GOOGLE
    )
    private val HARITA_HEDEFLERI = setOf("harita", "haritalar", "maps", "yandex", "waze", "navigasyon")
    private val ARAMA_FIIL = setOf(
        "arat", "aratır", "aratsana", "aratın", "bul", "bulsana", "bulur", "bulun", "göster", "göstersene", "gösterir"
    )

    private fun uygulamadaArama(k: List<Kelime>): Niyet? {
        val t = k.map { it.n }
        var idx = -1
        var bas = -1
        var uygulama: HedefUygulama? = null
        var harita = false
        for (i in t.indices) {
            val (kok, _) = Ekler.bulunmaAyrilma(t[i]) ?: continue
            val kk = TurkceMetin.katla(kok)
            if (i > 0 && TurkceMetin.katla(t[i - 1]) == "youtube" && (kk == "muzik" || kk == "music")) {
                idx = i; bas = i - 1; uygulama = HedefUygulama.YOUTUBE_MUSIC; break
            }
            if (i > 0 && TurkceMetin.katla(t[i - 1]) == "google" && (kk == "haritalar" || kk == "harita" || kk == "maps")) {
                idx = i; bas = i - 1; harita = true; break
            }
            ARAMA_HEDEFLERI[kk]?.let { uygulama = it; idx = i; bas = i }
            if (idx >= 0) break
            if (kk in HARITA_HEDEFLERI) {
                idx = i; bas = i; harita = true; break
            }
        }
        if (idx < 0) return null
        val son = t.last()
        val aramaFiili = son in ARA_FIIL || son in ARAMA_FIIL
        val calFiili = son in OYNAT_FIIL || son in CAL_FIIL || dinleMi(son)
        val sorguKelimeleri = k.filterIndexed { i, _ -> i < bas || i > idx }
            .let { if ((aramaFiili || calFiili) && it.isNotEmpty() && it.last().n == son) it.dropLast(1) else it }
        val (sorgu, gorunen) = sarkiTemizle(sorguKelimeleri)
        if (harita) {
            if (sorgu.isEmpty()) return Niyet.UygulamaAc("harita", "Harita")
            return Niyet.Navigasyon(Hedef.Adres(sorgu, gorunen), rotaBaslat = false)
        }
        val u = uygulama ?: return null
        if (sorgu.isEmpty()) {
            val ad = when (u) {
                HedefUygulama.YOUTUBE -> "youtube"
                HedefUygulama.YOUTUBE_MUSIC -> "youtube müzik"
                HedefUygulama.SPOTIFY -> "spotify"
                HedefUygulama.GOOGLE -> "tarayıcı"
            }
            return Niyet.UygulamaAc(ad, TurkceMetin.basHarfBuyuk(ad))
        }
        return Niyet.SarkiCal(sorgu, gorunen, u, sadeceAra = !calFiili)
    }

    // ---------------------------------------------------------------- telefon araması
    private val ARA_FIIL = setOf(
        "ara", "arar", "arayın", "arayin", "arasana", "arasın", "arayabilir", "arayabilirsin", "arayabilirmisin",
        "ararmısın", "ararmisin", "arayalım", "arayacağım", "arayacağız", "aramak", "arıyorum", "arayıver",
        "arayıversene", "arayiver", "arayınız", "çaldır", "çaldırsana", "çaldırır", "çaldırın", "çaldırabilir"
    )
    private val TELEFON_ET = setOf("et", "eder", "etsene", "edebilir", "edin", "edermisin", "edelim", "aç", "açar", "açsana")
    private val BAGLA = setOf("bağla", "bağlar", "bağlasana", "bağlayın", "bağlayabilir")
    private val ARAMA_KONU = setOf(
        "şarkı", "şarkısı", "şarkısını", "şarkıları", "şarkılarını", "video", "videosu", "videosunu", "videoları",
        "videolarını", "klip", "klibi", "klibini", "müzik", "müziği", "albüm", "albümü", "albümünü", "film",
        "filmi", "dizi", "diziyi", "tarifi", "haber", "haberleri", "hava", "durumu"
    )
    private val ARA_HEDEF_DOLGU = setOf(
        "beni", "bana", "bizi", "hemen", "telefonla", "telefondan", "telefon", "bir", "bi", "şu", "şunu",
        "lütfen", "acil", "cepten", "cebinden", "cep", "cepte", "numarayı", "numarasını", "numarasından",
        "numarası", "telefonunu", "cebini", "şimdi", "acele", "bluetooth", "bluetoothtan", "bluetooth'tan",
        "bluetooth'la", "bluetoothla"
    )
    private val NUMARA_KELIMELERI = setOf("numarasını", "numarası", "numarayı", "telefonunu", "cebini", "numarasından")

    private fun aramaKomutu(k: List<Kelime>): Niyet? {
        val t = k.map { it.n }
        val son = t.last()
        val fiilBas: Int
        var yonelmeli = false
        var telefonAc = false
        when {
            son in ARA_FIIL -> fiilBas = t.size - 1
            t.size >= 2 && t[t.size - 2] == "telefon" && son in TELEFON_ET -> {
                fiilBas = t.size - 2
                yonelmeli = true
                telefonAc = son.startsWith("aç")
            }
            son in BAGLA && t.size >= 2 && Ekler.yonelme(t[t.size - 2]).silinen > 0 -> {
                fiilBas = t.size - 1
                yonelmeli = true
            }
            else -> return null
        }
        val hedefHam = k.subList(0, fiilBas)
        val hedefT = hedefHam.map { it.n }
        if (!yonelmeli && hedefT.any { it in ARAMA_KONU }) {
            val (sorgu, gorunen) = sarkiTemizle(hedefHam)
            return if (sorgu.isEmpty()) null else Niyet.SarkiCal(sorgu, gorunen, null, sadeceAra = true)
        }
        if (!yonelmeli && yakinlikVar(hedefT)) {
            val temiz = hedefHam.filterNot { it.n in setOf("bana", "bir", "bi", "lütfen", "hemen") }
            if (temiz.isNotEmpty()) {
                val sonK = temiz.last()
                val kesim = Ekler.belirtmeAdaylari(sonK.n).last()
                val sorgu = (temiz.dropLast(1).map { it.n } + kesim.kok).joinToString(" ")
                val gorunen = (temiz.dropLast(1).map { it.ham } + kesim.hamaUygula(sonK.ham)).joinToString(" ")
                return Niyet.Navigasyon(Hedef.Adres(sorgu, gorunen), rotaBaslat = false)
            }
        }
        val numaraModu = hedefT.any { it in NUMARA_KELIMELERI }
        val hedef = hedefHam.filterNot { it.n in ARA_HEDEF_DOLGU }
        if (hedef.isEmpty()) return if (telefonAc) null else Niyet.Ara()
        val numara = Sayilar.telefonNumarasi(hedef.map { it.n })
        if (numara != null) return Niyet.Ara(numara = numara)
        return Niyet.Ara(kisi = kisiSorgusu(hedef, yonelmeli, numaraModu))
    }

    private fun yakinlikVar(t: List<String>): Boolean {
        val s = t.joinToString(" ")
        return s.contains("en yakın") || t.any { it.startsWith("yakınımdaki") || it.startsWith("yakındaki") || it == "yakın" }
    }

    private fun kisiSorgusu(hedef: List<Kelime>, yonelmeli: Boolean, numaraModu: Boolean): KisiSorgusu {
        val son = hedef.last()
        val bas = hedef.dropLast(1)
        val basN = bas.map { it.n }
        val kesimler = when {
            numaraModu -> Ekler.ilgiAdaylari(son.n)
            yonelmeli -> Ekler.yonelmeAdaylari(son.n)
            else -> Ekler.belirtmeAdaylari(son.n)
        }
        val adaylar = mutableListOf<String>()
        for (ks in kesimler) adaylar += (basN + ks.kok).joinToString(" ")
        if (bas.isEmpty()) for (ks in kesimler) adaylar += Ekler.iyelikTabanlari(ks.kok)
        val kesin = son.n.contains('\'')
        val gorunen = (bas.map { it.ham } + kesimler.first().hamaUygula(son.ham)).joinToString(" ")
        return KisiSorgusu(gorunen, adaylar.distinct(), kesin)
    }

    /**
     * "Kimi arayayım?" sorusunun cevabı: "Ahmet", "annemi", "0532...", "Ahmet'i ara" ya da "iptal".
     */
    fun kisiCevabiCoz(metin: String): Niyet {
        val tum = TurkceMetin.kelimeler(metin)
        val t = tum.map { it.n }
        if (t.isEmpty()) return Niyet.Bilinmiyor(metin)
        if (iptalMi(t)) return Niyet.Iptal
        val dogrudan = coz(metin)
        if (dogrudan is Niyet.Ara && (dogrudan.numara != null || dogrudan.kisi != null)) return dogrudan
        val k = tum.filterNot { it.n in ARA_HEDEF_DOLGU || it.n in SON_DOLGU || it.n in BAS_DOLGU }
        if (k.isEmpty()) return Niyet.Bilinmiyor(metin)
        val numara = Sayilar.telefonNumarasi(k.map { it.n })
        if (numara != null) return Niyet.Ara(numara = numara)
        return Niyet.Ara(kisi = kisiSorgusu(k, yonelmeli = false, numaraModu = false))
    }

    // ---------------------------------------------------------------- radyo / televizyon
    private fun izleMi(k: String) = k.startsWith("izle") || k.startsWith("izlet")

    private fun radyoKelimesiMi(k: String): Boolean {
        val kok = k.substringBefore('\'')
        return kok.startsWith("radyo") || kok == "radio" || kok == "fm"
    }

    private fun tvKelimesiMi(k: String): Boolean {
        val kok = k.substringBefore('\'')
        return kok.startsWith("televizyon") || kok == "tv" || kok == "tvyi" || kok.startsWith("kanal")
    }

    /** Ad sayılmayan genel kelimeler. */
    private val RADYO_GENEL = setOf(
        "radyo", "radyoyu", "radyoda", "radyodan", "radyosu", "radyosunu", "internet", "online", "canlı", "bir", "bi"
    )
    private val TV_GENEL = setOf(
        "televizyon", "televizyonu", "televizyonda", "televizyondan", "kanalı", "kanalını", "kanala", "kanalda",
        "kanaldan", "kanalından", "canlı", "yayın", "yayını", "bir", "bi"
    )

    private fun radyoTvKomutu(k: List<Kelime>): Niyet? {
        val t = k.map { it.n }
        val son = t.last()
        val oynat = son in AC_FIIL || son in CAL_FIIL || dinleMi(son) || izleMi(son)
        if (!oynat && t.size > 1) return null
        val nesne = if (oynat) k.dropLast(1) else k
        val nt = nesne.map { it.n }
        if (nt.any { it in UYGULAMA_KELIMELERI }) return null
        val tv = nt.any { tvKelimesiMi(it) } || (izleMi(son) && nt.isNotEmpty())
        val radyo = !tv && nt.any { radyoKelimesiMi(it) }
        if (!tv && !radyo) return if (izleMi(son) && nt.isEmpty()) Niyet.Televizyon() else null
        if (tv) {
            var ad = nesne.filterNot { it.n in TV_GENEL || it.n in NESNE_BAS_DOLGU }
            if (ad.isNotEmpty() && ad.last().n == "kanal") ad = ad.dropLast(1)
            val kalan = ad.map { it.n.substringBefore('\'') }
            if (kalan.isEmpty() || kalan == listOf("tv")) return Niyet.Televizyon()
            return Niyet.Televizyon(kalan.joinToString(" "), ad.joinToString(" ") { it.ham.substringBefore('\'') })
        }
        val ad = nesne.filterNot { it.n in RADYO_GENEL || it.n in NESNE_BAS_DOLGU }
        if (ad.isEmpty()) return Niyet.Radyo()
        return Niyet.Radyo(
            ad.joinToString(" ") { it.n.substringBefore('\'') },
            ad.joinToString(" ") { it.ham.substringBefore('\'') }
        )
    }

    // ---------------------------------------------------------------- navigasyon
    private fun hareketFiiliMi(k: String) =
        (k.startsWith("git") && !k.startsWith("gitar")) || k.startsWith("gid") || k.startsWith("götür") ||
            k == "sür" || k == "sürsene" || k == "sürelim" || k.startsWith("yönlendir")

    private fun anahtarKelimeMi(k: String, sonraki: String?) =
        k.startsWith("rota") || k.startsWith("navigasyon") || k == "navi" ||
            (k == "yol" && sonraki != null && (sonraki.startsWith("tarif") || sonraki.startsWith("göster")))

    private val EV = setOf("ev", "evim", "evimiz", "ev adresi", "ev adresim", "eve", "evime")
    private val IS = setOf(
        "iş", "işim", "iş yeri", "işyeri", "iş yerim", "işyerim", "ofis", "ofisim", "şirket", "şirketim",
        "iş adresi", "iş adresim", "büro", "işe"
    )
    private val NAV_SON_FIIL = setOf(
        "başlat", "oluştur", "çiz", "hesapla", "ayarla", "yap", "aç", "çıkar", "ver", "al", "göster", "bul",
        "kur", "et", "lütfen", "başla", "başlatır", "oluşturur", "çizer", "verir", "tarifi", "tarif", "bana",
        "hemen", "başlatsana", "oluştursana", "çizsene"
    )
    private val NAV_BAS_DOLGU = setOf("beni", "bizi", "bana", "lütfen", "hemen", "şimdi", "acil", "bir", "bi", "hadi")
    private val NAV_SON_DOLGU = setOf("doğru", "kadar", "nasıl", "için", "bir", "lütfen", "yol", "olan")
    private val HAREKET_SONRASI = setOf("lütfen", "hadi", "bakalım", "beni", "bizi", "hemen", "artık")

    private fun navigasyonKomutu(k: List<Kelime>): Niyet? {
        val t = k.map { it.n }
        var v = -1
        var hareket = false
        for (i in t.indices) {
            if (hareketFiiliMi(t[i])) {
                v = i; hareket = true; break
            }
            if (anahtarKelimeMi(t[i], t.getOrNull(i + 1))) {
                v = i; break
            }
        }
        if (v < 0) {
            val d = t.indexOfFirst { it == "dön" || it.startsWith("dönelim") || it.startsWith("döneceğ") || it == "dönüyorum" }
            if (d > 0 && t[d - 1] in setOf("eve", "evime", "işe")) {
                v = d; hareket = true
            } else {
                return null
            }
        }
        val anahtarBasta = v == 0
        var hedef: List<Kelime>
        if (!anahtarBasta) {
            val sonrasi = t.drop(v + 1)
            val uygun = if (hareket) {
                sonrasi.all { it in HAREKET_SONRASI || hareketFiiliMi(it) }
            } else {
                sonrasi.all { it in NAV_SON_FIIL || anahtarKelimeMi(it, null) || it.startsWith("tarif") || it.startsWith("göster") }
            }
            if (!uygun) return null
            hedef = k.subList(0, v)
        } else {
            val atla = if (t[0] == "yol") 2 else 1
            hedef = if (atla < k.size) k.subList(atla, k.size) else emptyList()
            hedef = hedef.dropLastWhile { it.n in NAV_SON_FIIL || hareketFiiliMi(it.n) }
        }
        hedef = hedef.dropWhile { it.n in NAV_BAS_DOLGU }
        if (hedef.size >= 3 && hedef[0].n == "en" && (hedef[1].n == "kısa" || hedef[1].n == "hızlı") && hedef[2].n.startsWith("yol")) {
            hedef = hedef.drop(3)
        }
        hedef = hedef.dropLastWhile { it.n in NAV_SON_DOLGU }
        if (hedef.isEmpty()) return null
        val son = hedef.last()
        val kesim = Ekler.yonelme(son.n, guvenli = !hareket)
        val sorgu = (hedef.dropLast(1).map { it.n } + kesim.kok).joinToString(" ")
        if (sorgu in EV) return Niyet.Navigasyon(Hedef.Ev)
        if (sorgu in IS) return Niyet.Navigasyon(Hedef.Is)
        val gorunen = (hedef.dropLast(1).map { it.ham } + kesim.hamaUygula(son.ham)).joinToString(" ")
        return Niyet.Navigasyon(Hedef.Adres(sorgu, gorunen))
    }

    // ---------------------------------------------------------------- uygulama açma / şarkı çalma
    private val AC_FIIL = setOf(
        "aç", "açar", "açsana", "açın", "açabilir", "açabilirsin", "açabilirmisin", "açarmısın", "açalım",
        "açıver", "açıversene", "başlat", "başlatır", "başlatsana", "başlatın", "çalıştır", "çalıştırır",
        "çalıştırsana", "göster", "göstersene"
    )
    private val CAL_FIIL = setOf(
        "çal", "çalar", "çalsana", "çalın", "çalabilir", "çalabilirsin", "çalalım", "çalıver", "çalıversene",
        "çalarmısın", "oynat", "oynatır", "oynatsana", "oynatalım", "oynatın", "koy", "koyar", "koysana", "koyalım"
    )

    private fun dinleMi(k: String) = k.startsWith("dinle") || k.startsWith("dinlet")

    private val NESNE_BAS_DOLGU = setOf("bana", "bize", "bir", "bi", "biraz", "şu", "şunu", "lütfen", "hemen", "şimdi", "acaba", "hadi", "bakalım")
    private val NESNE_SON_DOLGU = setOf("lütfen", "hemen", "bir", "bi", "biraz", "şimdi", "bakalım")
    private val GENEL_MUZIK = setOf("müzik", "müziği", "şarkı", "şarkıyı", "bir", "bi", "biraz", "parça", "parçayı", "müzikler", "şarkılar")
    private val UYGULAMA_KELIMELERI = setOf(
        "uygulama", "uygulaması", "uygulamasını", "uygulamayı", "programı", "programını", "program", "app",
        "aplikasyonu", "aplikasyon"
    )
    private val SARKI_ATILACAK = setOf(
        "şarkısını", "şarkısı", "şarkıyı", "şarkılarını", "şarkıları", "şarkısından", "şarkılarından",
        "parçasını", "parçası", "parçalarını", "albümünü", "albümü", "videosunu", "videosu", "videolarını",
        "klibini", "klibi", "müziğini", "müzikleri", "müziklerini"
    )

    /** Şarkı sorgusunu temizler: "Tarkan'ın Şımarık şarkısını" → "tarkan şımarık" / "Tarkan Şımarık". */
    private fun sarkiTemizle(kelimeler: List<Kelime>): Pair<String, String> {
        var l = kelimeler.filterNot { it.n in SARKI_ATILACAK }
        l = l.dropWhile { it.n in NESNE_BAS_DOLGU }.dropLastWhile { it.n in NESNE_SON_DOLGU }
        val n = l.joinToString(" ") { it.n.substringBefore('\'') }.trim()
        val ham = l.joinToString(" ") { it.ham.substringBefore('\'') }.trim()
        return n to ham
    }

    private fun acCalKomutu(k: List<Kelime>, uygulamalar: Collection<String>): Niyet? {
        val t = k.map { it.n }
        val son = t.last()
        val acMi = son in AC_FIIL
        val calMi = son in CAL_FIIL || dinleMi(son)
        if (!acMi && !calMi) return null
        val gosterMi = son == "göster" || son == "göstersene"
        val nesne = k.dropLast(1).dropWhile { it.n in NESNE_BAS_DOLGU }.dropLastWhile { it.n in NESNE_SON_DOLGU }
        if (nesne.isEmpty()) return if (gosterMi) null else Niyet.Medya(MedyaEylemi.DEVAM)
        val nt = nesne.map { it.n }
        if (nt.all { it in GENEL_MUZIK }) return Niyet.Medya(MedyaEylemi.DEVAM)
        if (acMi) {
            val temiz = nesne.dropLastWhile { it.n in UYGULAMA_KELIMELERI }
            val uygulamaKelimesiVardi = temiz.size < nesne.size
            if (temiz.isNotEmpty()) {
                val sonK = temiz.last()
                val bas = temiz.dropLast(1)
                val kesimler = Ekler.belirtmeAdaylari(sonK.n)
                val adaylar = kesimler.map { (bas.map { it.n } + it.kok).joinToString(" ") }
                val eslesen = adaylar.indexOfFirst { UygulamaEslestirici.bilinenUygulamaMi(it, uygulamalar) }
                val kesmeli = sonK.n.contains('\'')
                if (eslesen >= 0 || kesmeli || uygulamaKelimesiVardi) {
                    val secilen = if (eslesen >= 0) eslesen else adaylar.lastIndex
                    val gorunen = (bas.map { it.ham } + kesimler[secilen].hamaUygula(sonK.ham)).joinToString(" ")
                    return Niyet.UygulamaAc(adaylar[secilen], gorunen, sarkiOlabilir = eslesen < 0 && !uygulamaKelimesiVardi)
                }
            }
            if (gosterMi) return null
        }
        val (sorgu, gorunen) = sarkiTemizle(nesne)
        if (sorgu.isEmpty()) return Niyet.Medya(MedyaEylemi.DEVAM)
        return Niyet.SarkiCal(sorgu, gorunen)
    }

    // ---------------------------------------------------------------- giriş noktaları

    /**
     * Tek bir metni çözer. [uygulamalar]: yüklü uygulama etiketleri ("X aç" uygulama mı şarkı mı kararı için).
     */
    fun coz(metin: String, uygulamalar: Collection<String> = emptyList()): Niyet {
        val tum = TurkceMetin.kelimeler(metin)
        if (tum.isEmpty()) return Niyet.Bilinmiyor(metin)
        if (iptalMi(tum.map { it.n })) return Niyet.Iptal
        var k = tum
        while (k.isNotEmpty() && k.first().n in BAS_DOLGU) k = k.drop(1)
        while (k.isNotEmpty() && k.last().n in SON_DOLGU) k = k.dropLast(1)
        while (k.size > 1 && (k.first().n == "bir" || k.first().n == "bi")) k = k.drop(1)
        if (k.isEmpty()) return Niyet.Bilinmiyor(metin)
        val t = k.map { it.n }
        if (iptalMi(t)) return Niyet.Iptal
        saatMi(t)?.let { return it }
        sesKomutu(t)?.let { return it }
        medyaKomutu(t)?.let { return it }
        uygulamadaArama(k)?.let { return it }
        aramaKomutu(k)?.let { return it }
        radyoTvKomutu(k)?.let { return it }
        navigasyonKomutu(k)?.let { return it }
        acCalKomutu(k, uygulamalar)?.let { return it }
        return Niyet.Bilinmiyor(metin)
    }

    /** Konuşma tanımanın verdiği alternatiflerden ilk anlaşılanı seçer. */
    fun cozAdaylar(metinler: List<String>, uygulamalar: Collection<String> = emptyList()): Niyet {
        var ilk: Niyet? = null
        for (m in metinler) {
            val n = coz(m, uygulamalar)
            if (n !is Niyet.Bilinmiyor) return n
            if (ilk == null) ilk = n
        }
        return ilk ?: Niyet.Bilinmiyor("")
    }
}
