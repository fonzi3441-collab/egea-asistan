package com.egea.asistan.medya

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.egea.asistan.core.RadyoAdi
import com.egea.asistan.core.TurkceMetin
import org.json.JSONObject

/** İnternet radyosu istasyonu (Radio Browser veritabanından). */
data class Istasyon(
    val kimlik: String,
    val ad: String,
    val url: String,
    val logo: String?,
    val etiketler: String,
    val ulke: String,
    val codec: String,
    val bitHizi: Int
) {
    /** Kartlarda ve sesli cevapta kullanılacak ad: "KRAL FM" → "Kral FM", kısa kısaltmalar (FM, TRT) korunur. */
    val gorunenAd: String
        get() = ad.trim().split(Regex("\\s+")).joinToString(" ") { w ->
            if (w.length > 3 && w.all { !it.isLetter() || it.isUpperCase() } && w.any { it.isLetter() }) {
                TurkceMetin.basHarfBuyuk(TurkceMetin.kucukHarf(w))
            } else {
                w
            }
        }

    val etiketListesi: List<String>
        get() = etiketler.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun mediaItem(): MediaItem {
        val uri = Uri.parse(url)
        val meta = MediaMetadata.Builder()
            .setTitle(gorunenAd)
            .setStation(gorunenAd)
            .setArtist(etiketListesi.take(3).joinToString(", ").ifBlank { "Canlı radyo" })
            .setArtworkUri(logo?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()
        return MediaItem.Builder()
            .setMediaId(kimlik)
            .setUri(uri)
            .setMimeType(mimeTuru(url))
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(meta)
            .build()
    }

    fun json(): JSONObject = JSONObject()
        .put("id", kimlik).put("ad", ad).put("url", url).put("logo", logo ?: "")
        .put("etiket", etiketler).put("ulke", ulke).put("codec", codec).put("bit", bitHizi)

    companion object {
        fun mimeTuru(url: String): String? = if (url.contains(".m3u8", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else null

        fun jsondan(o: JSONObject): Istasyon? {
            val url = o.optString("url")
            if (url.isBlank()) return null
            return Istasyon(
                o.optString("id").ifBlank { url },
                RadyoAdi.sadelestir(o.optString("ad").ifBlank { url }),
                url,
                o.optString("logo").takeIf { it.isNotBlank() },
                o.optString("etiket"),
                o.optString("ulke"),
                o.optString("codec"),
                o.optInt("bit")
            )
        }

        /** Oynatıcıdaki öğeden istasyon (son dinleneni kaydetmek için; ağ ya da disk gerektirmez). */
        fun ogeden(oge: MediaItem): Istasyon? {
            val uri = (oge.requestMetadata.mediaUri ?: oge.localConfiguration?.uri)?.toString() ?: return null
            val m = oge.mediaMetadata
            val ad = (m.station ?: m.title)?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val etiket = m.artist?.toString()?.takeIf { it != "Canlı radyo" }.orEmpty()
            return Istasyon(oge.mediaId.ifBlank { uri }, ad, uri, m.artworkUri?.toString(), etiket, "", "", 0)
        }

        /** Radio Browser API kaydından. */
        fun radioBrowserdan(o: JSONObject): Istasyon? {
            // Son denetimde çalışmayan yayınlar (hidebroken'a ek, istemci tarafında da) alınmaz.
            if (o.optInt("lastcheckok", 1) == 0) return null
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
            val ad = RadyoAdi.sadelestir(o.optString("name"))
            if (url.isBlank() || ad.isBlank()) return null
            if (!url.startsWith("http://") && !url.startsWith("https://")) return null
            return Istasyon(
                o.optString("stationuuid").ifBlank { url },
                ad,
                url,
                o.optString("favicon").trim().takeIf { it.startsWith("http") },
                o.optString("tags"),
                o.optString("countrycode"),
                o.optString("codec"),
                o.optInt("bitrate")
            )
        }
    }
}
