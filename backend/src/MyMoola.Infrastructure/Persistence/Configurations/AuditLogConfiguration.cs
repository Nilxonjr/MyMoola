using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class AuditLogConfiguration : IEntityTypeConfiguration<AuditLog>
{
    public void Configure(EntityTypeBuilder<AuditLog> builder)
    {
        builder.ToTable("audit_log");

        builder.HasKey(a => a.Id);
        builder.Property(a => a.Id).HasColumnName("id");

        builder.Property(a => a.ActorId).HasColumnName("actor_id");

        builder.Property(a => a.ActorType)
            .HasColumnName("actor_type")
            .HasMaxLength(20)
            .IsRequired();

        builder.Property(a => a.Action)
            .HasColumnName("action")
            .HasMaxLength(100)
            .IsRequired();
        builder.HasIndex(a => a.Action);

        builder.Property(a => a.TargetEntity)
            .HasColumnName("target_entity")
            .HasMaxLength(50)
            .IsRequired();

        builder.Property(a => a.TargetId).HasColumnName("target_id").IsRequired();
        builder.HasIndex(a => a.TargetId);

        builder.Property(a => a.IpAddress).HasColumnName("ip_address").HasMaxLength(50);
        builder.Property(a => a.BeforeState).HasColumnName("before_state");
        builder.Property(a => a.AfterState).HasColumnName("after_state");

        builder.Property(a => a.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.HasIndex(a => a.CreatedAt);

        // AuditLog is append-only — UpdatedAt is not needed
        builder.Ignore(a => a.UpdatedAt);
        builder.Ignore(a => a.DomainEvents);
    }
}
