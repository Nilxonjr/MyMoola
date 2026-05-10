using MyMoola.API.Attributes;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Common.Models;

namespace MyMoola.API.Middleware;

public sealed class IdempotencyMiddleware(RequestDelegate next)
{
    private static readonly TimeSpan CacheTtl = TimeSpan.FromHours(24);
    private static readonly TimeSpan LockTtl = TimeSpan.FromSeconds(30);

    public async Task InvokeAsync(
        HttpContext context,
        IIdempotencyService idempotencyService,
        ICurrentUserService currentUserService)
    {
        // Only apply to endpoints marked with [Idempotency]
        var endpoint = context.GetEndpoint();
        var hasAttribute = endpoint?.Metadata.GetMetadata<IdempotencyAttribute>() is not null;

        if (!hasAttribute)
        {
            await next(context);
            return;
        }

        // Only apply to unsafe methods
        if (!HttpMethods.IsPost(context.Request.Method)
            && !HttpMethods.IsPut(context.Request.Method)
            && !HttpMethods.IsPatch(context.Request.Method))
        {
            await next(context);
            return;
        }

        // Require Idempotency-Key header
        if (!context.Request.Headers.TryGetValue("Idempotency-Key", out var keyValues))
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
            await context.Response.WriteAsJsonAsync(new
            {
                title = "Bad Request",
                status = 400,
                detail = "Idempotency-Key header is required for this endpoint."
            });
            return;
        }

        var idempotencyKey = keyValues.ToString();

        // Require authenticated user — [Idempotency] always implies auth
        var userId = currentUserService.UserId;
        if (userId is null)
        {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            return;
        }

        var cacheKey = $"idempotency:{userId}:{idempotencyKey}";
        var lockKey = $"lock:{userId}:{idempotencyKey}";

        // Check Redis cache — return immediately if found
        var cached = await idempotencyService.GetAsync(cacheKey);
        if (cached is not null)
        {
            context.Response.StatusCode = cached.StatusCode;
            context.Response.ContentType = cached.ContentType;
            await context.Response.WriteAsync(cached.Body);
            return;
        }

        // Acquire distributed lock — prevent concurrent duplicate requests
        var acquired = await idempotencyService.TryAcquireLockAsync(lockKey, LockTtl);
        if (!acquired)
        {
            context.Response.StatusCode = StatusCodes.Status409Conflict;
            await context.Response.WriteAsJsonAsync(new
            {
                title = "Conflict",
                status = 409,
                detail = "A request with this idempotency key is already being processed."
            });
            return;
        }

        // Store key in HttpContext.Items for IIdempotencyContext
        context.Items["IdempotencyKey"] = idempotencyKey;

        // Buffer response body so we can capture it
        var originalBody = context.Response.Body;
        await using var memoryStream = new MemoryStream();
        context.Response.Body = memoryStream;

        try
        {
            await next(context);

            memoryStream.Position = 0;
            var responseBody = await new StreamReader(memoryStream).ReadToEndAsync();

            // Only cache successful responses — never cache failures
            if (context.Response.StatusCode is 200 or 201 or 204)
            {
                var cachedResponse = new CachedResponse
                {
                    StatusCode = context.Response.StatusCode,
                    ContentType = context.Response.ContentType ?? "application/json",
                    Body = responseBody
                };

                await idempotencyService.SetAsync(cacheKey, cachedResponse, CacheTtl);
            }

            // Copy buffered response back to original stream
            memoryStream.Position = 0;
            await memoryStream.CopyToAsync(originalBody);
        }
        finally
        {
            // Always restore original body and release lock
            context.Response.Body = originalBody;
            await idempotencyService.ReleaseLockAsync(lockKey);
        }
    }
}