using FluentValidation;
using MyMoola.Application.Features.Auth.Commands;

namespace MyMoola.Application.Features.Auth.Validators;

public sealed class ResendOtpCommandValidator : AbstractValidator<ResendOtpCommand>
{
    public ResendOtpCommandValidator()
    {
        RuleFor(x => x.PhoneNumber)
            .NotEmpty().WithMessage("Phone number is required.")
            .Matches(@"^\+[1-9]\d{6,14}$")
            .WithMessage("Phone number must be in E.164 format e.g. +254712345678.");

        RuleFor(x => x.Purpose)
            .IsInEnum().WithMessage("Purpose must be either Registration or Login.");
    }
}