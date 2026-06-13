using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;

namespace MyMoola.API.Hubs;

/// <summary>
/// SignalR hub for real-time wallet notifications.
/// Authenticated via JWT passed as access_token query parameter —
/// standard WebSocket workaround since WebSockets cannot send custom headers.
///
/// Kotlin client (Android):
///   implementation 'com.microsoft.signalr:signalr:8.0.0'
///
///   val connection = HubConnectionBuilder
///       .create("https://yourapi.com/hubs/wallet")
///       .withAccessTokenProvider { Callable { yourJwtToken } }
///       .build()
///
///   connection.on("WalletCredited", { payload: WalletCreditedPayload ->
///       // update UI, show toast
///   }, WalletCreditedPayload::class.java)
///
///   connection.start().blockingAwait()
/// </summary>
[Authorize]
public sealed class WalletHub : Hub
{
    /// <summary>
    /// Called when a client connects.
    /// SignalR automatically groups connections by the authenticated user ID
    /// when using IUserIdProvider — no manual group management needed.
    /// </summary>
    public override async Task OnConnectedAsync()
    {
        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        await base.OnDisconnectedAsync(exception);
    }
}