using System.Diagnostics;

namespace MyMoola.API.Middleware;

/// <summary>
/// Logs every HTTP request and response with method, path, status code,
/// and elapsed time in milliseconds.
/// </summary>
public sealed class RequestLoggingMiddleware(
    RequestDelegate next,
    ILogger<RequestLoggingMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        var sw = Stopwatch.StartNew();

        logger.LogInformation(
            "→ {Method} {Path} from {IP}",
            context.Request.Method,
            context.Request.Path,
            context.Connection.RemoteIpAddress);

        await next(context);

        sw.Stop();

        var level = context.Response.StatusCode >= 500 ? LogLevel.Error
                  : context.Response.StatusCode >= 400 ? LogLevel.Warning
                  : LogLevel.Information;

        logger.Log(level,
            "← {Method} {Path} {StatusCode} in {ElapsedMs}ms",
            context.Request.Method,
            context.Request.Path,
            context.Response.StatusCode,
            sw.ElapsedMilliseconds);
    }
}