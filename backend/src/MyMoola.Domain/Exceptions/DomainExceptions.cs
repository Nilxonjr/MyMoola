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