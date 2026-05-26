// MyMoola.Infrastructure/Services/MpesaService.cs
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Infrastructure.Settings;
using StackExchange.Redis;

namespace MyMoola.Infrastructure.Services;

public sealed class MpesaService(
    HttpClient http,
    IOptions<MpesaOptions> options,
    IConnectionMultiplexer redis,
    ILogger<MpesaService> logger) : IMpesaService
{
    private readonly MpesaOptions _opts = options.Value;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    public async Task<StkPushResult> InitiateStkPushAsync(
        string phoneNumber,
        int amountKes,
        string accountReference,
        string transactionDesc,
        CancellationToken ct = default)
    {
        var token = await GetAccessTokenAsync(ct);
        var formatted = FormatPhone(phoneNumber);
        var timestamp = DateTimeOffset.UtcNow.ToString("yyyyMMddHHmmss");
        var password = Convert.ToBase64String(
            Encoding.UTF8.GetBytes($"{_opts.ShortCode}{_opts.Passkey}{timestamp}"));

        var payload = new
        {
            BusinessShortCode = _opts.ShortCode,
            Password = password,
            Timestamp = timestamp,
            TransactionType = "CustomerPayBillOnline",
            Amount = amountKes,
            PartyA = formatted,
            PartyB = _opts.ShortCode,
            PhoneNumber = formatted,
            CallBackURL = _opts.StkCallbackUrl,
            AccountReference = accountReference,
            TransactionDesc = transactionDesc
        };

        var response = await SendAsync(
            HttpMethod.Post,
            "mpesa/stkpush/v1/processrequest",
            payload,
            token,
            ct);

        var result = Deserialize<StkPushResponse>(response)
            ?? throw new InvalidOperationException(
                "Safaricom returned null STK Push response.");

        if (result.ResponseCode != "0")
        {
            logger.LogError(
                "STK Push rejected. Code={Code} Description={Desc}",
                result.ResponseCode, result.ResponseDescription);

            throw new InvalidOperationException(
                $"Safaricom STK Push rejected: {result.ResponseDescription}");
        }

        logger.LogInformation(
            "STK Push accepted. CheckoutRequestId={CheckoutRequestId}",
            result.CheckoutRequestId);

        return new StkPushResult(result.CheckoutRequestId, result.MerchantRequestId);
    }

    public async Task<B2CResult> InitiateB2CAsync(
        string phoneNumber,
        int amountKes,
        string remarks,
        CancellationToken ct = default)
    {
        var token = await GetAccessTokenAsync(ct);
        var formatted = FormatPhone(phoneNumber);

        var payload = new
        {
            InitiatorName = _opts.B2CInitiatorName,
            SecurityCredential = _opts.B2CSecurityCredential,
            CommandID = "BusinessPayment",
            Amount = amountKes,
            PartyA = _opts.ShortCode,
            PartyB = formatted,
            Remarks = remarks,
            QueueTimeOutURL = _opts.B2CQueueTimeOutUrl,
            ResultURL = _opts.B2CCallbackUrl,
            Occasion = string.Empty
        };

        var response = await SendAsync(
            HttpMethod.Post,
            "mpesa/b2c/v3/paymentrequest",
            payload,
            token,
            ct);

        var result = Deserialize<B2CResponse>(response)
            ?? throw new InvalidOperationException(
                "Safaricom returned null B2C response.");

        if (result.ResponseCode != "0")
        {
            logger.LogError(
                "B2C rejected. Code={Code} Description={Desc}",
                result.ResponseCode, result.ResponseDescription);

            throw new InvalidOperationException(
                $"Safaricom B2C rejected: {result.ResponseDescription}");
        }

        logger.LogInformation(
            "B2C accepted. ConversationId={ConversationId}",
            result.ConversationId);

        return new B2CResult(result.ConversationId, result.OriginatorConversationId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    // MyMoola.Infrastructure/Services/MpesaService.cs
    // Replace GetAccessTokenAsync private method

    private async Task<string> GetAccessTokenAsync(CancellationToken ct)
    {
        const string cacheKey = "mpesa:access_token";
        var db = redis.GetDatabase();

        // Check Redis first
        var cached = await db.StringGetAsync(cacheKey);
        if (cached.HasValue)
            return cached.ToString();

        // Fetch from Safaricom
        var credentials = Convert.ToBase64String(
            Encoding.UTF8.GetBytes($"{_opts.ConsumerKey}:{_opts.ConsumerSecret}"));

        var request = new HttpRequestMessage(
            HttpMethod.Get,
            "oauth/v1/generate?grant_type=client_credentials");

        request.Headers.Authorization =
            new AuthenticationHeaderValue("Basic", credentials);

        var response = await http.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();

        var body = await response.Content.ReadAsStringAsync(ct);
        var result = Deserialize<MpesaTokenResponse>(body)
            ?? throw new InvalidOperationException(
                "Safaricom returned null token response.");

        // Cache in Redis — expire 300 seconds early
        var ttl = TimeSpan.FromSeconds(result.ExpiresIn - 300);
        await db.StringSetAsync(cacheKey, result.AccessToken, ttl);

        logger.LogInformation("Safaricom OAuth token refreshed and cached in Redis.");

        return result.AccessToken;
    }

    private async Task<string> SendAsync(
        HttpMethod method,
        string endpoint,
        object payload,
        string token,
        CancellationToken ct)
    {
        var request = new HttpRequestMessage(method, endpoint)
        {
            Content = new StringContent(
                JsonSerializer.Serialize(payload, JsonOptions),
                Encoding.UTF8,
                "application/json")
        };

        request.Headers.Authorization =
            new AuthenticationHeaderValue("Bearer", token);

        var response = await http.SendAsync(request, ct);
        var body = await response.Content.ReadAsStringAsync(ct);

        if (!response.IsSuccessStatusCode)
        {
            logger.LogError(
                "Safaricom request failed. Endpoint={Endpoint} Status={Status} Body={Body}",
                endpoint, response.StatusCode, body);

            throw new InvalidOperationException(
                $"Safaricom {endpoint} failed: {response.StatusCode} — {body}");
        }

        return body;
    }

    private static T? Deserialize<T>(string json)
        => JsonSerializer.Deserialize<T>(json, JsonOptions);

    private static string FormatPhone(string phone)
        => phone.StartsWith('+') ? phone[1..] : phone;

    // -------------------------------------------------------------------------
    // Private response DTOs
    // -------------------------------------------------------------------------

    private sealed record MpesaTokenResponse(
        [property: JsonPropertyName("access_token")] string AccessToken,
        [property: JsonPropertyName("expires_in")] int ExpiresIn);

    private sealed record StkPushResponse(
        [property: JsonPropertyName("MerchantRequestID")] string MerchantRequestId,
        [property: JsonPropertyName("CheckoutRequestID")] string CheckoutRequestId,
        [property: JsonPropertyName("ResponseCode")] string ResponseCode,
        [property: JsonPropertyName("ResponseDescription")] string ResponseDescription);

    private sealed record B2CResponse(
        [property: JsonPropertyName("ConversationID")] string ConversationId,
        [property: JsonPropertyName("OriginatorConversationID")] string OriginatorConversationId,
        [property: JsonPropertyName("ResponseCode")] string ResponseCode,
        [property: JsonPropertyName("ResponseDescription")] string ResponseDescription);
}