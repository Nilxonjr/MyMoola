using FluentValidation;
using MediatR;

namespace MyMoola.Application.Common.Behaviours;

/// <summary>
/// MediatR pipeline behaviour that runs all FluentValidation validators
/// for a command before the handler executes.
/// Throws <see cref="ValidationException"/> on failure — caught by ExceptionHandlingMiddleware.
/// </summary>
public sealed class ValidationBehaviour<TRequest, TResponse>(
    IEnumerable<IValidator<TRequest>> validators)
    : IPipelineBehavior<TRequest, TResponse>
    where TRequest : notnull
{
    public async Task<TResponse> Handle(
        TRequest request,
        RequestHandlerDelegate<TResponse> next,
        CancellationToken ct)
    {
        if (!validators.Any())
            return await next();

        var context = new ValidationContext<TRequest>(request);

        var failures = validators
            .Select(v => v.Validate(context))
            .SelectMany(r => r.Errors)
            .Where(f => f is not null)
            .ToList();

        if (failures.Count != 0)
            throw new ValidationException(failures);

        return await next();
    }
}