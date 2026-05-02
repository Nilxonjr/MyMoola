using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class Transaction : BaseEntity
{
    public string ReferenceCode { get; private set; } = null!;
    public TransactionType Type { get; private set; }
    public TransactionStatus Status { get; private set; }
    public Guid? InitiatorUserId { get; private set; }
    public Guid? CounterpartyUserId { get; private set; }
    public Currency Currency { get; private set; }
    public decimal Amount { get; private set; }
    public decimal FeeAmount { get; private set; }
    public decimal? KesAmount { get; private set; }
    public decimal? ExchangeRateSnapshot { get; private set; }
    public decimal? MarketRateSnapshot { get; private set; }
    public string? MpesaReference { get; private set; }
    public string? OnChainTxHash { get; private set; }
    public int OnChainConfirmations { get; private set; }
    public string IdempotencyKey { get; private set; } = null!;
    public string? Metadata { get; private set; }
    public string? AdminNote { get; private set; }
    public DateTimeOffset? CompletedAt { get; private set; }

    // EF Core
    private Transaction() { }

    public static Transaction Create(
        string referenceCode,
        TransactionType type,
        Currency currency,
        decimal amount,
        string idempotencyKey,
        Guid? initiatorUserId = null,
        Guid? counterpartyUserId = null,
        decimal feeAmount = 0)
    {
        return new Transaction
        {
            ReferenceCode = referenceCode,
            Type = type,
            Status = TransactionStatus.Pending,
            Currency = currency,
            Amount = amount,
            FeeAmount = feeAmount,
            IdempotencyKey = idempotencyKey,
            InitiatorUserId = initiatorUserId,
            CounterpartyUserId = counterpartyUserId
        };
    }

    public void SetExchangeRates(decimal exchangeRate, decimal marketRate, decimal kesAmount)
    {
        ExchangeRateSnapshot = exchangeRate;
        MarketRateSnapshot = marketRate;
        KesAmount = kesAmount;
    }

    public void MarkProcessing()
    {
        Status = TransactionStatus.Processing;
    }

    public void MarkCompleted()
    {
        Status = TransactionStatus.Completed;
        CompletedAt = DateTimeOffset.UtcNow;
    }

    public void MarkFailed()
    {
        Status = TransactionStatus.Failed;
    }

    public void MarkReversed()
    {
        Status = TransactionStatus.Reversed;
    }

    public void MarkExpired()
    {
        Status = TransactionStatus.Expired;
    }

    public void SetMpesaReference(string reference)
    {
        MpesaReference = reference;
    }

    public void SetOnChainTxHash(string txHash)
    {
        OnChainTxHash = txHash;
    }

    public void IncrementConfirmations(int confirmations)
    {
        OnChainConfirmations = confirmations;
    }

    public void SetAdminNote(string note)
    {
        AdminNote = note;
    }

    public void SetMetadata(string metadata)
    {
        Metadata = metadata;
    }
}
