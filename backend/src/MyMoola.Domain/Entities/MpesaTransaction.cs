using MyMoola.Domain.Common;

namespace MyMoola.Domain.Entities;

public sealed class MpesaTransaction : BaseEntity
{
    public Guid TransactionId { get; private set; }
    public string? CheckoutRequestId { get; private set; }
    public string? MerchantRequestId { get; private set; }
    public string? MpesaReceiptNumber { get; private set; }
    public string PhoneNumber { get; private set; } = null!;   // Encrypted at application layer
    public decimal AmountKes { get; private set; }
    public string Direction { get; private set; } = null!;     // inbound | outbound
    public string Status { get; private set; } = null!;        // initiated | confirmed | failed | timeout
    public string? RawCallbackPayload { get; private set; }

    private MpesaTransaction() { }

    public static MpesaTransaction Create(
        Guid transactionId,
        string encryptedPhoneNumber,
        decimal amountKes,
        string direction,
        string? checkoutRequestId = null,
        string? merchantRequestId = null)
    {
        return new MpesaTransaction
        {
            TransactionId = transactionId,
            PhoneNumber = encryptedPhoneNumber,
            AmountKes = amountKes,
            Direction = direction,
            Status = "initiated",
            CheckoutRequestId = checkoutRequestId,
            MerchantRequestId = merchantRequestId
        };
    }

    public void Confirm(string receiptNumber, string rawPayload)
    {
        Status = "confirmed";
        MpesaReceiptNumber = receiptNumber;
        RawCallbackPayload = rawPayload;
    }

    public void Fail(string rawPayload)
    {
        Status = "failed";
        RawCallbackPayload = rawPayload;
    }

    public void Timeout()
    {
        Status = "timeout";
    }
}
