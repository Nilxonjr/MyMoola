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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID

object HomeApiClient {
    data class ApiResult<out T>(
        val data: T? = null,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean get() = data != null
    }

    data class MeResponse(
        val id: String,
        val fullName: String,
        val phone: String
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

    data class LookupUserResponse(
        val fullName: String,
        val phoneNumber: String
    )

    data class SendToUserRequest(
        val recipientPhone: String,
        val currency: String,
        val amount: Double,
        val pin: String
    )

    data class SendToUserResponse(
        val transactionId: String,
        val referenceCode: String,
        val message: String
    )

    data class UserTransaction(
        val id: String,
        val referenceCode: String,
        val type: String,
        val status: String,
        val initiatorUserId: String?,
        val counterpartyUserId: String?,
        val currency: String,
        val amount: Double,
        val createdAt: String
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
                ApiResult(
                    data = MeResponse(
                        id = json.optString("id", ""),
                        fullName = json.optString("fullName", "User"),
                        phone = json.optString("phone", "")
                    )
                )
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

    suspend fun lookupUserByPhone(phoneNumber: String): ApiResult<LookupUserResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(phoneNumber, Charsets.UTF_8.name())
                val path = "/api/users/lookup?phone=$encoded"

                val firstAttempt = executeAuthorizedGet(path)
                val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                    executeAuthorizedGet(path)
                } else {
                    firstAttempt
                }

                val code = finalAttempt.statusCode
                val body = finalAttempt.body
                if (code == HttpURLConnection.HTTP_OK) {
                    val json = JSONObject(body)
                    ApiResult(
                        data = LookupUserResponse(
                            fullName = json.optString("fullName", ""),
                            phoneNumber = json.optString("phoneNumber", "")
                        )
                    )
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code))
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error while looking up recipient.")
            }
        }

    suspend fun sendToUser(request: SendToUserRequest): ApiResult<SendToUserResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val firstAttempt = executeAuthorizedSend(request)
                val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                    executeAuthorizedSend(request)
                } else {
                    firstAttempt
                }

                val code = finalAttempt.statusCode
                val body = finalAttempt.body
                if (code == HttpURLConnection.HTTP_OK) {
                    val json = JSONObject(body)
                    ApiResult(
                        data = SendToUserResponse(
                            transactionId = json.optString("transactionId", ""),
                            referenceCode = json.optString("referenceCode", ""),
                            message = json.optString("message", "Transfer completed successfully.")
                        )
                    )
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code))
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error while sending funds.")
            }
        }

    suspend fun getAllTransactions(): ApiResult<List<UserTransaction>> = withContext(Dispatchers.IO) {
        runCatching {
            val collected = mutableListOf<UserTransaction>()
            var page = 1
            val pageSize = 50
            var totalPages = 1

            do {
                val path = "/api/users/me/transactions?page=$page&pageSize=$pageSize"
                val firstAttempt = executeAuthorizedGet(path)
                val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                    executeAuthorizedGet(path)
                } else {
                    firstAttempt
                }

                val code = finalAttempt.statusCode
                val body = finalAttempt.body
                if (code != HttpURLConnection.HTTP_OK) {
                    return@runCatching ApiResult(errorMessage = extractErrorMessage(body, code))
                }

                val json = JSONObject(body)
                totalPages = json.optInt("totalPages", 1).coerceAtLeast(1)
                val items = json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    collected.add(
                        UserTransaction(
                            id = item.optString("id"),
                            referenceCode = item.optString("referenceCode"),
                            type = item.optString("type"),
                            status = item.optString("status"),
                            initiatorUserId = item.optString("initiatorUserId").ifBlank { null },
                            counterpartyUserId = item.optString("counterpartyUserId").ifBlank { null },
                            currency = item.optString("currency"),
                            amount = item.optDouble("amount", 0.0),
                            createdAt = item.optString("createdAt")
                        )
                    )
                }

                page += 1
            } while (page <= totalPages)

            ApiResult(data = collected)
        }.getOrElse {
            ApiResult(errorMessage = "Network error while loading transactions.")
        }
    }

    private data class RawResponse(
        val statusCode: Int,
        val body: String
    )

    private fun executeAuthorizedSend(request: SendToUserRequest): RawResponse {
        val accessToken = AuthSession.accessToken
        if (accessToken.isNullOrBlank()) {
            return RawResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "")
        }

        val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/transactions/send")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
        }

        val payload = JSONObject().apply {
            put("recipientPhone", request.recipientPhone)
            put("currency", request.currency)
            put("amount", request.amount)
            put("pin", request.pin)
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val code = connection.responseCode
        val body = readBody(connection, code in 200..299)
        return RawResponse(code, body)
    }

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
