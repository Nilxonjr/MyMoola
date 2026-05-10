namespace MyMoola.Application.Common.Interfaces;

public interface IIdempotencyContext
{
    string? IdempotencyKey { get; }
}