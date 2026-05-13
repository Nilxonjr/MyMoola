using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class WalletConfiguration : IEntityTypeConfiguration<Wallet>
{
    public void Configure(EntityTypeBuilder<Wallet> builder)
    {
        builder.ToTable("wallets");

        builder.HasKey(w => w.Id);
        builder.Property(w => w.Id).HasColumnName("id").ValueGeneratedNever();

        builder.Property(w => w.UserId).HasColumnName("user_id").IsRequired();

        builder.Property(w => w.Currency)
            .HasColumnName("currency")
            .HasMaxLength(10)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(w => w.Balance)
            .HasColumnName("balance")
            .HasColumnType("decimal(28,18)")
            .HasDefaultValue(0m)
            .IsRequired();

        builder.Property(w => w.LockedBalance)
            .HasColumnName("locked_balance")
            .HasColumnType("decimal(28,18)")
            .HasDefaultValue(0m)
            .IsRequired();

        builder.Property(w => w.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.Property(w => w.UpdatedAt).HasColumnName("updated_at").IsRequired();

        builder.Property(w => w.RowVersion).HasColumnName("row_version").IsRowVersion();

        builder.HasIndex(w => new { w.UserId, w.Currency })
    .IsUnique()
    .HasDatabaseName("IX_wallets_user_currency");

        builder.ToTable(t => t.HasCheckConstraint("CK_wallets_balance", "[balance] >= 0"));
        builder.ToTable(t => t.HasCheckConstraint("CK_wallets_locked_balance", "[locked_balance] >= 0"));

        builder.HasOne<User>()
            .WithMany()
            .HasForeignKey(w => w.UserId)
            .OnDelete(DeleteBehavior.Restrict);

        builder.Ignore(w => w.DomainEvents);
        builder.Ignore(w => w.TotalBalance);
    }
}
