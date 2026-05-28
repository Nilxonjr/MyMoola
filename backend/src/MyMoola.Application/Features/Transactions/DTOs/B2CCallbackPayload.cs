// MyMoola.Application/Features/Transactions/DTOs/B2CCallbackPayload.cs
using System.Text.Json;
using System.Text.Json.Serialization;

namespace MyMoola.Application.Features.Transactions.DTOs;

public sealed record B2CCallbackPayload(
    B2CResult Result);

public sealed record B2CResult(
    [property: JsonPropertyName("ConversationID")] string ConversationID,
    [property: JsonPropertyName("OriginatorConversationID")] string OriginatorConversationID,
    [property: JsonPropertyName("ResultCode")] int ResultCode,
    [property: JsonPropertyName("ResultDesc")] string ResultDesc,
    [property: JsonPropertyName("ResultParameters")] B2CResultParameters? ResultParameters);

public sealed record B2CResultParameters(
    [property: JsonPropertyName("ResultParameter")] List<B2CResultParameter> ResultParameter);

public sealed record B2CResultParameter(
    [property: JsonPropertyName("Key")] string Key,
    [property: JsonPropertyName("Value")] JsonElement Value);