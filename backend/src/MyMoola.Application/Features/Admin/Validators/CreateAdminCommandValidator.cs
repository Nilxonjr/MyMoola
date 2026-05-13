using FluentValidation;
using MyMoola.Application.Features.Admin.Commands;

namespace MyMoola.Application.Features.Admin.Validators;

public sealed class CreateAdminCommandValidator : AbstractValidator<CreateAdminCommand>
{
    public CreateAdminCommandValidator()
    {
        RuleFor(x => x.Name)
            .NotEmpty()
            .MaximumLength(100)
            .WithMessage("Name is required and must not exceed 100 characters.");

        RuleFor(x => x.Email)
            .NotEmpty()
            .EmailAddress()
            .WithMessage("A valid email address is required.");

        RuleFor(x => x.Role)
            .IsInEnum()
            .WithMessage("Role must be a valid admin role.");
    }
}