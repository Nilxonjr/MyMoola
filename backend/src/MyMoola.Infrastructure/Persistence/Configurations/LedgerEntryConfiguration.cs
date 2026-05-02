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
        builder.Property(l => l.Id).HasColumnName("id");

        builder.Property(l => l.TransactionId).HasColumnName("transaction_id").IsRequired();
        builder.Property(l => l.WalletId).HasColumnName("wallet_id").IsRequired();

        builder.Property(l => l.EntryType)
            .HasColumnName("entry_type")
            .HasMaxLength(10)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(l => l.Amount)
            .HasColumnName("amount")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.ToTable(t => t.HasCheckConstraint("CK_ledger_entries_amount", "[amount] > 0"));

        builder.Property(l => l.BalanceBefore)
            .HasColumnName("balance_before")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(l => l.BalanceAfter)
            .HasColumnName("balance_after")
            .HasColumnType("decimal(28,18)")
            .IsRequired();

        builder.Property(l => l.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.HasIndex(l => l.CreatedAt);

        builder.HasOne<Transaction>()
            .WithMany()
            .HasForeignKey(l => l.TransactionId)
            .OnDelete(DeleteBehavior.Restrict);

        builder.HasOne<Wallet>()
            .WithMany()
            .HasForeignKey(l => l.WalletId)
            .OnDelete(DeleteBehavior.Restrict);

        // LedgerEntry is append-only — UpdatedAt is not needed
        builder.Ignore(l => l.UpdatedAt);
        builder.Ignore(l => l.DomainEvents);
    }
}
