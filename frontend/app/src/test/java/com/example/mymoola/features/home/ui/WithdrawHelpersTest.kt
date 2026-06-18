package com.example.mymoola.features.home.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertNull
import org.junit.Test

class WithdrawHelpersTest {
    @Test
    fun clearWithdrawAttempt_removesSavedAttemptKey() {
        val state = SavedStateHandle(mapOf(WithdrawViewModel.ActiveAttemptKey to "attempt-1"))

        clearWithdrawAttempt(state)

        assertNull(state.get<String>(WithdrawViewModel.ActiveAttemptKey))
    }
}
