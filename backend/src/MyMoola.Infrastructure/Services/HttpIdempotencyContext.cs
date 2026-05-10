using Microsoft.AspNetCore.Http;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class HttpIdempotencyContext(
    IHttpContextAccessor httpContextAccessor) : IIdempotencyContext
{
    public string? IdempotencyKey =>
        httpContextAccessor.HttpContext?
            .Items["IdempotencyKey"]?
            .ToString();
}