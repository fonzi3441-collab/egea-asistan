package com.egea.asistan.core

import java.io.File

/** Testler için en basit WAV okuyucu: mono, 16-bit PCM ya da 8-bit G.711 mu-law. */
object Wav {
    private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, i: Int) = le16(b, i) or (le16(b, i + 2) shl 16)

    private fun mulawCoz(u: Int): Short {
        val x = u.inv() and 0xFF
        val isaret = x and 0x80
        val us = (x shr 4) and 0x07
        val mantis = x and 0x0F
        val deger = (((mantis shl 3) + 0x84) shl us) - 0x84
        return (if (isaret != 0) -deger else deger).toShort()
    }

    fun oku(baytlar: ByteArray): ShortArray {
        var bicim = 1
        var bit = 16
        var i = 12
        while (i + 8 <= baytlar.size) {
            val kimlik = String(baytlar, i, 4, Charsets.US_ASCII)
            val boy = le32(baytlar, i + 4)
            val bas = i + 8
            when (kimlik) {
                "fmt " -> {
                    bicim = le16(baytlar, bas)
                    bit = le16(baytlar, bas + 14)
                }
                "data" -> {
                    val son = minOf(bas + boy, baytlar.size)
                    return when {
                        bicim == 7 && bit == 8 -> ShortArray(son - bas) { k -> mulawCoz(baytlar[bas + k].toInt() and 0xFF) }
                        bicim == 1 && bit == 16 -> ShortArray((son - bas) / 2) { k -> le16(baytlar, bas + 2 * k).toShort() }
                        else -> error("desteklenmeyen WAV biçimi: $bicim / $bit bit")
                    }
                }
            }
            i = bas + boy + (boy and 1)
        }
        error("WAV içinde data bölümü yok")
    }

    fun oku(f: File): ShortArray = oku(f.readBytes())
}
