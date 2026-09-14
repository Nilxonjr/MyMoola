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
    @Volatile
    private var cachedMe: MeResponse? = null
    @Volatile
    private var cachedBalance: BalanceResponse? = null
    @Volatile
    private var cachedTransactions: List<UserTransaction>? = null

    data class ApiResult<out T>(
        val data: T? = null,
        val errorMessage: String? = null,
        val statusCode: Int? = null
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

    data class DepositAddressResponse(
        val chain: String,
        val address: String,
        val network: String,
        val supportedAssets: List<String>
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

    data class QuoteResponse(
        val quoteId: String,
        val currency: String,
        val rateKes: Double,
        val buyRateKes: Double,
        val sellRateKes: Double,
        val spreadPercent: Double,
        val expiresAt: String
    )

    data class BuyCryptoRequest(
        val currency: String,
        val grossKes: Double,
        val quoteId: String,
        val pin: String
    )

    data class BuyCryptoResponse(
        val transactionId: String,
        val referenceCode: String,
        val message: String
    )

    data class SellCryptoRequest(
        val currency: String,
        val cryptoAmount: Double,
        val quoteId: String,
        val pin: String
    )

    data class SellCryptoResponse(
        val transactionId: String,
        val referenceCode: String,
        val message: String
    )

    data class WithdrawalQuoteResponse(
        val quoteId: String,
        val currency: String,
        val feeAmount: Double,
        val expiresInSeconds: Long
    )

    data class WithdrawCryptoRequest(
        val currency: String,
        val amount: Double,
        val toAddress: String,
        val pin: String,
        val quoteId: String
    )

    data class WithdrawCryptoResponse(
        val transactionId: String,
        val referenceCode: String,
        val amount: Double,
        val feeAmount: Double,
        val netAmount: Double,
        val currency: String,
        val toAddress: String,
        val status: String,
        val message: String
    )

    data class PayMerchantRequest(
        val merchantType: String,
        val currency: String,
        val amountKes: Double,
        val quoteId: String,
        val pin: String,
        val paybillNumber: String? = null,
        val accountNumber: String? = null,
        val tillNumber: String? = null,
        val phoneNumber: String? = null
    )

    data class PayMerchantResponse(
        val transactionId: String,
        val referenceCode: String,
        val message: String
    )

    data class RateHistoryPoint(
        val timestamp: String,
        val kesRate: Double
    )

    data class RateHistorySeries(
        val currency: String,
        val points: List<RateHistoryPoint>
    )

    data class RateHistoryResponse(
        val generatedAt: String,
        val range: String,
        val interval: String,
        val series: List<RateHistorySeries>
    )

    data class UserTransaction(
        val id: String,
        val referenceCode: String,
        val type: String,
        val merchantType: String?,
        val receiverName: String?,
        val status: String,
        val initiatorUserId: String?,
        val counterpartyUserId: String?,
        val interactedPhone: String?,
        val currency: String,
        val amount: Double,
        val createdAt: String,
        val marketRateSnapshot: Double?,
        val onChainTxHash: String?,
        val onChainConfirmations: Int,
        val mpesaReference: String?
    )

    fun getCachedMe(): MeResponse? = cachedMe
    fun getCachedBalance(): BalanceResponse? = cachedBalance
    fun getCachedTransactions(): List<UserTransaction>? = cachedTransactions

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
                ).also { cachedMe = it.data }
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
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
                ).also { cachedBalance = it.data }
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
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
                    val rawMessage = extractErrorMessage(body, code)
                    val normalizedMessage = when {
                        code == HttpURLConnection.HTTP_NOT_FOUND ->
                            "No user found with phone number $phoneNumber."

                        rawMessage.contains("user with key", ignoreCase = true) ->
                            rawMessage.replace(
                                "user with key",
                                "user with phone number",
                                ignoreCase = true
                            )

                        else -> rawMessage
                    }
                    ApiResult(errorMessage = normalizedMessage, statusCode = code)
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error while looking up recipient.")
            }
        }

    suspend fun getDepositAddress(chain: String = "Ethereum"): ApiResult<DepositAddressResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedChain = URLEncoder.encode(chain, Charsets.UTF_8.name())
                val path = "/api/users/me/deposit-address?chain=$encodedChain"

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
                    val assetsJson = json.optJSONArray("supportedAssets") ?: JSONArray()
                    val supportedAssets = buildList {
                        for (i in 0 until assetsJson.length()) {
                            add(assetsJson.optString(i))
                        }
                    }
                    ApiResult(
                        data = DepositAddressResponse(
                            chain = json.optString("chain", chain),
                            address = json.optString("address", ""),
                            network = json.optString("network", ""),
                            supportedAssets = supportedAssets
                        )
                    )
                } else {
                    ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
                }
            }.getOrElse {
                ApiResult(errorMessage = "Network error while loading wallet address.")
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
                    ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
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
                        return@runCatching ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
                    }

                    val json = JSONObject(body)
                    totalPages = json.optInt("totalPages", 1).coerceAtLeast(1)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val metadata = item.optString("metadata").ifBlank { null }
                        val metadataJson = metadata
                            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                        val merchantType = metadataJson
                            ?.let { meta ->
                                meta.optString("merchantType").ifBlank {
                                    meta.optString("MerchantType")
                                }
                            }
                            ?.ifBlank { null }
                        val receiverName = metadataJson
                            ?.let { meta ->
                                meta.optString("receiverName").ifBlank {
                                    meta.optString("ReceiverName")
                                }
                            }
                            ?.ifBlank { null }
                        collected.add(
                            UserTransaction(
                                id = item.optString("id"),
                                referenceCode = item.optString("referenceCode"),
                                type = item.optString("type"),
                                merchantType = merchantType,
                                receiverName = receiverName,
                                status = item.optString("status"),
                                initiatorUserId = item.optString("initiatorUserId").ifBlank { null },
                                counterpartyUserId = item.optString("counterpartyUserId").ifBlank { null },
                                interactedPhone = item.optString("interactedPhone").ifBlank { null },
                                currency = item.optString("currency"),
                                amount = item.optDouble("amount", 0.0),
                                createdAt = item.optString("createdAt"),
                                marketRateSnapshot = item.optDouble("marketRateSnapshot").takeUnless { item.isNull("marketRateSnapshot") },
                                onChainTxHash = item.optString("onChainTxHash").ifBlank { null },
                                onChainConfirmations = item.optInt("onChainConfirmations", 0),
                                mpesaReference = item.optString("mpesaReference").ifBlank { null }
                            )
                        )
                    }

                    page += 1
                } while (page <= totalPages)

                ApiResult(data = collected).also { cachedTransactions = it.data }
            }.getOrElse {
                ApiResult(errorMessage = "Network error while loading transactions.")
            }
        }

    suspend fun getRatesHistory(
        currencies: List<String>,
        range: String = "7d",
        interval: String = "day"
    ): ApiResult<RateHistoryResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val currenciesParam = URLEncoder.encode(currencies.joinToString(","), Charsets.UTF_8.name())
            val rangeParam = URLEncoder.encode(range, Charsets.UTF_8.name())
            val intervalParam = URLEncoder.encode(interval, Charsets.UTF_8.name())
            val path = "/api/rates/history?currencies=$currenciesParam&range=$rangeParam&interval=$intervalParam"

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
                val seriesJson = json.optJSONArray("series") ?: JSONArray()
                val series = buildList {
                    for (i in 0 until seriesJson.length()) {
                        val seriesItem = seriesJson.getJSONObject(i)
                        val pointsJson = seriesItem.optJSONArray("points") ?: JSONArray()
                        val points = buildList {
                            for (j in 0 until pointsJson.length()) {
                                val point = pointsJson.getJSONObject(j)
                                add(
                                    RateHistoryPoint(
                                        timestamp = point.optString("timestamp", ""),
                                        kesRate = point.optDouble("kesRate", 0.0)
                                    )
                                )
                            }
                        }
                        add(
                            RateHistorySeries(
                                currency = seriesItem.optString("currency", ""),
                                points = points
                            )
                        )
                    }
                }

                ApiResult(
                    data = RateHistoryResponse(
                        generatedAt = json.optString("generatedAt", ""),
                        range = json.optString("range", range),
                        interval = json.optString("interval", interval),
                        series = series
                    )
                )
            } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                // Some environments may not have history records yet.
                // Treat this as an empty dataset so the UI can render a clean empty state.
                ApiResult(
                    data = RateHistoryResponse(
                        generatedAt = "",
                        range = range,
                        interval = interval,
                        series = emptyList()
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while loading rates.")
        }
    }

    suspend fun getQuote(currency: String): ApiResult<QuoteResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = currency.trim().uppercase()
            val path = "/api/transactions/quote/$normalized"

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
                    data = QuoteResponse(
                        quoteId = json.optString("quoteId", ""),
                        currency = json.optString("currency", normalized),
                        rateKes = json.optDouble("rateKes", 0.0),
                        buyRateKes = json.optDouble("buyRateKes", 0.0),
                        sellRateKes = json.optDouble("sellRateKes", 0.0),
                        spreadPercent = json.optDouble("spreadPercent", 0.0),
                        expiresAt = json.optString("expiresAt", "")
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while loading quote.")
        }
    }

    suspend fun buyCrypto(
        request: BuyCryptoRequest,
        idempotencyKey: String
    ): ApiResult<BuyCryptoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val firstAttempt = executeAuthorizedBuy(request, idempotencyKey)
            val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                    executeAuthorizedBuy(request, idempotencyKey)
                } else {
                    firstAttempt
                }

            val code = finalAttempt.statusCode
            val body = finalAttempt.body
            if (code in 200..299) {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val nested = json?.optJSONObject("data")
                    ?: json?.optJSONObject("result")
                    ?: json?.optJSONObject("payload")

                fun stringFrom(vararg keys: String): String {
                    keys.forEach { key ->
                        val direct = json?.optString(key).orEmpty()
                        if (direct.isNotBlank()) return direct
                        val fromNested = nested?.optString(key).orEmpty()
                        if (fromNested.isNotBlank()) return fromNested
                    }
                    return ""
                }

                val transactionId = stringFrom(
                    "transactionId",
                    "TransactionId",
                    "transactionID",
                    "id"
                ).ifBlank {
                    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                val referenceCode = stringFrom(
                    "referenceCode",
                    "ReferenceCode",
                    "reference",
                    "transactionReference"
                ).ifBlank {
                    Regex("TXN-[A-Za-z0-9-]+")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                val message = stringFrom("message", "Message")
                    .ifBlank { "Payment initiated. Enter your M-Pesa PIN when prompted." }

                ApiResult(
                    data = BuyCryptoResponse(
                        transactionId = transactionId,
                        referenceCode = referenceCode,
                        message = message
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while initiating buy.")
        }
    }

    suspend fun sellCrypto(
        request: SellCryptoRequest,
        idempotencyKey: String
    ): ApiResult<SellCryptoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val firstAttempt = executeAuthorizedSell(request, idempotencyKey)
            val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                    executeAuthorizedSell(request, idempotencyKey)
                } else {
                    firstAttempt
                }

            val code = finalAttempt.statusCode
            val body = finalAttempt.body
            if (code in 200..299) {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val nested = json?.optJSONObject("data")
                    ?: json?.optJSONObject("result")
                    ?: json?.optJSONObject("payload")

                fun stringFrom(vararg keys: String): String {
                    keys.forEach { key ->
                        val direct = json?.optString(key).orEmpty()
                        if (direct.isNotBlank()) return direct
                        val fromNested = nested?.optString(key).orEmpty()
                        if (fromNested.isNotBlank()) return fromNested
                    }
                    return ""
                }

                val transactionId = stringFrom(
                    "transactionId",
                    "TransactionId",
                    "transactionID",
                    "id"
                ).ifBlank {
                    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                val referenceCode = stringFrom(
                    "referenceCode",
                    "ReferenceCode",
                    "reference",
                    "transactionReference"
                ).ifBlank {
                    Regex("SELL-[A-Za-z0-9-]+|TXN-[A-Za-z0-9-]+")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                val message = stringFrom("message", "Message")
                    .ifBlank { "Sell initiated. M-Pesa payment will arrive shortly." }

                ApiResult(
                    data = SellCryptoResponse(
                        transactionId = transactionId,
                        referenceCode = referenceCode,
                        message = message
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while initiating sell.")
        }
    }

    suspend fun getWithdrawalQuote(
        currency: String,
        amount: Double
    ): ApiResult<WithdrawalQuoteResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = currency.trim().uppercase()
            val encodedCurrency = URLEncoder.encode(normalized, Charsets.UTF_8.name())
            val encodedAmount = URLEncoder.encode(amount.toString(), Charsets.UTF_8.name())
            val path = "/api/transactions/withdrawal-quote?currency=$encodedCurrency&amount=$encodedAmount"

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
                    data = WithdrawalQuoteResponse(
                        quoteId = json.optString("quoteId", ""),
                        currency = json.optString("currency", normalized),
                        feeAmount = json.optDouble("feeAmount", 0.0),
                        expiresInSeconds = json.optLong("expiresInSeconds", 30L)
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while loading withdrawal fee.")
        }
    }

    suspend fun withdrawCrypto(
        request: WithdrawCryptoRequest,
        idempotencyKey: String
    ): ApiResult<WithdrawCryptoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val firstAttempt = executeAuthorizedWithdraw(request, idempotencyKey)
            val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                    executeAuthorizedWithdraw(request, idempotencyKey)
                } else {
                    firstAttempt
                }

            val code = finalAttempt.statusCode
            val body = finalAttempt.body
            if (code in 200..299) {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val nested = json?.optJSONObject("data")
                    ?: json?.optJSONObject("result")
                    ?: json?.optJSONObject("payload")

                fun stringFrom(vararg keys: String): String {
                    keys.forEach { key ->
                        val direct = json?.optString(key).orEmpty()
                        if (direct.isNotBlank()) return direct
                        val fromNested = nested?.optString(key).orEmpty()
                        if (fromNested.isNotBlank()) return fromNested
                    }
                    return ""
                }

                fun doubleFrom(vararg keys: String): Double {
                    keys.forEach { key ->
                        if (json != null && json.has(key) && !json.isNull(key)) return json.optDouble(key, 0.0)
                        if (nested != null && nested.has(key) && !nested.isNull(key)) return nested.optDouble(key, 0.0)
                    }
                    return 0.0
                }

                val transactionId = stringFrom(
                    "transactionId",
                    "TransactionId",
                    "transactionID",
                    "id"
                ).ifBlank {
                    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                val referenceCode = stringFrom(
                    "referenceCode",
                    "ReferenceCode",
                    "reference",
                    "transactionReference"
                ).ifBlank {
                    Regex("WDR-[A-Za-z0-9-]+|TXN-[A-Za-z0-9-]+")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                ApiResult(
                    data = WithdrawCryptoResponse(
                        transactionId = transactionId,
                        referenceCode = referenceCode,
                        amount = doubleFrom("amount", "Amount"),
                        feeAmount = doubleFrom("feeAmount", "FeeAmount"),
                        netAmount = doubleFrom("netAmount", "NetAmount"),
                        currency = stringFrom("currency", "Currency").ifBlank { request.currency },
                        toAddress = stringFrom("toAddress", "ToAddress").ifBlank { request.toAddress },
                        status = stringFrom("status", "Status").ifBlank { "Pending" },
                        message = stringFrom("message", "Message")
                            .ifBlank { "Withdrawal queued. Network confirmation may take a few minutes." }
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while initiating withdrawal.")
        }
    }

    suspend fun payMerchant(
        request: PayMerchantRequest,
        idempotencyKey: String
    ): ApiResult<PayMerchantResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val firstAttempt = executeAuthorizedPayMerchant(request, idempotencyKey)
            val finalAttempt = if (firstAttempt.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && AuthApiClient.refreshSession()) {
                    executeAuthorizedPayMerchant(request, idempotencyKey)
                } else {
                    firstAttempt
                }

            val code = finalAttempt.statusCode
            val body = finalAttempt.body
            if (code in 200..299) {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val nested = json?.optJSONObject("data")
                    ?: json?.optJSONObject("result")
                    ?: json?.optJSONObject("payload")

                fun stringFrom(vararg keys: String): String {
                    keys.forEach { key ->
                        val direct = json?.optString(key).orEmpty()
                        if (direct.isNotBlank()) return direct
                        val fromNested = nested?.optString(key).orEmpty()
                        if (fromNested.isNotBlank()) return fromNested
                    }
                    return ""
                }

                val transactionId = stringFrom(
                    "transactionId",
                    "TransactionId",
                    "transactionID",
                    "id"
                ).ifBlank {
                    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                val referenceCode = stringFrom(
                    "referenceCode",
                    "ReferenceCode",
                    "reference",
                    "transactionReference"
                ).ifBlank {
                    Regex("PAY-[A-Za-z0-9-]+|TXN-[A-Za-z0-9-]+")
                        .find(body)
                        ?.value
                        .orEmpty()
                }

                val message = stringFrom("message", "Message")
                    .ifBlank { "Payment initiated. Merchant will receive funds shortly." }

                ApiResult(
                    data = PayMerchantResponse(
                        transactionId = transactionId,
                        referenceCode = referenceCode,
                        message = message
                    )
                )
            } else {
                ApiResult(errorMessage = extractErrorMessage(body, code), statusCode = code)
            }
        }.getOrElse {
            ApiResult(errorMessage = "Network error while initiating merchant payment.")
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

    private fun executeAuthorizedBuy(
        request: BuyCryptoRequest,
        idempotencyKey: String
    ): RawResponse {
        val accessToken = AuthSession.accessToken
        if (accessToken.isNullOrBlank()) {
            return RawResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "")
        }

        val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/transactions/buy")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Idempotency-Key", idempotencyKey)
        }

        val payload = JSONObject().apply {
            put("currency", request.currency)
            put("grossKes", request.grossKes)
            put("quoteId", request.quoteId)
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

    private fun executeAuthorizedSell(
        request: SellCryptoRequest,
        idempotencyKey: String
    ): RawResponse {
        val accessToken = AuthSession.accessToken
        if (accessToken.isNullOrBlank()) {
            return RawResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "")
        }

        val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/transactions/sell")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Idempotency-Key", idempotencyKey)
        }

        val payload = JSONObject().apply {
            put("currency", request.currency)
            put("cryptoAmount", request.cryptoAmount)
            put("quoteId", request.quoteId)
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

    private fun executeAuthorizedPayMerchant(
        request: PayMerchantRequest,
        idempotencyKey: String
    ): RawResponse {
        val accessToken = AuthSession.accessToken
        if (accessToken.isNullOrBlank()) {
            return RawResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "")
        }

        val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/transactions/pay-merchant")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Idempotency-Key", idempotencyKey)
        }

        val payload = JSONObject().apply {
            put("merchantType", request.merchantType)
            put("currency", request.currency)
            put("amountKes", request.amountKes)
            put("quoteId", request.quoteId)
            put("pin", request.pin)
            put("paybillNumber", request.paybillNumber ?: JSONObject.NULL)
            put("accountNumber", request.accountNumber ?: JSONObject.NULL)
            put("tillNumber", request.tillNumber ?: JSONObject.NULL)
            put("phoneNumber", request.phoneNumber ?: JSONObject.NULL)
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val code = connection.responseCode
        val body = readBody(connection, code in 200..299)
        return RawResponse(code, body)
    }

    private fun executeAuthorizedWithdraw(
        request: WithdrawCryptoRequest,
        idempotencyKey: String
    ): RawResponse {
        val accessToken = AuthSession.accessToken
        if (accessToken.isNullOrBlank()) {
            return RawResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "")
        }

        val url = URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/api/transactions/withdraw")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Idempotency-Key", idempotencyKey)
        }

        val payload = JSONObject().apply {
            put("currency", request.currency)
            put("amount", request.amount)
            put("toAddress", request.toAddress)
            put("pin", request.pin)
            put("quoteId", request.quoteId)
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
