package com.example.mymoola.features.home.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayWithMpesaHelpersTest {
    @Test
    fun merchantPaymentStatusHelpers_coverTerminalVariants() {
        assertTrue("Succeeded".isCompletedMerchantPaymentStatus())
        assertTrue("Declined".isFailedMerchantPaymentStatus())
        assertTrue("Timeout".isFailedMerchantPaymentStatus())
        assertFalse("Processing".isCompletedMerchantPaymentStatus())
        assertFalse("Processing".isFailedMerchantPaymentStatus())
    }

    @Test
    fun clearMerchantPaymentAttempt_removesSavedAttemptKey() {
        val state = SavedStateHandle(mapOf(PayWithMpesaViewModel.ActiveAttemptKey to "attempt-1"))

        clearMerchantPaymentAttempt(state)

        assertNull(state.get<String>(PayWithMpesaViewModel.ActiveAttemptKey))
    }
}
