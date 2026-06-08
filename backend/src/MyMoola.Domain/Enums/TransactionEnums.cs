namespace MyMoola.Domain.Enums;

public enum TransactionType
{
    Deposit,
    Withdrawal,
    Send,
    Receive,
    Buy,
    Sell,
    Fee,
    TreasuryIn,
    TreasuryOut,
    Reversal,
    AdminAdjustment,
    MerchantPayment
}

public enum TransactionStatus
{
    Pending,
    Processing,
    Completed,
    Failed,
    Reversed,
    Expired,
    Deposit,
    Withdrawal,
    Sweep,
    Confirmed
}
