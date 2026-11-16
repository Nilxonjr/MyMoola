package com.example.mymoola.features.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.auth.data.AuthApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OtpUiState(
    val otp: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val secondsRemaining: Int = 30,
    val verifiedToken: AuthApiClient.AuthTokenResponse? = null
)

class OtpViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OtpUiState())
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        startCountdown()
    }

    fun onOtpChanged(value: String) {
        updateState {
            it.copy(
                otp = value.filter(Char::isDigit).take(6),
                errorMessage = null,
                successMessage = null,
                verifiedToken = null
            )
        }
    }

    fun resendOtp(phoneNumber: String, purpose: AuthApiClient.OtpPurpose) {
        if (_uiState.value.secondsRemaining > 0 || _uiState.value.isResending || _uiState.value.isVerifying) return

        viewModelScope.launch {
            updateState {
                it.copy(
                    isResending = true,
                    errorMessage = null,
                    successMessage = null,
                    verifiedToken = null
                )
            }

            val result = AuthApiClient.resendOtp(
                AuthApiClient.ResendOtpRequest(
                    phoneNumber = phoneNumber,
                    purpose = purpose
                )
            )

            if (result.isSuccess) {
                updateState {
                    it.copy(
                        isResending = false,
                        successMessage = result.data?.message ?: "OTP resent to your phone number.",
                        secondsRemaining = 30
                    )
                }
                startCountdown()
            } else {
                updateState {
                    it.copy(
                        isResending = false,
                        errorMessage = result.errorMessage ?: "Unable to resend OTP right now."
                    )
                }
            }
        }
    }

    fun verifyOtp(phoneNumber: String, purpose: AuthApiClient.OtpPurpose) {
        val currentOtp = _uiState.value.otp
        if (currentOtp.length != 6) {
            updateState {
                it.copy(
                    errorMessage = "OTP must be exactly 6 digits.",
                    successMessage = null,
                    verifiedToken = null
                )
            }
            return
        }

        viewModelScope.launch {
            updateState {
                it.copy(
                    isVerifying = true,
                    errorMessage = null,
                    successMessage = null,
                    verifiedToken = null
                )
            }

            val result = AuthApiClient.verifyOtp(
                AuthApiClient.VerifyOtpRequest(
                    phoneNumber = phoneNumber,
                    otp = currentOtp,
                    purpose = purpose
                )
            )

            if (result.isSuccess) {
                updateState {
                    it.copy(
                        isVerifying = false,
                        successMessage = "Phone verified successfully.",
                        verifiedToken = result.data
                    )
                }
            } else {
                updateState {
                    it.copy(
                        isVerifying = false,
                        errorMessage = result.errorMessage ?: "OTP verification failed."
                    )
                }
            }
        }
    }

    fun consumeVerifiedToken(): AuthApiClient.AuthTokenResponse? {
        val token = _uiState.value.verifiedToken
        if (token != null) {
            updateState { it.copy(verifiedToken = null) }
        }
        return token
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_uiState.value.secondsRemaining > 0) {
                delay(1000)
                updateState { state ->
                    state.copy(secondsRemaining = (state.secondsRemaining - 1).coerceAtLeast(0))
                }
            }
        }
    }

    private fun updateState(transform: (OtpUiState) -> OtpUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
