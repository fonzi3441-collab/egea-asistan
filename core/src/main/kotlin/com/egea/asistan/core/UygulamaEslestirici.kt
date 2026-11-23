package com.egea.asistan.core

/** Yüklü uygulamaları söylenen adla bulanık eşleştirir ("YouTube'u aç", "haritayı aç", "radyoyu aç"). */
object UygulamaEslestirici {
    data class Uygulama(val etiket: String, val paket: String)

    private val HARITA = listOf("maps", "haritalar", "harita", "navigasyon", "navigation", "navi", "navigator", "waze", "yandex")
    private val MUZIK = listOf("music", "müzik", "player", "çalar", "audio", "spotify")
    private val RADYO = listOf("radio", "radyo", "fm", "tuner")
    private val TELEFON = listOf("phone", "telefon", "bluetooth", "bt", "dialer", "call", "handsfree", "bt phone")
    private val TARAYICI = listOf("chrome", "browser", "tarayıcı", "internet", "web")
    private val MAGAZA = listOf("play store", "store", "market", "mağaza", "google play")

    /** Türkçe/kısa adlar → uygulama etiketlerinde geçebilecek kelimeler. */
    val TAKMA_ADLAR: Map<String, List<String>> = mapOf(
        "harita" to HARITA, "haritalar" to HARITA, "navigasyon" to HARITA, "maps" to HARITA, "navi" to HARITA,
        "müzik" to MUZIK, "müzik çalar" to MUZIK,
        "radyo" to RADYO,
        "telefon" to TELEFON, "bluetooth" to listOf("bluetooth", "bt"), "arama" to TELEFON,
        "ayarlar" to listOf("settings", "ayarlar", "ayar"),
        "kamera" to listOf("camera", "kamera", "dvr"),
        "galeri" to listOf("gallery", "galeri", "photos", "fotoğraflar", "resimler"),
        "fotoğraflar" to listOf("gallery", "galeri", "photos", "fotoğraflar"),
        "video" to listOf("video", "videos", "videolar", "movie"),
        "tarayıcı" to TARAYICI, "internet" to TARAYICI,
        "dosyalar" to listOf("files", "dosyalar", "file manager", "dosya yöneticisi", "explorer"),
        "dosya yöneticisi" to listOf("files", "file manager", "explorer"),
        "hava durumu" to listOf("weather", "hava", "hava durumu"),
        "takvim" to listOf("calendar", "takvim"),
        "hesap makinesi" to listOf("calculator", "hesap makinesi"),
        "mağaza" to MAGAZA, "play store" to MAGAZA, "market" to MAGAZA,
        "mesajlar" to listOf("messages", "mesajlar", "sms"),
        "rehber" to listOf("contacts", "kişiler", "rehber"),
        "kişiler" to listOf("contacts", "kişiler", "rehber"),
        "youtube" to listOf("youtube"),
        "youtube müzik" to listOf("youtube music"),
        "spotify" to listOf("spotify"),
        "waze" to listOf("waze"),
        "yandex" to listOf("yandex"),
        "google haritalar" to listOf("maps", "google maps", "haritalar"),
        "chrome" to listOf("chrome"),
        "whatsapp" to listOf("whatsapp"),
        "netflix" to listOf("netflix"),
        "telegram" to listOf("telegram")
    )

    private fun tekilPuan(sorgu: String, u: Uygulama): Double {
        val q = TurkceMetin.katla(sorgu)
        val e = TurkceMetin.katla(u.etiket)
        if (q.isEmpty() || e.isEmpty()) return 0.0
        if (e == q) return 1.0
        if (e.replace(" ", "") == q.replace(" ", "")) return 0.98
        val et = e.split(' ')
        val qt = q.split(' ')
        if (q.length >= 3 && e.startsWith(q)) return 0.9
        if (q.length >= 3 && et.contains(q)) return 0.88
        if (qt.size > 1 && et.containsAll(qt)) return 0.86
        if (e.length >= 3 && q.startsWith(e)) return 0.85
        val paketParcalari = u.paket.lowercase().split('.', '_')
        if (q.length >= 4 && paketParcalari.contains(q.replace(" ", ""))) return 0.8
        var oran = Benzerlik.oran(q, e)
        if (qt.size == 1) for (t in et) if (t.length >= 3) oran = maxOf(oran, Benzerlik.oran(q, t) - 0.05)
        return oran
    }

    /** Söylenen ad ile uygulama arasındaki puan (takma adlar dahil). */
    fun puan(sorgu: String, u: Uygulama): Double {
        var enIyi = tekilPuan(sorgu, u)
        val takma = TAKMA_ADLAR[TurkceMetin.kucukHarf(sorgu).trim()]
        if (takma != null) for (t in takma) enIyi = maxOf(enIyi, tekilPuan(t, u) * 0.93)
        return enIyi
    }

    /** En uygun uygulama; [esik] altındaysa null. Eşitlikte etiketi kısa olan seçilir. */
    fun bul(sorgu: String, uygulamalar: List<Uygulama>, esik: Double = 0.72): Uygulama? {
        var enIyi: Uygulama? = null
        var enIyiPuan = 0.0
        for (u in uygulamalar) {
            val p = puan(sorgu, u)
            if (p > enIyiPuan + 1e-9 || (enIyi != null && kotlin.math.abs(p - enIyiPuan) < 1e-9 && u.etiket.length < enIyi.etiket.length)) {
                enIyi = u
                enIyiPuan = p
            }
        }
        return if (enIyiPuan >= esik) enIyi else null
    }

    /** Ad bilinen bir uygulama türü mü ya da yüklü bir uygulamanın adına çok mu benziyor? */
    fun bilinenUygulamaMi(ad: String, etiketler: Collection<String>): Boolean {
        val k = TurkceMetin.kucukHarf(ad).trim()
        if (k.isEmpty()) return false
        if (TAKMA_ADLAR.containsKey(k)) return true
        return etiketler.any { tekilPuan(k, Uygulama(it, "")) >= 0.86 }
    }
}
