// MyMoola.Application/Common/Options/OutboxProcessorOptions.cs
namespace MyMoola.Infrastructure.Settings;

public sealed class OutboxProcessorOptions
{
    public const string SectionName = "OutboxProcessor";

    public int BatchSize { get; init; } = 10;
    public int PollingIntervalSeconds { get; init; } = 5;
    public int LockDurationSeconds { get; init; } = 30;
}