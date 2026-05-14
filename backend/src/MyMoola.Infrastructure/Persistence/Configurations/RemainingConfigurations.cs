using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class ExchangeRateConfiguration : IEntityTypeConfiguration<ExchangeRate>
{
    public void Configure(EntityTypeBuilder<ExchangeRate> builder)
    {
        builder.ToTable("exchange_rates");

        builder.HasKey(e => e.Id);
        builder.Property(e => e.Id).HasColumnName("id").ValueGeneratedNever();

        builder.Property(e => e.Currency)
            .HasColumnName("currency")
            .HasMaxLength(10)
            .HasConversion<string>()
            .IsRequired();
        builder.HasIndex(e => e.Currency);

        builder.Property(e => e.RateKes).HasColumnName("rate_kes").HasColumnType("decimal(18,4)").IsRequired();
        builder.Property(e => e.RateUsd).HasColumnName("rate_usd").HasColumnType("decimal(18,4)").IsRequired();
        builder.Property(e => e.BuyRateKes).HasColumnName("buy_rate_kes").HasColumnType("decimal(18,4)").IsRequired();
        builder.Property(e => e.SellRateKes).HasColumnName("sell_rate_kes").HasColumnType("decimal(18,4)").IsRequired();
        builder.Property(e => e.SpreadPercent).HasColumnName("spread_percent").HasColumnType("decimal(6,4)").IsRequired();

        builder.Property(e => e.Source).HasColumnName("source").HasMaxLength(30).IsRequired();

        builder.Property(e => e.FetchedAt).HasColumnName("fetched_at").IsRequired();
        builder.HasIndex(e => e.FetchedAt);

        // ExchangeRate is append-only — CreatedAt and UpdatedAt from BaseEntity not needed
        builder.Ignore(e => e.CreatedAt);
        builder.Ignore(e => e.UpdatedAt);
        builder.Ignore(e => e.DomainEvents);
    }
}

public sealed class TreasuryPositionConfiguration : IEntityTypeConfiguration<TreasuryPosition>
{
    public void Configure(EntityTypeBuilder<TreasuryPosition> builder)
    {
        builder.ToTable("treasury_positions");

        builder.HasKey(t => t.Id);
        builder.Property(t => t.Id).HasColumnName("id").ValueGeneratedNever();

        builder.Property(t => t.Currency)
            .HasColumnName("currency")
            .HasMaxLength(10)
            .HasConversion<string>()
            .IsRequired();
        builder.HasIndex(t => t.Currency).IsUnique();

        builder.Property(t => t.Balance)
            .HasColumnName("balance")
            .HasColumnType("decimal(28,18)")
            .HasDefaultValue(0m)
            .IsRequired();

        builder.Property(t => t.KesReserve)
            .HasColumnName("kes_reserve")
            .HasColumnType("decimal(18,2)")
            .HasDefaultValue(0m)
            .IsRequired();

        builder.Property(t => t.CoverageRatio)
            .HasColumnName("coverage_ratio")
            .HasColumnType("decimal(8,4)")
            .HasDefaultValue(0m)
            .IsRequired();

        builder.Property(t => t.BuyHalted).HasColumnName("buy_halted").HasDefaultValue(false).IsRequired();
        builder.Property(t => t.LastRebalancedAt).HasColumnName("last_rebalanced_at");
        builder.Property(t => t.LastSyncedAt).HasColumnName("last_synced_at");

        builder.Property(t => t.UpdatedAt).HasColumnName("updated_at").IsRequired();

        builder.Ignore(t => t.CreatedAt);
        builder.Ignore(t => t.DomainEvents);
    }
}

public sealed class SystemControlConfiguration : IEntityTypeConfiguration<SystemControl>
{
    public void Configure(EntityTypeBuilder<SystemControl> builder)
    {
        builder.ToTable("system_controls");

        builder.HasKey(s => s.Id);
        builder.Property(s => s.Id).HasColumnName("id").ValueGeneratedNever();

        builder.Property(s => s.ControlKey)
            .HasColumnName("control_key")
            .HasMaxLength(50)
            .IsRequired();
        builder.HasIndex(s => s.ControlKey).IsUnique();

        builder.Property(s => s.IsEnabled).HasColumnName("is_enabled").HasDefaultValue(true).IsRequired();
        builder.Property(s => s.Reason).HasColumnName("reason");
        builder.Property(s => s.DisabledBy).HasColumnName("disabled_by");
        builder.Property(s => s.DisabledAt).HasColumnName("disabled_at");

        builder.Property(s => s.UpdatedAt).HasColumnName("updated_at").IsRequired();

        builder.Ignore(s => s.CreatedAt);
        builder.Ignore(s => s.DomainEvents);
    }
}

public sealed class IdempotencyKeyConfiguration : IEntityTypeConfiguration<IdempotencyKey>
{
    public void Configure(EntityTypeBuilder<IdempotencyKey> builder)
    {
        builder.ToTable("idempotency_keys");

        builder.HasKey(i => i.Key);
        builder.Property(i => i.Key).HasColumnName("key").HasMaxLength(100).IsRequired();

        builder.Property(i => i.ResponseBody).HasColumnName("response_body").IsRequired();
        builder.Property(i => i.StatusCode).HasColumnName("status_code").IsRequired();
        builder.Property(i => i.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.Property(i => i.ExpiresAt).HasColumnName("expires_at").IsRequired();

        builder.HasIndex(i => i.ExpiresAt);
    }
}

public sealed class AdminUserConfiguration : IEntityTypeConfiguration<AdminUser>
{
    public void Configure(EntityTypeBuilder<AdminUser> builder)
    {
        builder.ToTable("admin_users");

        builder.HasKey(a => a.Id);

        builder.Property(a => a.Id)
            .HasColumnName("id")
            .ValueGeneratedNever();

        builder.Property(a => a.Name)
            .HasColumnName("name")
            .HasMaxLength(100)
            .IsRequired();

        builder.Property(a => a.Email)
            .HasColumnName("email")
            .HasMaxLength(200)
            .IsRequired();

        builder.HasIndex(a => a.Email)
            .IsUnique()
            .HasDatabaseName("IX_admin_users_email");

        builder.Property(a => a.PasswordHash)
            .HasColumnName("password_hash")
            .HasMaxLength(100)
            .IsRequired();

        builder.Property(a => a.Role)
            .HasColumnName("role")
            .HasMaxLength(30)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(a => a.IsActive)
            .HasColumnName("is_active")
            .HasDefaultValue(true)
            .IsRequired();

        builder.Property(a => a.MustChangePassword)
            .HasColumnName("must_change_password")
            .HasDefaultValue(true)
            .IsRequired();

        builder.Property(a => a.LastLoginAt)
            .HasColumnName("last_login_at");

        builder.Property(a => a.CreatedBy)
            .HasColumnName("created_by");

        #pragma warning disable CS0618
        builder.UseXminAsConcurrencyToken();
        #pragma warning restore CS0618

        builder.Property(a => a.CreatedAt)
            .HasColumnName("created_at")
            .IsRequired();

        builder.Property(a => a.UpdatedAt)
            .HasColumnName("updated_at")
            .IsRequired();

        builder.Ignore(a => a.DomainEvents);
    }
}