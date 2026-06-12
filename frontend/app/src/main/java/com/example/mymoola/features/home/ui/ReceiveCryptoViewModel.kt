package com.example.mymoola.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReceiveCryptoUiState(
    val isLoading: Boolean = true,
    val addressResponse: HomeApiClient.DepositAddressResponse? = null,
    val errorMessage: String? = null,
    val copiedMessage: String? = null
)

class ReceiveCryptoViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiveCryptoUiState())
    val uiState: StateFlow<ReceiveCryptoUiState> = _uiState.asStateFlow()

    init {
        loadAddress()
    }

    fun loadAddress() {
        viewModelScope.launch {
            updateState {
                it.copy(
                    isLoading = true,
                    copiedMessage = null
                )
            }
            val result = HomeApiClient.getDepositAddress()
            if (result.isSuccess) {
                updateState {
                    it.copy(
                        isLoading = false,
                        addressResponse = result.data,
                        errorMessage = null
                    )
                }
            } else {
                updateState {
                    it.copy(
                        isLoading = false,
                        addressResponse = null,
                        errorMessage = result.errorMessage ?: "Unable to load wallet address."
                    )
                }
            }
        }
    }

    fun onAddressCopied() {
        updateState { it.copy(copiedMessage = "Wallet address copied.") }
    }

    private fun updateState(transform: (ReceiveCryptoUiState) -> ReceiveCryptoUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
