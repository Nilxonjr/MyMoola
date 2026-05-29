using MediatR;

namespace MyMoola.Application.Features.Rates.Queries;

public sealed record GetRateHistoryQuery(
    string Currencies,
    string Range,
    string Interval) : IRequest<GetRateHistoryResponse>;

public sealed record GetRateHistoryResponse(
    DateTimeOffset GeneratedAt,
    string Range,
    string Interval,
    IReadOnlyList<RateSeriesDto> Series);

public sealed record RateSeriesDto(
    string Currency,
    IReadOnlyList<RatePointDto> Points);

public sealed record RatePointDto(
    DateTimeOffset Timestamp,
    decimal KesRate);