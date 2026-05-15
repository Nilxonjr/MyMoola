package com.example.mymoola.features.auth.data

import com.example.mymoola.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AuthApiClient {
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

    data class LoginRequest(
        val phoneNumber: String,
        val pin: String
    )

    data class LoginInitiatedResponse(
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
                    ApiResult(
                        data = AuthTokenResponse(
                            accessToken = json.optString("accessToken"),
                            refreshToken = json.optString("refreshToken"),
                            tokenType = json.optString("tokenType", "Bearer")
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

    suspend fun deleteMyAccount(accessToken: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
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

                if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                    ApiResult(data = Unit)
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code))
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error. Check API URL/server and try again.")
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
