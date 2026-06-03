// MyMoola.Application/Common/Options/MpesaOptions.cs
namespace MyMoola.Infrastructure.Settings;

public sealed class MpesaOptions
{
    public const string SectionName = "Mpesa";

    public string BaseUrl { get; init; } = default!;
    public string ConsumerKey { get; init; } = default!;
    public string ConsumerSecret { get; init; } = default!;
    public string ShortCode { get; init; } = default!;
    public string Passkey { get; init; } = default!;
    public string B2CInitiatorName { get; init; } = default!;
    public string B2CSecurityCredential { get; init; } = default!;
    public string StkCallbackUrl { get; init; } = default!;
    public string B2CCallbackUrl { get; init; } = default!;
    public string B2CQueueTimeOutUrl { get; init; } = default!;

    public string B2BCallbackUrl { get; init; } = default!;
    public string B2BQueueTimeOutUrl { get; init; } = default!;

    public string B2BShortCode { get; init; } = default!;

    public string B2CShortCode { get; init; } = default!;
}