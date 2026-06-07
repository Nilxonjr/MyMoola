using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Options;
using MyMoola.Infrastructure.Settings;

namespace MyMoola.API.Middleware;

public sealed class AlchemyWebhookMiddleware(
    RequestDelegate next,
    IOptions<AlchemyOptions> alchemyOptions)
{
    private readonly AlchemyOptions _alchemy = alchemyOptions.Value;

    public async Task InvokeAsync(HttpContext context)
    {
        if (!context.Request.Path.StartsWithSegments("/api/webhooks/alchemy"))
        {
            await next(context);
            return;
        }

        // Buffer the body so we can read it for signature validation
        // and still have it available for the controller
        context.Request.EnableBuffering();

        var body = await ReadBodyAsync(context.Request);

        if (!IsValidSignature(body, context.Request.Headers["X-Alchemy-Signature"]))
        {
            context.Response.StatusCode = StatusCodes.Status403Forbidden;
            await context.Response.WriteAsync("Invalid webhook signature.");
            return;
        }

        // Rewind so the controller can read the body again
        context.Request.Body.Position = 0;

        await next(context);
    }

    private bool IsValidSignature(string body, string? signature)
    {
        if (string.IsNullOrEmpty(signature)) return false;

        var keyBytes = Encoding.UTF8.GetBytes(_alchemy.WebhookSigningKey);
        var bodyBytes = Encoding.UTF8.GetBytes(body);

        var hash = HMACSHA256.HashData(keyBytes, bodyBytes);
        var expected = Convert.ToHexString(hash).ToLowerInvariant();

        return string.Equals(expected, signature.ToLowerInvariant(), StringComparison.Ordinal);
    }

    private static async Task<string> ReadBodyAsync(HttpRequest request)
    {
        using var reader = new StreamReader(
            request.Body,
            encoding: Encoding.UTF8,
            detectEncodingFromByteOrderMarks: false,
            leaveOpen: true);

        return await reader.ReadToEndAsync();
    }
}