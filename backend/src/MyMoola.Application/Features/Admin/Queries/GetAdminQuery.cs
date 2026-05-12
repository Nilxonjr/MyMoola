using MediatR;

namespace MyMoola.Application.Features.Admin.Queries;

public sealed record GetAdminQuery(Guid AdminId) : IRequest<AdminDto>;