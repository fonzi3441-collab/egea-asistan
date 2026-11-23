package com.egea.asistan.core

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * .vcf (vCard 2.1 / 3.0 / 4.0) rehber dosyası okuyucu. Telefonların "rehberi dışa aktar" çıktısındaki
 * QUOTED-PRINTABLE + UTF-8 isimleri ve katlanmış satırları destekler.
 */
object VcfOkuyucu {
    data class Numara(val numara: String, val tur: String)
    data class Kisi(val ad: String, val numaralar: List<Numara>)

    fun oku(metin: String): List<Kisi> {
        val satirlar = satirlariAc(metin)
        val kisiler = mutableListOf<Kisi>()
        var fn: String? = null
        var n: String? = null
        var numaralar = mutableListOf<Numara>()
        var icinde = false
        for (satir in satirlar) {
            val ust = satir.uppercase()
            if (ust.startsWith("BEGIN:VCARD")) {
                icinde = true; fn = null; n = null; numaralar = mutableListOf()
                continue
            }
            if (ust.startsWith("END:VCARD")) {
                if (icinde) {
                    val ad = (fn?.takeIf { it.isNotBlank() } ?: n?.let { nAlaniniCevir(it) })?.trim().orEmpty()
                    val tekil = numaralar.distinctBy { it.numara }
                    if (tekil.isNotEmpty()) kisiler += Kisi(ad.ifEmpty { tekil.first().numara }, tekil)
                }
                icinde = false
                continue
            }
            if (!icinde) continue
            val iki = satir.indexOf(':')
            if (iki <= 0) continue
            val sol = satir.substring(0, iki)
            val deger = satir.substring(iki + 1)
            val parcalar = sol.split(';')
            val ozellik = parcalar[0].substringAfter('.').uppercase() // "item1.TEL" → TEL
            val parametreler = parcalar.drop(1).map { it.uppercase() }
            val qp = parametreler.any { it.contains("QUOTED-PRINTABLE") }
            val charset = parametreler.firstOrNull { it.startsWith("CHARSET=") }?.substringAfter('=') ?: "UTF-8"
            fun coz(v: String) = kacisCoz(if (qp) qpCoz(v, charset) else v)
            when (ozellik) {
                "FN" -> fn = coz(deger)
                "N" -> n = coz(deger)
                "TEL" -> {
                    val numara = numaraTemizle(deger.removePrefix("tel:").removePrefix("TEL:"))
                    if (numara.count { it.isDigit() } >= 3) numaralar += Numara(numara, turBul(parametreler))
                }
            }
        }
        return kisiler
    }

    /** Katlanmış satırları (boşluk/sekme ile başlayan) ve QP yumuşak satır sonlarını (=) birleştirir. */
    private fun satirlariAc(metin: String): List<String> {
        val ham = metin.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val sonuc = mutableListOf<String>()
        for (s in ham) {
            if ((s.startsWith(" ") || s.startsWith("\t")) && sonuc.isNotEmpty()) {
                sonuc[sonuc.lastIndex] = sonuc.last() + s.substring(1)
            } else if (sonuc.isNotEmpty() && sonuc.last().endsWith("=") && sonuc.last().uppercase().contains("QUOTED-PRINTABLE")) {
                sonuc[sonuc.lastIndex] = sonuc.last().dropLast(1) + s
            } else {
                sonuc += s
            }
        }
        return sonuc
    }

    private fun qpCoz(v: String, charset: String): String {
        val cikti = ByteArrayOutputStream()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '=' && i + 2 < v.length) {
                val hex = v.substring(i + 1, i + 3)
                val b = hex.toIntOrNull(16)
                if (b != null) {
                    cikti.write(b); i += 3; continue
                }
            }
            if (c == '=' && i == v.length - 1) { i++; continue }
            val bytes = c.toString().toByteArray(Charsets.UTF_8)
            cikti.write(bytes, 0, bytes.size)
            i++
        }
        return try {
            String(cikti.toByteArray(), Charset.forName(charset))
        } catch (e: Exception) {
            String(cikti.toByteArray(), Charsets.UTF_8)
        }
    }

    private fun kacisCoz(v: String): String =
        v.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /** N alanı: Soyad;Ad;İkinci ad;Ön ek;Son ek → "Ad İkinci Soyad". */
    private fun nAlaniniCevir(n: String): String {
        val p = n.split(';')
        val soyad = p.getOrNull(0).orEmpty()
        val ad = p.getOrNull(1).orEmpty()
        val ikinci = p.getOrNull(2).orEmpty()
        return listOf(ad, ikinci, soyad).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun numaraTemizle(s: String): String {
        val t = s.trim()
        val arti = t.startsWith("+")
        return (if (arti) "+" else "") + t.filter { it.isDigit() }
    }

    private fun turBul(parametreler: List<String>): String {
        val hepsi = parametreler.joinToString(",")
        return when {
            hepsi.contains("CELL") || hepsi.contains("MOBILE") -> "cep"
            hepsi.contains("HOME") -> "ev"
            hepsi.contains("WORK") -> "iş"
            else -> "diğer"
        }
    }
}
