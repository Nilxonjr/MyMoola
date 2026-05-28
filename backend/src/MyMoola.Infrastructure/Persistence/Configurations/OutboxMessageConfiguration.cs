// MyMoola.Infrastructure/Persistence/Configurations/OutboxMessageConfiguration.cs
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class OutboxMessageConfiguration : IEntityTypeConfiguration<OutboxMessage>
{
    public void Configure(EntityTypeBuilder<OutboxMessage> builder)
    {
        builder.ToTable("outbox_messages");

        builder.HasKey(m => m.Id);

        builder.Property(m => m.Id)
            .HasColumnName("id")
            .ValueGeneratedNever();

        builder.Property(m => m.Type)
            .HasColumnName("type")
            .HasMaxLength(100)
            .IsRequired();

        builder.Property(m => m.Payload)
            .HasColumnName("payload")
            .IsRequired();

        builder.Property(m => m.Status)
            .HasColumnName("status")
            .HasMaxLength(20)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(m => m.RetryCount)
            .HasColumnName("retry_count")
            .HasDefaultValue(0)
            .IsRequired();

        builder.Property(m => m.Error)
            .HasColumnName("error")
            .HasMaxLength(2000);

        builder.Property(m => m.ProcessedAt)
            .HasColumnName("processed_at");

        builder.Property(m => m.LockedUntil)
            .HasColumnName("locked_until");

        builder.Property(m => m.LastAttemptedAt)
            .HasColumnName("last_attempted_at");

        builder.Property(m => m.CreatedAt)
            .HasColumnName("created_at")
            .IsRequired();

        builder.Property(m => m.UpdatedAt)
            .HasColumnName("updated_at")
            .IsRequired();

        builder.ToTable(t => t.HasCheckConstraint(
            "CK_outbox_messages_retry_count",
            "retry_count >= 0"));

        builder.HasIndex(m => new { m.Status, m.LockedUntil, m.CreatedAt })
            .HasDatabaseName("IX_outbox_messages_status_locked_until_created_at");

        builder.Ignore(m => m.DomainEvents);
    }
}