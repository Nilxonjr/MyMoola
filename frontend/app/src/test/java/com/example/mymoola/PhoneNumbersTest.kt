package com.example.mymoola

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumbersTest {
    @Test
    fun normalizesCommonKenyanFormats() {
        assertEquals("+254712345678", normalizeKenyanPhone("0712345678"))
        assertEquals("+254712345678", normalizeKenyanPhone("+254712345678"))
        assertEquals("+254712345678", normalizeKenyanPhone("254712345678"))
        assertEquals("", normalizeKenyanPhone("123"))
    }
}
