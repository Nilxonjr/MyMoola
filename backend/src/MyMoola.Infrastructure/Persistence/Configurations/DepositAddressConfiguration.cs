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
        builder.Property(d => d.Id).HasColumnName("id");

        builder.Property(d => d.UserId).HasColumnName("user_id").IsRequired();

        builder.Property(d => d.Currency)
            .HasColumnName("currency")
            .HasMaxLength(10)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(d => d.Address)
            .HasColumnName("address")
            .HasMaxLength(100)
            .IsRequired();
        builder.HasIndex(d => d.Address).IsUnique();

        builder.Property(d => d.DerivationPath)
            .HasColumnName("derivation_path")
            .HasMaxLength(50)
            .IsRequired();

        builder.Property(d => d.IsActive).HasColumnName("is_active").HasDefaultValue(true);
        builder.Property(d => d.LastUsedAt).HasColumnName("last_used_at");

        builder.Property(d => d.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.Property(d => d.UpdatedAt).HasColumnName("updated_at").IsRequired();

        builder.HasOne<User>()
            .WithMany()
            .HasForeignKey(d => d.UserId)
            .OnDelete(DeleteBehavior.Restrict);

        builder.Ignore(d => d.DomainEvents);
    }
}
