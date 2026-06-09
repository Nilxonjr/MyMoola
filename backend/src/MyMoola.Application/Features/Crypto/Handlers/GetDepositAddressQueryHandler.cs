using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Crypto.Queries;

namespace MyMoola.Application.Features.Crypto.Handlers;

public sealed class GetDepositAddressQueryHandler(
    IDepositAddressRepository depositAddresses,
    IUserRepository users,
    IBlockchainService blockchain,
    ICurrentUserService currentUser,
    IUnitOfWork uow) : IRequestHandler<GetDepositAddressQuery, GetDepositAddressResponse>
{
    // Assets supported per chain — one address serves all of them
    private static readonly Dictionary<Chain, string[]> SupportedAssets = new()
    {
        [Chain.Ethereum] = ["ETH", "USDC", "WBTC"]
    };

    // Network label per chain — driven by config ideally but sufficient here for now
    private static readonly Dictionary<Chain, string> NetworkLabels = new()
    {
        [Chain.Ethereum] = "Sepolia Testnet"
    };

    public async Task<GetDepositAddressResponse> Handle(
        GetDepositAddressQuery request,
        CancellationToken ct)
    {
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var userId = currentUser.UserId.Value;
        // Return existing address if already generated for this chain
        var existing = await depositAddresses
            .FindByUserAndChainAsync(userId, request.Chain, ct);

        if (existing is not null)
            return BuildResponse(existing.Address, request.Chain);

        // Fetch user — needed to get a stable identifier for derivation
        //var user = await users.FindByIdAsync(userId, ct)
        //    ?? throw new NotFoundException(nameof(User), userId);

        // Allocate next derivation index from dedicated DB sequence
        var derivationIndex = await depositAddresses.GetNextDerivationIndexAsync(ct);
        var derivationPath = $"m/44'/60'/0'/0/{derivationIndex}";

        var address = await blockchain.GetEthAddressAsync(derivationIndex, ct);

        var depositAddress = DepositAddress.Create(
            userId: userId,
            chain: request.Chain,
            address: address,
            derivationPath: derivationPath,
            derivationIndex: derivationIndex);

        await depositAddresses.AddAsync(depositAddress, ct);

        // Save to DB first — address is in our records before we tell Alchemy
        await uow.SaveChangesAsync(ct);

        // Register with Alchemy after successful DB save
        // If this fails the address is still in DB — a background job can retry
        await blockchain.RegisterWebhookAddressAsync(address, ct);

        return BuildResponse(address, request.Chain);
    }

    private static GetDepositAddressResponse BuildResponse(string address, Chain chain) =>
        new(
            Chain: chain.ToString(),
            Address: address,
            Network: NetworkLabels[chain],
            SupportedAssets: SupportedAssets[chain]);
}