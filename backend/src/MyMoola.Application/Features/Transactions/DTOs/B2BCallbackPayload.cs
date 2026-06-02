// MyMoola.Application/Features/Transactions/DTOs/B2BCallbackPayload.cs
using System.Text.Json;
using System.Text.Json.Serialization;

namespace MyMoola.Application.Features.Transactions.DTOs;

public sealed record B2BCallbackPayload(
    B2BResult Result);

public sealed record B2BResult(
    [property: JsonPropertyName("ConversationID")] string ConversationID,
    [property: JsonPropertyName("OriginatorConversationID")] string OriginatorConversationID,
    [property: JsonPropertyName("ResultCode")] int ResultCode,
    [property: JsonPropertyName("ResultDesc")] string ResultDesc,
    [property: JsonPropertyName("ResultParameters")] B2BResultParameters? ResultParameters);

public sealed record B2BResultParameters(
    [property: JsonPropertyName("ResultParameter")] List<B2BResultParameter> ResultParameter);

public sealed record B2BResultParameter(
    [property: JsonPropertyName("Key")] string Key,
    [property: JsonPropertyName("Value")] JsonElement Value);