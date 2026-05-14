// MyMoola.Infrastructure/Services/ResendEmailService.cs
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using System.Net.Http.Json;
using System.Text.Json;

namespace MyMoola.Infrastructure.Services;

public sealed class ResendEmailService(
    HttpClient httpClient,
    IConfiguration configuration,
    ILogger<ResendEmailService> logger)
    : IEmailService
{
    public async Task SendAsync(
        string to,
        string subject,
        string body,
        CancellationToken ct = default)
    {
        var fromAddress = configuration["Email:FromAddress"]
            ?? throw new InvalidOperationException("Email:FromAddress is not configured.");
        var fromName = configuration["Email:FromName"] ?? "MyMoola";

        var payload = new
        {
            from = $"{fromName} <{fromAddress}>",
            to = new[] { to },
            subject,
            text = body
        };

        var response = await httpClient.PostAsJsonAsync("emails", payload, ct);
        var responseBody = await response.Content.ReadAsStringAsync(ct);

        if (!response.IsSuccessStatusCode)
        {
            logger.LogError(
                "Resend email failed. Status={Status} Body={Body}",
                (int)response.StatusCode, responseBody);
            response.EnsureSuccessStatusCode();
        }

        logger.LogInformation(
            "Email sent via Resend. To={To} Subject={Subject}",
            to, subject);
    }
}