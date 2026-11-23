package com.egea.asistan.core

import org.junit.Assert.assertEquals
import org.junit.Test

class IskeletTest {
    @Test
    fun turkceKucukHarf() {
        assertEquals("ıi̇stanbul".replace("i̇", "i"), TurkceMetin.normalize("IİSTANBUL"))
    }
}
