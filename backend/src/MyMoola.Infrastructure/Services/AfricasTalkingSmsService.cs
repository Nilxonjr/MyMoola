using System.Text.Json;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class AfricasTalkingSmsService(
    HttpClient httpClient,
    IConfiguration configuration,
    ILogger<AfricasTalkingSmsService> logger) : ISmsService
{
    public async Task SendAsync(string to, string message, CancellationToken ct = default)
    {
        var username = configuration["AfricasTalking:Username"]!;
        var senderId = configuration["AfricasTalking:SenderId"];

        var fields = new Dictionary<string, string>
        {
            ["username"] = username,
            ["to"] = to,
            ["message"] = message,
        };

        if (!string.IsNullOrWhiteSpace(senderId))
            fields["from"] = senderId;

        using var content = new FormUrlEncodedContent(fields);

        var response = await httpClient.PostAsync("version1/messaging", content, ct);
        var body = await response.Content.ReadAsStringAsync(ct);

        if (!response.IsSuccessStatusCode)
        {
            logger.LogError(
                "Africa's Talking SMS failed. Status: {Status}. Body: {Body}",
                (int)response.StatusCode, body);

            response.EnsureSuccessStatusCode();
        }

        logger.LogInformation(
            "SMS sent to {Recipient}. Response: {Response}",
            to, body);
    }
}