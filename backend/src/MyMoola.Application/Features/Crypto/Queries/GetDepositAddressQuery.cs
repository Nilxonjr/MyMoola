using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Crypto.Queries;

public sealed record GetDepositAddressQuery(
    Chain Chain) : IRequest<GetDepositAddressResponse>;

public sealed record GetDepositAddressResponse(
    string Chain,
    string Address,
    string Network,
    string[] SupportedAssets);