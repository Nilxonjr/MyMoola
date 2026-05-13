using FluentValidation;
using MyMoola.Application.Features.Auth.Commands;

namespace MyMoola.Application.Features.Auth.Validators;

public sealed class VerifyOtpCommandValidator : AbstractValidator<VerifyOtpCommand>
{
    public VerifyOtpCommandValidator()
    {
        RuleFor(x => x.PhoneNumber)
            .NotEmpty().WithMessage("Phone number is required.")
            .Matches(@"^\+[1-9]\d{6,14}$").WithMessage("Phone number must be in E.164 format e.g. +254712345678.");

        RuleFor(x => x.Otp)
            .NotEmpty().WithMessage("OTP is required.")
            .Length(6).WithMessage("OTP must be exactly 6 digits.")
            .Matches(@"^\d{6}$").WithMessage("OTP must contain digits only.");

        RuleFor(x => x.Purpose)
            .IsInEnum().WithMessage("Purpose must be either Registration or Login.");
    }
}