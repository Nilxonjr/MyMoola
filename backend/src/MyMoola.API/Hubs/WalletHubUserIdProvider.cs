using Microsoft.AspNetCore.SignalR;
using MyMoola.Application.Common.Constants;

namespace MyMoola.API.Hubs;

/// <summary>
/// Tells SignalR which claim to use as the user identifier.
/// SignalR uses this to route Clients.User(userId) calls
/// to the correct connection — even if the user has multiple
/// connections (multiple devices).
/// </summary>
/// 
public sealed class WalletHubUserIdProvider : IUserIdProvider
{
    public string? GetUserId(HubConnectionContext connection)
        => connection.User.FindFirst(ClaimNames.UserId)?.Value;
}