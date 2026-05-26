// MyMoola.Application/Features/Transactions/Models/StkCallbackPayload.cs
using System.Text.Json;

namespace MyMoola.Application.Features.Transactions.DTOs;

public sealed record StkCallbackPayload(
    StkCallbackBody Body);

public sealed record StkCallbackBody(
    StkCallbackData StkCallback);

public sealed record StkCallbackData(
    string MerchantRequestID,
    string CheckoutRequestID,
    int ResultCode,
    string ResultDesc,
    StkCallbackMetadata? CallbackMetadata);

public sealed record StkCallbackMetadata(
    List<StkCallbackMetadataItem> Item);

public sealed record StkCallbackMetadataItem(
    string Name,
    JsonElement? Value);