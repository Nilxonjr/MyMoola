// MyMoola.Application/Common/Interfaces/IMpesaService.cs
namespace MyMoola.Application.Common.Interfaces;

public sealed record StkPushResult(
    string CheckoutRequestId,
    string MerchantRequestId);

public sealed record B2CResult(
    string ConversationId,
    string OriginatorConversationId);

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
}