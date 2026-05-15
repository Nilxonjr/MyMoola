namespace MyMoola.Domain.Enums;

public enum AccountStatus
{
    Active,
    Frozen,
    Suspended,
    Deleted
}

public enum KycStatus
{
    Unverified,
    Pending,
    Verified,
    Rejected
}

public enum LedgerEntryType
{
    Credit,
    Debit,
    Lock,
    Unlock
}
