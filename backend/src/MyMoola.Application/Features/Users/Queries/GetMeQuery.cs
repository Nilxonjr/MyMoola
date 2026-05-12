using MediatR;

namespace MyMoola.Application.Features.Users.Queries;

public sealed record GetMeQuery : IRequest<GetMeResponse>;


public sealed record GetMeResponse(
    Guid Id,
    string FullName,
    string Phone,
    string? Email,
    bool IsEmailVerified,
    string AccountStatus,
    string KycStatus,
    DateTimeOffset CreatedAt);