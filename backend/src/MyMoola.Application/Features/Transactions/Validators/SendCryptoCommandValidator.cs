using FluentValidation;
using MyMoola.Application.Features.Transactions.Commands;

namespace MyMoola.Application.Features.Transactions.Validators;

public sealed class SendCryptoCommandValidator : AbstractValidator<SendCryptoCommand>
{
    public SendCryptoCommandValidator()
    {
        RuleFor(x => x.RecipientPhone)
            .NotEmpty()
            .Matches(@"^\+[1-9]\d{1,14}$")
            .WithMessage("Recipient phone must be in E.164 format.");

        RuleFor(x => x.Currency)
            .IsInEnum()
            .WithMessage("Currency must be a valid value.");

        RuleFor(x => x.Amount)
            .GreaterThan(0)
            .WithMessage("Amount must be greater than zero.")
            .Must(amount => decimal.Round(amount, 8) == amount)
            .WithMessage("Amount must not exceed 8 decimal places.");

        RuleFor(x => x.Pin)
            .NotEmpty()
            .Length(4)
            .Matches(@"^\d{4}$")
            .WithMessage("PIN must be exactly 4 digits.");
    }
}