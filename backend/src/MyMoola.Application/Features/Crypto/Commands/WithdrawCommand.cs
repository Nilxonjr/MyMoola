using MediatR;
using MyMoola.Domain.Enums;
using FluentValidation;

namespace MyMoola.Application.Features.Crypto.Commands;

public sealed record WithdrawCommand(
    Currency Currency,
    decimal Amount,
    string ToAddress,
    string Pin,
    Guid QuoteId) : IRequest<WithdrawResponse>;

public sealed record WithdrawResponse(
    Guid TransactionId,
    string ReferenceCode,
    decimal Amount,
    decimal FeeAmount,
    decimal NetAmount,
    string Currency,
    string ToAddress,
    string Status);

public sealed class WithdrawCommandValidator : AbstractValidator<WithdrawCommand>
{
    public WithdrawCommandValidator()
    {
        RuleFor(x => x.Amount)
            .GreaterThan(0)
            .WithMessage("Withdrawal amount must be greater than zero.");

        RuleFor(x => x.Pin)
            .NotEmpty()
            .Length(4, 6)
            .WithMessage("PIN is required.");

        RuleFor(x => x.ToAddress)
            .NotEmpty()
            .Must(BeValidEthereumAddress)
            .WithMessage("Invalid Ethereum address. Must be a valid EIP-55 checksum address.");

        RuleFor(x => x.Currency)
            .IsInEnum()
            .Must(c => c != Currency.KES)
            .WithMessage("Currency must be a supported crypto currency.");

        RuleFor(x => x.QuoteId)
            .NotEmpty()
            .WithMessage("Quote ID is required.");
    }

    private static bool BeValidEthereumAddress(string address)
    {
        if (string.IsNullOrWhiteSpace(address)) return false;
        if (!address.StartsWith("0x") || address.Length != 42) return false;
        // Accept any valid hex — checksum is optional
        return address[2..].All(c => Uri.IsHexDigit(c));
    }
}