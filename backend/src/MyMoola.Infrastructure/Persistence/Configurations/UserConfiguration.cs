using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;
using MyMoola.Domain.ValueObjects;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class UserConfiguration : IEntityTypeConfiguration<User>
{
    public void Configure(EntityTypeBuilder<User> builder)
    {
        builder.ToTable("users");

        builder.HasKey(u => u.Id);
        builder.Property(u => u.Id).HasColumnName("id")
            .ValueGeneratedNever();

        builder.Property(u => u.PhoneNumberValue)
    .HasColumnName("phone_number")
    .HasMaxLength(20)
    .IsRequired();

        builder.HasIndex(u => u.PhoneNumberValue)
            .IsUnique();

        builder.Ignore(u => u.PhoneNumber);


        builder.Property(u => u.PhoneVerifiedAt).HasColumnName("phone_verified_at");

        builder.Property(u => u.Email).HasColumnName("email").HasMaxLength(200);
        builder.HasIndex(u => u.Email).IsUnique().HasFilter("[email] IS NOT NULL");

        builder.Property(u => u.EmailVerifiedAt).HasColumnName("email_verified_at");

        builder.Property(u => u.PinHash)
            .HasColumnName("pin_hash")
            .HasMaxLength(100)
            .IsRequired();

        builder.Property(u => u.FailedPinAttempts)
            .HasColumnName("failed_pin_attempts")
            .HasDefaultValue(0);

        builder.Property(u => u.PinLockedUntil).HasColumnName("pin_locked_until");

        builder.Property(u => u.AccountStatus)
            .HasColumnName("account_status")
            .HasMaxLength(20)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(u => u.FreezeReason).HasColumnName("freeze_reason");

        builder.Property(u => u.FullName)
            .HasColumnName("full_name")
            .HasMaxLength(100)
            .IsRequired();

        builder.Property(u => u.NationalId).HasColumnName("national_id").HasMaxLength(20);
        builder.HasIndex(u => u.NationalId).IsUnique().HasFilter("[national_id] IS NOT NULL");

        builder.Property(u => u.KycStatus)
            .HasColumnName("kyc_status")
            .HasMaxLength(20)
            .HasConversion<string>()
            .IsRequired();

        builder.Property(u => u.KycVerifiedAt).HasColumnName("kyc_verified_at");
        builder.Property(u => u.LastLoginAt).HasColumnName("last_login_at");

        builder.Property(u => u.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.Property(u => u.UpdatedAt).HasColumnName("updated_at").IsRequired();

        builder.Property(u => u.RowVersion).HasColumnName("row_version").IsRowVersion();

        builder.Ignore(u => u.DomainEvents);
    }
}
