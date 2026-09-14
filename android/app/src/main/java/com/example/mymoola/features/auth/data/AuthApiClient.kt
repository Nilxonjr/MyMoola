package com.example.mymoola.features.auth.data

import com.example.mymoola.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object AuthApiClient {
    private val refreshSessionMutex = Mutex()

    enum class OtpPurpose {
        Registration,
        Login
    }

    data class RegisterRequest(
        val phoneNumber: String,
        val pin: String,
        val fullName: String
    )

    data class RegisterResponse(
        val userId: String,
        val message: String
    )

    data class VerifyOtpRequest(
        val phoneNumber: String,
        val otp: String,
        val purpose: OtpPurpose
    )

    data class ResendOtpRequest(
        val phoneNumber: String,
        val purpose: OtpPurpose
    )

    data class LoginRequest(
        val phoneNumber: String,
        val pin: String
    )

    data class LoginInitiatedResponse(
        val message: String
    )

    data class ResendOtpResponse(
        val message: String
    )

    data class AuthTokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String
    )

    data class ApiResult<out T>(
        val data: T? = null,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean get() = data != null
    }

    suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/auth/register")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
                }

                val payload = JSONObject().apply {
                    put("phoneNumber", request.phoneNumber)
                    put("pin", request.pin)
                    put("fullName", request.fullName)
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = connection.responseCode
                val body = readBody(connection, code in 200..299)

                if (code == HttpURLConnection.HTTP_CREATED) {
                    val json = JSONObject(body)
                    ApiResult(
                        data = RegisterResponse(
                            userId = json.optString("userId"),
                            message = json.optString("message", "Registration submitted.")
                        )
                    )
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code))
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error. Check API URL/server and try again.")
            }
        }

    suspend fun verifyOtp(request: VerifyOtpRequest): ApiResult<AuthTokenResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/auth/verify-otp")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                val payload = JSONObject().apply {
                    put("phoneNumber", request.phoneNumber)
                    put("otp", request.otp)
                    put("purpose", request.purpose.name)
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = connection.responseCode
                val body = readBody(connection, code in 200..299)

                if (code == HttpURLConnection.HTTP_OK) {
                    val json = JSONObject(body)
                    val tokens = AuthTokenResponse(
                        accessToken = json.optString("accessToken"),
                        refreshToken = json.optString("refreshToken"),
                        tokenType = json.optString("tokenType", "Bearer")
                    )
                    AuthSession.setTokens(tokens.accessToken, tokens.refreshToken)
                    ApiResult(
                        data = AuthTokenResponse(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                            tokenType = tokens.tokenType
                        )
                    )
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code))
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error. Check API URL/server and try again.")
            }
        }

    suspend fun login(request: LoginRequest): ApiResult<LoginInitiatedResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/auth/login")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                val payload = JSONObject().apply {
                    put("phoneNumber", request.phoneNumber)
                    put("pin", request.pin)
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = connection.responseCode
                val body = readBody(connection, code in 200..299)

                if (code == HttpURLConnection.HTTP_OK) {
                    val json = JSONObject(body)
                    ApiResult(
                        data = LoginInitiatedResponse(
                            message = json.optString("message", "OTP sent to your phone number.")
                        )
                    )
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code))
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error. Check API URL/server and try again.")
            }
        }

    suspend fun resendOtp(request: ResendOtpRequest): ApiResult<ResendOtpResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/auth/otp/resend")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                val payload = JSONObject().apply {
                    put("phoneNumber", request.phoneNumber)
                    put("purpose", request.purpose.name)
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = connection.responseCode
                val body = readBody(connection, code in 200..299)

                if (code == HttpURLConnection.HTTP_OK) {
                    val json = JSONObject(body)
                    ApiResult(
                        data = ResendOtpResponse(
                            message = json.optString("message", "OTP resent to your phone number.")
                        )
                    )
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code))
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error. Check API URL/server and try again.")
            }
        }

    suspend fun deleteMyAccount(): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val firstAttempt = executeDeleteMyAccount(AuthSession.accessToken)
                if (firstAttempt.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
                    return@runCatching firstAttempt.toUnitResult()
                }

                val refreshed = refreshSession()
                if (!refreshed) {
                    AuthSession.clear()
                    return@runCatching ApiResult(errorMessage = "Session expired. Please log in again.")
                }

                executeDeleteMyAccount(AuthSession.accessToken).toUnitResult()
            }.getOrElse {
                ApiResult(errorMessage = "Network error. Check API URL/server and try again.")
            }
        }

    suspend fun refreshSession(): Boolean =
        withContext(Dispatchers.IO) {
            refreshSessionMutex.withLock {
                runCatching {
                    val currentRefreshToken = AuthSession.refreshToken
                    if (currentRefreshToken.isNullOrBlank()) return@runCatching false

                    val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/auth/refresh")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        doInput = true
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                    }

                    val payload = JSONObject().apply {
                        put("refreshToken", currentRefreshToken)
                    }

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val code = connection.responseCode
                    val body = readBody(connection, code in 200..299)

                    if (code == HttpURLConnection.HTTP_OK) {
                        val json = JSONObject(body)
                        val newAccessToken = json.optString("accessToken")
                        val newRefreshToken = json.optString("refreshToken")
                        if (newAccessToken.isBlank() || newRefreshToken.isBlank()) {
                            return@runCatching false
                        }

                        AuthSession.setTokens(newAccessToken, newRefreshToken)
                        true
                    } else {
                        false
                    }
                }.getOrElse {
                    false
                }
            }
        }

    private data class RawResponse(
        val statusCode: Int,
        val body: String
    )

    private fun executeDeleteMyAccount(accessToken: String?): RawResponse {
        if (accessToken.isNullOrBlank()) {
            return RawResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "")
        }

        val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/users/me")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val code = connection.responseCode
        val body = readBody(connection, code in 200..299)
        return RawResponse(code, body)
    }

    private fun RawResponse.toUnitResult(): ApiResult<Unit> {
        return if (statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
            ApiResult(data = Unit)
        } else {
            ApiResult(errorMessage = extractErrorMessage(body, statusCode))
        }
    }

    private fun readBody(connection: HttpURLConnection, useInputStream: Boolean): String {
        val stream = if (useInputStream) connection.inputStream else connection.errorStream
            ?: return ""

        return BufferedReader(InputStreamReader(stream)).use { reader ->
            buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    append(line)
                }
            }
        }
    }

    private fun extractErrorMessage(body: String, statusCode: Int): String {
        if (body.isBlank()) {
            return "Request failed with status $statusCode."
        }

        return runCatching {
            val json = JSONObject(body)
            val detail = json.optString("detail")
            val title = json.optString("title")

            if (detail.isNotBlank()) detail
            else if (title.isNotBlank()) title
            else "Request failed with status $statusCode."
        }.getOrElse {
            "Request failed with status $statusCode."
        }
    }
}
