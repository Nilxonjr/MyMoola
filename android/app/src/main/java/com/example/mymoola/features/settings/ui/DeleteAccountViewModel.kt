package com.example.mymoola.features.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    val errorMessage: String? = null,
    val isDeleting: Boolean = false,
    val deleteSucceeded: Boolean = false
)

class DeleteAccountViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    fun deleteAccount() {
        if (AuthSession.accessToken.isNullOrBlank()) {
            updateState { it.copy(errorMessage = "Session missing. Please log in again.") }
            return
        }

        viewModelScope.launch {
            updateState {
                it.copy(
                    isDeleting = true,
                    errorMessage = null,
                    deleteSucceeded = false
                )
            }
            val result = AuthApiClient.deleteMyAccount()
            if (result.isSuccess) {
                updateState {
                    it.copy(
                        isDeleting = false,
                        errorMessage = null,
                        deleteSucceeded = true
                    )
                }
            } else {
                updateState {
                    it.copy(
                        isDeleting = false,
                        errorMessage = result.errorMessage ?: "Failed to delete account."
                    )
                }
            }
        }
    }

    fun consumeDeleteSuccess(): Boolean {
        val succeeded = _uiState.value.deleteSucceeded
        if (succeeded) {
            updateState { it.copy(deleteSucceeded = false) }
        }
        return succeeded
    }

    private fun updateState(transform: (DeleteAccountUiState) -> DeleteAccountUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
