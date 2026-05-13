namespace MyMoola.Domain.Exceptions;

public sealed class InsufficientBalanceException : Exception
{
    public InsufficientBalanceException()
        : base("Insufficient balance to complete this operation.") { }

    public InsufficientBalanceException(string message)
        : base(message) { }
}

public sealed class DailyLimitExceededException : Exception
{
    public DailyLimitExceededException()
        : base("This transaction exceeds your daily limit.") { }

    public DailyLimitExceededException(string message)
        : base(message) { }
}

public sealed class AccountFrozenException : Exception
{
    public AccountFrozenException()
        : base("This account is frozen and cannot perform transactions.") { }

    public AccountFrozenException(string reason)
        : base($"Account is frozen: {reason}") { }
}

public sealed class OperationDisabledException : Exception
{
    public OperationDisabledException(string controlKey)
        : base($"Operation '{controlKey}' is currently disabled.") { }

    public OperationDisabledException(string controlKey, string reason)
        : base($"Operation '{controlKey}' is currently disabled. Reason: {reason}") { }
}


public sealed class ConflictException : Exception
{
    public ConflictException(string entity)
        : base($"A conflict occurred on {entity}. Please retry the operation.") { }
}

public sealed class NotFoundException : Exception
{
    public NotFoundException(string entity, object key)
        : base($"{entity} with key '{key}' was not found.") { }
}

public sealed class InvalidOtpException : Exception
{
    public InvalidOtpException()
        : base("OTP is invalid or has expired.") { }
}

public sealed class InvalidCredentialsException : Exception
{
    public InvalidCredentialsException()
        : base("Invalid phone number or PIN.") { }
}

public sealed class PinLockedException : Exception
{
    public DateTimeOffset LockedUntil { get; }

    public PinLockedException(DateTimeOffset lockedUntil)
        : base($"PIN is locked until {lockedUntil:o}. Please try again later.")
    {
        LockedUntil = lockedUntil;
    }
}

public sealed class ConcurrencyException : Exception
{
    public ConcurrencyException()
        : base("The resource was modified by another request. Please retry.") { }
}

public sealed class UnauthorizedException : Exception
{
    public UnauthorizedException()
        : base("Authentication is required.") { }

    public UnauthorizedException(string message)
        : base(message) { }
}
