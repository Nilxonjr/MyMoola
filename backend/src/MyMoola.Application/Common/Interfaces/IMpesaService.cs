// MyMoola.Application/Common/Interfaces/IMpesaService.cs
namespace MyMoola.Application.Common.Interfaces;

public sealed record StkPushResult(
    string CheckoutRequestId,
    string MerchantRequestId);

public sealed record B2CResult(
    string ConversationId,
    string OriginatorConversationId);

public sealed record B2BResult(
    string ConversationId,
    string OriginatorConversationId);

public sealed record B2CStatusResult(
    string ConversationId,
    int ResultCode,
    string ResultDesc);

public interface IMpesaService
{
    /// <summary>
    /// Initiates an STK Push to collect KES from a user's M-Pesa.
    /// Amount must be a whole integer — Safaricom rejects decimals.
    /// </summary>
    Task<StkPushResult> InitiateStkPushAsync(
        string phoneNumber,
        int amountKes,
        string accountReference,
        string transactionDesc,
        CancellationToken ct = default);

    /// <summary>
    /// Sends KES to a user's M-Pesa via B2C.
    /// Amount must be a whole integer — Safaricom rejects decimals.
    /// </summary>
    Task<B2CResult> InitiateB2CAsync(
        string phoneNumber,
        int amountKes,
        string remarks,
        CancellationToken ct = default);

    /// <summary>
    /// Pays a merchant via B2B BusinessPayBill.
    /// Amount is exact KES merchant receives.
    /// </summary>
    Task<B2BResult> InitiateB2BPaybillAsync(
        string paybillNumber,
        string accountNumber,
        int amountKes,
        string remarks,
        CancellationToken ct = default);

    /// <summary>
    /// Pays a Till merchant via B2B BusinessBuyGoods.
    /// Amount is exact KES merchant receives.
    /// No account number — Till identifies the merchant uniquely.
    /// </summary>
    Task<B2BResult> InitiateB2BTillAsync(
        string tillNumber,
        int amountKes,
        string remarks,
        CancellationToken ct = default);

    /// <summary>
    /// Pays a Pochi la Biashara merchant.
    /// Uses dedicated Pochi endpoint — different from B2C.
    /// </summary>
    Task<B2BResult> InitiatePochiAsync(
        string phoneNumber,
        int amountKes,
        string remarks,
        CancellationToken ct = default);

    /// <summary>
    /// Sends money to any registered M-Pesa number.
    /// Uses standard B2C endpoint.
    /// </summary>
    Task<B2CResult> InitiateSendMoneyAsync(
        string phoneNumber,
        int amountKes,
        string remarks,
        CancellationToken ct = default);

    Task<B2CStatusResult> QueryB2CStatusAsync(
    string conversationId,
    CancellationToken ct = default);
}