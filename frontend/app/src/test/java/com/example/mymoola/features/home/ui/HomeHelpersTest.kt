package com.example.mymoola.features.home.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHelpersTest {
    @Test
    fun parseHomeInstant_handlesIsoTimestamps() {
        assertNotNull(parseHomeInstant("2026-06-18T10:00:00Z"))
        assertNotNull(parseHomeInstant("2026-06-18T13:00:00+03:00"))
    }

    @Test
    fun failedHomeStatuses_coverTerminalVariants() {
        assertTrue("Failed".isFailedHomeStatus())
        assertTrue("Declined".isFailedHomeStatus())
        assertTrue("Timeout".isFailedHomeStatus())
        assertFalse("Completed".isFailedHomeStatus())
    }
}
