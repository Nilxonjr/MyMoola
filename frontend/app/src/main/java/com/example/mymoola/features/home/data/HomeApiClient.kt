package com.example.mymoola.features.home.data

import com.example.mymoola.BuildConfig
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object HomeApiClient {
    data class ApiResult<out T>(
        val data: T? = null,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean get() = data != null
    }

    data class MeResponse(
        val fullName: String
    )

    data class WalletBalance(
        val currency: String,
        val total: Double
    )

    data class BalanceResponse(
        val displayCurrency: String,
        val totalFiatEquivalent: Double,
        val wallets: List<WalletBalance>
    )

    suspend fun getMe(): ApiResult<MeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val firstAttempt = executeAuthorizedGet("/api/users/me")
            val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                executeAuthorizedGet("/api/users/me")
            } else {
                firstAttempt
            }

            val code = finalAttempt.statusCode
            val body = finalAttempt.body
            if (code == HttpURLConnection.HTTP_OK) {
                val json = JSONObject(body)
                ApiResult(data = MeResponse(fullName = json.optString("fullName", "User")))
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code))
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while loading profile.")
        }
    }

    suspend fun getBalance(): ApiResult<BalanceResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val firstAttempt = executeAuthorizedGet("/api/users/me/balance?currency=KES")
            val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                executeAuthorizedGet("/api/users/me/balance?currency=KES")
            } else {
                firstAttempt
            }

            val code = finalAttempt.statusCode
            val body = finalAttempt.body
            if (code == HttpURLConnection.HTTP_OK) {
                val json = JSONObject(body)
                val walletsJson = json.optJSONArray("wallets") ?: JSONArray()
                val wallets = buildList {
                    for (i in 0 until walletsJson.length()) {
                        val wallet = walletsJson.getJSONObject(i)
                        add(
                            WalletBalance(
                                currency = wallet.optString("currency", ""),
                                total = wallet.optDouble("total", 0.0)
                            )
                        )
                    }
                }

                ApiResult(
                    data = BalanceResponse(
                        displayCurrency = json.optString("displayCurrency", "KES"),
                        totalFiatEquivalent = json.optDouble("totalFiatEquivalent", 0.0),
                        wallets = wallets
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code))
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while loading balances.")
        }
    }

    private data class RawResponse(
        val statusCode: Int,
        val body: String
    )

    private fun executeAuthorizedGet(path: String): RawResponse {
        val accessToken = AuthSession.accessToken
        if (accessToken.isNullOrBlank()) {
            return RawResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "")
        }

        val connection = openGetConnection(path, accessToken)
        val code = connection.responseCode
        val body = readBody(connection, code in 200..299)
        return RawResponse(code, body)
    }

    private fun openGetConnection(path: String, accessToken: String): HttpURLConnection {
        val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}$path")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
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
        if (body.isBlank()) return "Request failed with status $statusCode."

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
