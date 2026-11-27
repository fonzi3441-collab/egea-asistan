package com.egea.asistan.core

import java.net.URLDecoder

/**
 * M3U/M3U8 kanal listesi okuyucu (IPTV listeleri). #EXTINF öznitelikleri (tvg-id, tvg-logo, group-title),
 * #EXTVLCOPT başlıkları (http-user-agent, http-referrer) ve Kodi tarzı "url|User-Agent=..." desteklenir.
 */
object M3uOkuyucu {
    data class Kanal(
        /** Temiz ad: "Kanal D (Turkiye) (1080p)" → "Kanal D". */
        val ad: String,
        val hamAd: String,
        val url: String,
        val logo: String?,
        val grup: String?,
        val kimlik: String?,
        val basliklar: Map<String, String>,
        /** Parantez/köşeli parantez etiketleri: "1080p", "Geo-blocked", "Not 24/7", "Turkiye". */
        val etiketler: List<String>
    )

    private val OZNITELIK = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val SON_PARANTEZ = Regex("""\s*[(\[]([^)\]]*)[)\]]\s*$""")

    fun oku(metin: String): List<Kanal> {
        val sonuc = mutableListOf<Kanal>()
        var bilgi: String? = null
        var basliklar = linkedMapOf<String, String>()
        var grupSatiri: String? = null
        for (hamSatir in metin.lineSequence()) {
            val s = hamSatir.trim().trimStart('﻿')
            if (s.isEmpty()) continue
            when {
                s.startsWith("#EXTINF", ignoreCase = true) -> {
                    bilgi = s
                    basliklar = linkedMapOf()
                    grupSatiri = null
                }
                s.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val g = s.substringAfter(':')
                    val anahtar = g.substringBefore('=').trim().lowercase()
                    val deger = g.substringAfter('=', "").trim()
                    if (deger.isNotEmpty()) {
                        when (anahtar) {
                            "http-user-agent" -> basliklar["User-Agent"] = deger
                            "http-referrer", "http-referer" -> basliklar["Referer"] = deger
                            "http-origin" -> basliklar["Origin"] = deger
                        }
                    }
                }
                s.startsWith("#EXTGRP:", ignoreCase = true) -> grupSatiri = s.substringAfter(':').trim()
                s.startsWith("#") -> Unit
                else -> {
                    var url = s
                    if (url.contains('|')) {
                        val parcalar = url.split('|', limit = 2)
                        url = parcalar[0]
                        for (kv in parcalar[1].split('&')) {
                            val k = kv.substringBefore('=').trim()
                            val v = kv.substringAfter('=', "").trim()
                            if (k.isNotEmpty() && v.isNotEmpty()) basliklar[baslikAdi(k)] = coz(v)
                        }
                    }
                    val b = bilgi
                    val gecerli = url.startsWith("http://") || url.startsWith("https://") ||
                        url.startsWith("rtmp") || url.startsWith("rtsp")
                    if (b != null && gecerli) sonuc += kanalYap(b, url, basliklar.toMap(), grupSatiri)
                    bilgi = null
                    basliklar = linkedMapOf()
                    grupSatiri = null
                }
            }
        }
        return sonuc
    }

    private fun coz(v: String): String = try {
        URLDecoder.decode(v, "UTF-8")
    } catch (e: Exception) {
        v
    }

    private fun baslikAdi(k: String): String = when (k.lowercase()) {
        "user-agent" -> "User-Agent"
        "referer", "referrer" -> "Referer"
        "origin" -> "Origin"
        else -> k
    }

    private fun kanalYap(extinf: String, url: String, basliklar: Map<String, String>, grupSatiri: String?): Kanal {
        val oz = OZNITELIK.findAll(extinf).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val hamAd = adiBul(extinf).ifBlank { oz["tvg-name"] ?: url }
        val (temiz, etiketler) = temizle(hamAd)
        return Kanal(
            ad = temiz.ifBlank { hamAd },
            hamAd = hamAd,
            url = url,
            logo = oz["tvg-logo"]?.takeIf { it.isNotBlank() },
            grup = (oz["group-title"] ?: grupSatiri)?.takeIf { it.isNotBlank() },
            kimlik = oz["tvg-id"]?.takeIf { it.isNotBlank() },
            basliklar = basliklar,
            etiketler = etiketler
        )
    }

    /** #EXTINF satırında tırnak dışındaki ilk virgülden sonrası kanal adıdır. */
    private fun adiBul(s: String): String {
        var tirnak = false
        for (i in s.indices) {
            val c = s[i]
            if (c == '"') tirnak = !tirnak else if (c == ',' && !tirnak) return s.substring(i + 1).trim()
        }
        return ""
    }

    /** Sondaki (1080p), (Turkiye), [Geo-blocked] gibi etiketleri ayırır. */
    fun temizle(ad: String): Pair<String, List<String>> {
        var s = ad.trim()
        val etiketler = mutableListOf<String>()
        while (true) {
            val m = SON_PARANTEZ.find(s) ?: break
            if (m.range.first == 0) break
            etiketler.add(0, m.groupValues[1].trim())
            s = s.substring(0, m.range.first).trim()
        }
        return s to etiketler
    }
}
