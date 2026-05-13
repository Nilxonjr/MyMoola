using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class TransactionConfiguration : IEntityTypeConfiguration<Transaction>
{
    public void Configure(EntityTypeBuilder<Transaction> builder)
    {
        builder.ToTable("transactions");

        builder.HasKey(t => t.Id);
        builder.Property(t => t.Id).HasColumnName("id").ValueGeneratedNever();

        builder.Property(t => t.ReferenceCode)
            .HasColumnName("reference_code")
            .HasMaxLength(30)
            .IsRequired();
        builder.HasIndex(t => t.ReferenceCode).IsUnique();

        builder.Property(t => t.Type)
            .HasColumnName("type")
            .HasMaxLength(20)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(t => t.Status)
            .HasColumnName("status")
            .HasMaxLength(20)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(t => t.InitiatorUserId).HasColumnName("initiator_user_id");
        builder.Property(t => t.CounterpartyUserId).HasColumnName("counterparty_user_id");

        builder.Property(t => t.Currency)
            .HasColumnName("currency")
            .HasMaxLength(10)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(t => t.Amount)
            .HasColumnName("amount")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(t => t.FeeAmount)
            .HasColumnName("fee_amount")
            .HasColumnType("decimal(28,18)")
            .HasDefaultValue(0m)
            .IsRequired();

        builder.Property(t => t.KesAmount)
            .HasColumnName("kes_amount")
            .HasColumnType("decimal(18,2)");

        builder.Property(t => t.ExchangeRateSnapshot)
            .HasColumnName("exchange_rate_snapshot")
            .HasColumnType("decimal(28,8)");

        builder.Property(t => t.MarketRateSnapshot)
            .HasColumnName("market_rate_snapshot")
            .HasColumnType("decimal(28,8)");

        builder.Property(t => t.MpesaReference).HasColumnName("mpesa_reference").HasMaxLength(50);
        builder.HasIndex(t => t.MpesaReference).HasFilter("[mpesa_reference] IS NOT NULL");

        builder.Property(t => t.OnChainTxHash).HasColumnName("on_chain_tx_hash").HasMaxLength(100);
        builder.HasIndex(t => t.OnChainTxHash).HasFilter("[on_chain_tx_hash] IS NOT NULL");

        builder.Property(t => t.OnChainConfirmations)
            .HasColumnName("on_chain_confirmations")
            .HasDefaultValue(0);

        builder.Property(t => t.IdempotencyKey)
            .HasColumnName("idempotency_key")
            .HasMaxLength(100)
            .IsRequired();
        builder.HasIndex(t => new { t.InitiatorUserId, t.IdempotencyKey })
            .IsUnique()
            .HasDatabaseName("IX_transactions_initiator_idempotency_key");

        builder.Property(t => t.Metadata).HasColumnName("metadata");
        builder.Property(t => t.AdminNote).HasColumnName("admin_note");

        builder.Property(t => t.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.HasIndex(t => t.CreatedAt);

        builder.Property(t => t.UpdatedAt).HasColumnName("updated_at").IsRequired();
        builder.Property(t => t.CompletedAt).HasColumnName("completed_at");

        builder.Ignore(t => t.DomainEvents);
    }
}
