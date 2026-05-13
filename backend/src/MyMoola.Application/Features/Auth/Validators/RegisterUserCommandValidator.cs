using FluentValidation;
using MyMoola.Application.Features.Auth.Commands;
using System.Net.NetworkInformation;

namespace MyMoola.Application.Features.Auth.Validators;

public sealed class RegisterUserCommandValidator : AbstractValidator<RegisterUserCommand>
{
    private static readonly HashSet<string> WeakPins =
    [
        "1234", "0000", "1111", "2222", "3333",
        "4444", "5555", "6666", "7777", "8888",
        "9999", "4321", "1212", "1122", "0101"
    ];

    public RegisterUserCommandValidator()
    {
        RuleFor(x => x.PhoneNumber)
            .NotEmpty().WithMessage("Phone number is required.")
            .Matches(@"^\+[1-9]\d{6,14}$").WithMessage("Phone number must be in E.164 format e.g. +254712345678.");

        RuleFor(x => x.Pin)
        .NotEmpty().WithMessage("PIN is required.")
        .Length(4).WithMessage("PIN must be exactly 4 digits.")
        .Matches(@"^\d{4}$").WithMessage("PIN must contain digits only.")
        .Must(pin => !WeakPins.Contains(pin)).WithMessage("PIN is too easy to guess. Please choose a stronger PIN.");

        RuleFor(x => x.FullName)
            .NotEmpty().WithMessage("Full name is required.")
            .MinimumLength(2).WithMessage("Full name must be at least 2 characters.")
            .MaximumLength(100).WithMessage("Full name must not exceed 100 characters.");
    }
}