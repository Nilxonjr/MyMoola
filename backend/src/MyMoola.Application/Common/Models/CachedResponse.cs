namespace MyMoola.Application.Common.Models;

public sealed class CachedResponse
{
    public int StatusCode { get; init; }
    public string ContentType { get; init; } = "application/json";
    public string Body { get; init; } = string.Empty;
}