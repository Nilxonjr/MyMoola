// MyMoola.Application/Features/Transactions/Commands/BuyCommand.cs
using FluentValidation;
using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record BuyCommand(
    Currency Currency,
    decimal GrossKes,
    Guid QuoteId,
    string Pin) : IRequest<BuyResponse>;

public sealed record BuyResponse(
    Guid TransactionId,
    string ReferenceCode,
    string Message);

public sealed class BuyCommandValidator : AbstractValidator<BuyCommand>
{
    public BuyCommandValidator()
    {
        RuleFor(x => x.Currency)
            .IsInEnum();

        RuleFor(x => x.GrossKes)
            .GreaterThan(500)
            .WithMessage("Amount must be greater than 500.")
            .LessThanOrEqualTo(300_000)
            .WithMessage("Amount exceeds maximum single transaction limit.");

        RuleFor(x => x.QuoteId)
            .NotEmpty()
            .WithMessage("A valid quote ID is required.");

        RuleFor(x => x.Pin)
            .NotEmpty()
            .Length(4, 6);
    }
}