// MyMoola.Domain/Enums/OutboxMessageStatus.cs
namespace MyMoola.Domain.Enums;

public enum OutboxMessageStatus
{
    Pending,
    Processed,
    DeadLettered
}