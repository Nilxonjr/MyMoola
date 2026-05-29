// MyMoola.Application/Features/Transactions/Commands/SellCommand.cs
using FluentValidation;
using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record SellCommand(
    Currency Currency,
    decimal CryptoAmount,
    Guid QuoteId,
    string Pin) : IRequest<SellResponse>;

public sealed record SellResponse(
    Guid TransactionId,
    string ReferenceCode,
    string Message);

public sealed class SellCommandValidator : AbstractValidator<SellCommand>
{
    public SellCommandValidator()
    {
        RuleFor(x => x.Currency)
            .IsInEnum();

        RuleFor(x => x.CryptoAmount)
            .GreaterThan(0)
            .WithMessage("Amount must be greater than zero.");

        RuleFor(x => x.QuoteId)
            .NotEmpty()
            .WithMessage("A valid quote ID is required.");

        RuleFor(x => x.Pin)
            .NotEmpty()
            .Length(4, 6);
    }
}

// MyMoola.Application/Features/Transactions/Commands/SellCommand.cs
// Add at bottom of file

public sealed record B2CCallbackOutboxPayload(
    string ConversationID,
    int ResultCode,
    string ResultDesc,
    string? MpesaReceiptNumber,
    string RawCallbackJson,
    Guid UserWalletId,
    Guid TreasuryWalletId,
    Guid TreasuryKesWalletId,
    Guid RevenueWalletId,
    Guid SpreadKesWalletId,
    Guid SettlementWalletId,
    Guid SuspenseWalletId,
    decimal CryptoAmount,
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal SpreadKes,
    int B2CAmountKes,
    decimal ResidualKes);

public sealed record B2CTimeoutOutboxPayload(
    string ConversationID,
    string RawCallbackJson,
    Guid UserWalletId,
    decimal CryptoAmount);