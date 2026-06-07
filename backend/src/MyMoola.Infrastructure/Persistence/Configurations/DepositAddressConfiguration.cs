using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class DepositAddressConfiguration : IEntityTypeConfiguration<DepositAddress>
{
    public void Configure(EntityTypeBuilder<DepositAddress> builder)
    {
        builder.ToTable("deposit_addresses");

        builder.HasKey(d => d.Id);
        builder.Property(d => d.Id).HasColumnName("id")
            .ValueGeneratedNever();

        builder.Property(d => d.UserId).HasColumnName("user_id").IsRequired();

        builder.Property(d => d.Chain)
            .HasColumnName("chain")
            .HasConversion<string>()
            .IsRequired();

        builder.Property(d => d.Address)
            .HasColumnName("address")
            .HasMaxLength(100)
            .IsRequired();

        builder.HasIndex(d => d.Address)
            .IsUnique()
            .HasDatabaseName("ix_deposit_addresses_address");

        builder.Property(d => d.DerivationPath)
            .HasColumnName("derivation_path")
            .HasMaxLength(50)
            .IsRequired();

        builder.Property(d => d.DerivationIndex)
            .HasColumnName("derivation_index")
            .IsRequired();


        builder.Property(d => d.IsActive).HasColumnName("is_active").HasDefaultValue(true);
        builder.Property(d => d.LastUsedAt).HasColumnName("last_used_at");

        builder.Property(d => d.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.Property(d => d.UpdatedAt).HasColumnName("updated_at").IsRequired();

        builder.HasOne<User>()
            .WithMany()
            .HasForeignKey(d => d.UserId)
            .OnDelete(DeleteBehavior.Restrict);

        builder.HasIndex(d => new { d.UserId, d.Chain })
            .IsUnique()
            .HasFilter("is_active = true")
            .HasDatabaseName("ix_deposit_addresses_user_chain_active");

        builder.Ignore(d => d.DomainEvents);
    }
}
