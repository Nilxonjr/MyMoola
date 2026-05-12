using System.Net;
using System.Net.Mail;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class SmtpEmailService(
    IConfiguration configuration,
    ILogger<SmtpEmailService> logger) : IEmailService
{
    public async Task SendAsync(
        string to,
        string subject,
        string body,
        CancellationToken ct = default)
    {
        var host = configuration["Email:Host"]
            ?? throw new InvalidOperationException("Email:Host is not configured.");
        var port = int.Parse(configuration["Email:Port"] ?? "587");
        var username = configuration["Email:Username"]
            ?? throw new InvalidOperationException("Email:Username is not configured.");
        var password = configuration["Email:Password"]
            ?? throw new InvalidOperationException("Email:Password is not configured.");
        var fromAddress = configuration["Email:FromAddress"]
            ?? throw new InvalidOperationException("Email:FromAddress is not configured.");
        var fromName = configuration["Email:FromName"] ?? "MyMoola";

        using var client = new SmtpClient(host, port)
        {
            Credentials = new NetworkCredential(username, password),
            EnableSsl = true
        };

        using var message = new MailMessage
        {
            From = new MailAddress(fromAddress, fromName),
            Subject = subject,
            Body = body,
            IsBodyHtml = false
        };

        message.To.Add(to);

        await client.SendMailAsync(message, ct);

        logger.LogInformation(
            "Email sent. To={To} Subject={Subject}",
            to, subject);
    }
}