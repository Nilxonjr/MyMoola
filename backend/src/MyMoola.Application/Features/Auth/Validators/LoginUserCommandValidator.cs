using FluentValidation;
using MyMoola.Application.Features.Auth.Commands;

namespace MyMoola.Application.Features.Auth.Validators;

public sealed class LoginUserCommandValidator : AbstractValidator<LoginUserCommand>
{
    public LoginUserCommandValidator()
    {
        RuleFor(x => x.PhoneNumber)
            .NotEmpty().WithMessage("Phone number is required.")
            .Matches(@"^\+[1-9]\d{6,14}$").WithMessage("Phone number must be in E.164 format e.g. +254712345678.");

        RuleFor(x => x.Pin)
            .NotEmpty().WithMessage("PIN is required.")
            .Length(4).WithMessage("PIN must be exactly 4 digits.")
            .Matches(@"^\d{4}$").WithMessage("PIN must contain digits only.");
    }
}