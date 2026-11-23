package com.egea.asistan.core

import java.util.Locale

object TurkceMetin {
    private val TR = Locale.forLanguageTag("tr-TR")
    fun normalize(metin: String): String = metin.replace("İ", "i").replace("I", "ı").lowercase(TR).trim()
}
