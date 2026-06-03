// MyMoola.Application/Features/Transactions/Commands/PayMerchantCommand.cs
using FluentValidation;
using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record PayMerchantCommand(
    MerchantPaymentType MerchantType,
    Currency Currency,
    decimal AmountKes,
    Guid QuoteId,
    string Pin,
    // Paybill only
    string? PaybillNumber,
    string? AccountNumber,
    // Till only
    string? TillNumber,
    // Pochi + SendMoney
    string? PhoneNumber) : IRequest<PayMerchantResponse>;

public sealed record PayMerchantResponse(
    Guid TransactionId,
    string ReferenceCode,
    string Message);

public sealed class PayMerchantCommandValidator : AbstractValidator<PayMerchantCommand>
{
    public PayMerchantCommandValidator()
    {
        RuleFor(x => x.MerchantType)
            .IsInEnum();

        RuleFor(x => x.Currency)
            .IsInEnum();

        RuleFor(x => x.AmountKes)
            .GreaterThan(0)
            .WithMessage("Amount must be greater than zero.")
            .LessThanOrEqualTo(300_000)
            .WithMessage("Amount exceeds maximum single transaction limit.");

        RuleFor(x => x.QuoteId)
            .NotEmpty()
            .WithMessage("A valid quote ID is required.");

        RuleFor(x => x.Pin)
            .NotEmpty()
            .Length(4, 6);

        // Paybill requires paybill number and account number
        When(x => x.MerchantType == MerchantPaymentType.Paybill, () =>
        {
            RuleFor(x => x.PaybillNumber)
                .NotEmpty()
                .WithMessage("Paybill number is required.")
                .Matches(@"^\d{5,7}$")
                .WithMessage("Invalid Paybill number format.");

            RuleFor(x => x.AccountNumber)
                .NotEmpty()
                .WithMessage("Account number is required for Paybill payments.")
                .MaximumLength(20)
                .WithMessage("Account number too long.");
        });

        // Till requires till number only
        When(x => x.MerchantType == MerchantPaymentType.Till, () =>
        {
            RuleFor(x => x.TillNumber)
                .NotEmpty()
                .WithMessage("Till number is required.")
                .Matches(@"^\d{5,7}$")
                .WithMessage("Invalid Till number format.");
        });

        // Pochi and SendMoney require phone number
        When(x => x.MerchantType == MerchantPaymentType.Pochi ||
                  x.MerchantType == MerchantPaymentType.SendMoney, () =>
                  {
                      RuleFor(x => x.PhoneNumber)
                          .NotEmpty()
                          .WithMessage("Phone number is required.")
                          .Matches(@"^2547\d{8}$")
                          .WithMessage("Phone number must be in format 2547XXXXXXXX.");
                  });
    }
}