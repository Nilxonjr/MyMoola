using System.Net;
using System.Text.Json;
using FluentValidation;
using Microsoft.EntityFrameworkCore;
using MyMoola.Domain.Exceptions;

namespace MyMoola.API.Middleware;

/// <summary>
/// Catches all unhandled exceptions and returns consistent RFC 7807
/// problem detail JSON responses. Must be the first middleware in the pipeline.
/// </summary>
public sealed class ExceptionHandlingMiddleware(
    RequestDelegate next,
    ILogger<ExceptionHandlingMiddleware> logger)
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (Exception ex)
        {
            await HandleAsync(context, ex);
        }
    }

    private async Task HandleAsync(HttpContext context, Exception exception)
    {

        var (status, title, errors) = Map(exception);

        if (status >= 500)
            logger.LogError(exception,
                "Unhandled exception on {Method} {Path}. Inner: {Inner}",
                context.Request.Method,
                context.Request.Path,
                exception.InnerException?.Message ?? "none");
        else
            logger.LogWarning(exception,
                "Domain error {Status} on {Method} {Path}",
                status,
                context.Request.Method,
                context.Request.Path);

        context.Response.ContentType = "application/problem+json";
        context.Response.StatusCode = status;

        var body = new
        {
            title,
            status,
            //detail = exception.Message,
            detail = exception.InnerException?.Message ?? exception.Message, // for testing
            instance = context.Request.Path.Value,
            traceId = context.TraceIdentifier,
            errors,
        };

        await context.Response.WriteAsync(
            JsonSerializer.Serialize(body, JsonOptions));
    }

    private static (int Status, string Title, object? Errors) Map(Exception ex) => ex switch
    {
        ValidationException ve => (
            StatusCodes.Status400BadRequest,
            "Validation Failed",
            ve.Errors
              .GroupBy(e => e.PropertyName, StringComparer.OrdinalIgnoreCase)
              .ToDictionary(
                  g => g.Key,
                  g => g.Select(e => e.ErrorMessage).ToArray())),

        InvalidOtpException => (StatusCodes.Status400BadRequest, "Invalid OTP", null),
        InvalidCredentialsException => (StatusCodes.Status401Unauthorized, "Unauthorized", null),
        UnauthorizedException => (StatusCodes.Status401Unauthorized, "Unauthorized", null),
        PinLockedException => (StatusCodes.Status403Forbidden, "Account Locked", null),
        AccountFrozenException => (StatusCodes.Status403Forbidden, "Account Frozen", null),
        OperationDisabledException => (StatusCodes.Status403Forbidden, "Operation Disabled", null),
        NotFoundException => (StatusCodes.Status404NotFound, "Not Found", null),
        ConflictException => (StatusCodes.Status409Conflict, "Conflict", null),
        ConcurrencyException => (StatusCodes.Status409Conflict, "Concurrency Conflict", null),
        DbUpdateConcurrencyException => (StatusCodes.Status409Conflict, "Concurrency Conflict", null),
        InsufficientBalanceException => (StatusCodes.Status422UnprocessableEntity, "Insufficient Balance", null),
        DailyLimitExceededException => (StatusCodes.Status422UnprocessableEntity, "Daily Limit Exceeded", null),
        InvalidOperationException => (StatusCodes.Status422UnprocessableEntity, "Invalid Operation", null),

        _ => (StatusCodes.Status500InternalServerError, "Internal Server Error", null),
    };
}