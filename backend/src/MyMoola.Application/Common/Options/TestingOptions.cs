// MyMoola.Infrastructure/Settings/TestingOptions.cs
namespace MyMoola.Application.Common.Options;

public sealed class TestingOptions
{
    public const string SectionName = "Testing";
    public string? StkPushPhoneOverride { get; init; }
    public decimal AmountMultiplier { get; init; } = 1m;  // default 1 — no multiplier
}