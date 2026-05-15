using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;
using MyMoola.Domain.ValueObjects;

namespace MyMoola.Domain.Entities;

public sealed class User : BaseEntity
{
    public PhoneNumber PhoneNumber { get; private set; } = null!;

    public string PhoneNumberValue { get; private set; } = null!;
    public DateTimeOffset? PhoneVerifiedAt { get; private set; }

    public string? Email { get; private set; }
    public DateTimeOffset? EmailVerifiedAt { get; private set; }

    public string PinHash { get; private set; } = null!;
    public int FailedPinAttempts { get; private set; }
    public DateTimeOffset? PinLockedUntil { get; private set; }
    public AccountStatus AccountStatus { get; private set; } = AccountStatus.Active;
    public string? FreezeReason { get; private set; }
    public string FullName { get; private set; } = null!;
    public string? NationalId { get; private set; }
    public KycStatus KycStatus { get; private set; } = KycStatus.Unverified;
    public DateTimeOffset? KycVerifiedAt { get; private set; }
    public DateTimeOffset? LastLoginAt { get; private set; }
    public uint Version { get; private set; }

    // EF Core
    private User() { }

    public static User Create(
        PhoneNumber phoneNumber,
        string pinHash,
        string fullName)
    {
        return new User
        {
            PhoneNumber = phoneNumber,
            PhoneNumberValue = phoneNumber.Value,
            PinHash = pinHash,
            FullName = fullName
        };
    }

    public void VerifyPhone()
    {
        PhoneVerifiedAt = DateTimeOffset.UtcNow;
    }

    public void RecordSuccessfulLogin()
    {
        FailedPinAttempts = 0;
        PinLockedUntil = null;
        LastLoginAt = DateTimeOffset.UtcNow;
    }

    public void RecordFailedPinAttempt()
    {
        FailedPinAttempts++;
        if (FailedPinAttempts >= 5)
            PinLockedUntil = DateTimeOffset.UtcNow.AddMinutes(30);
    }

    public bool IsPinLocked => PinLockedUntil.HasValue && PinLockedUntil > DateTimeOffset.UtcNow;

    public void Freeze(string reason)
    {
        AccountStatus = AccountStatus.Frozen;
        FreezeReason = reason;
    }

    public void Unfreeze()
    {
        AccountStatus = AccountStatus.Active;
        FreezeReason = null;
    }

    public void EnsureActive()
    {
        if (AccountStatus == AccountStatus.Frozen)
            throw new AccountFrozenException(FreezeReason ?? string.Empty);

        if (AccountStatus == AccountStatus.Suspended)
            throw new OperationDisabledException("Account is suspended.");
    }

 

    public void UpdateKycStatus(KycStatus status)
    {
        KycStatus = status;
        if (status == KycStatus.Verified)
            KycVerifiedAt = DateTimeOffset.UtcNow;
    }
}
