package com.example.mymoola.features.home.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewRecordsHelpersTest {
    @Test
    fun parseRecordEpochMillis_handlesIsoTimestamps() {
        assertNotNull(parseRecordEpochMillis("2026-06-18T10:00:00Z"))
        assertNotNull(parseRecordEpochMillis("2026-06-18T13:00:00+03:00"))
    }

    @Test
    fun failedRecordStatuses_coverTerminalVariants() {
        assertTrue("Failed".isFailedRecordStatus())
        assertTrue("Declined".isFailedRecordStatus())
        assertTrue("Timeout".isFailedRecordStatus())
        assertFalse("Completed".isFailedRecordStatus())
    }
}
