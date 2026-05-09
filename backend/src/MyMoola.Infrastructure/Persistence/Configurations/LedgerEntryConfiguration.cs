using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class LedgerEntryConfiguration : IEntityTypeConfiguration<LedgerEntry>
{
    public void Configure(EntityTypeBuilder<LedgerEntry> builder)
    {
        builder.ToTable("ledger_entries");

        builder.HasKey(l => l.Id);

        builder.Property(l => l.Id)
            .HasColumnName("id")
            .ValueGeneratedNever();

        builder.Property(l => l.TransactionId)
            .HasColumnName("transaction_id")
            .IsRequired();

        builder.Property(l => l.WalletId)
            .HasColumnName("wallet_id")
            .IsRequired();

        builder.Property(l => l.Currency)
            .HasColumnName("currency")
            .HasConversion<string>()
            .IsRequired();

        builder.Property(l => l.EntryType)
            .HasColumnName("entry_type")
            .HasConversion<string>()
            .IsRequired();

        builder.Property(l => l.Amount)
            .HasColumnName("amount")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(l => l.AvailableBalanceBefore)
            .HasColumnName("available_balance_before")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(l => l.AvailableBalanceAfter)
            .HasColumnName("available_balance_after")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(l => l.LockedBalanceBefore)
            .HasColumnName("locked_balance_before")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(l => l.LockedBalanceAfter)
            .HasColumnName("locked_balance_after")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(l => l.CreatedAt)
            .HasColumnName("created_at")
            .IsRequired();

        builder.Ignore(l => l.UpdatedAt);
        builder.Ignore(l => l.DomainEvents);

        builder.ToTable(t => t.HasCheckConstraint("CK_ledger_entries_amount", "[amount] > 0"));

        builder.HasIndex(l => l.WalletId)
            .HasDatabaseName("IX_ledger_entries_wallet_id");

        builder.HasIndex(l => l.TransactionId)
            .HasDatabaseName("IX_ledger_entries_transaction_id");

        builder.HasIndex(l => l.CreatedAt)
            .HasDatabaseName("IX_ledger_entries_created_at");

        builder.HasOne<Transaction>()
            .WithMany()
            .HasForeignKey(l => l.TransactionId)
            .OnDelete(DeleteBehavior.Restrict)
            .HasConstraintName("FK_ledger_entries_transactions_transaction_id");

        builder.HasOne<Wallet>()
            .WithMany()
            .HasForeignKey(l => l.WalletId)
            .OnDelete(DeleteBehavior.Restrict)
            .HasConstraintName("FK_ledger_entries_wallets_wallet_id");
    }
}