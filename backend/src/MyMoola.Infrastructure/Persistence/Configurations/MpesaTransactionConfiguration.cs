using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Configurations;

public sealed class MpesaTransactionConfiguration : IEntityTypeConfiguration<MpesaTransaction>
{
    public void Configure(EntityTypeBuilder<MpesaTransaction> builder)
    {
        builder.ToTable("mpesa_transactions");

        builder.HasKey(m => m.Id);
        builder.Property(m => m.Id).HasColumnName("id").ValueGeneratedNever();

        builder.Property(m => m.TransactionId).HasColumnName("transaction_id").IsRequired();

        builder.Property(m => m.CheckoutRequestId)
            .HasColumnName("checkout_request_id")
            .HasMaxLength(100);
        builder.HasIndex(m => m.CheckoutRequestId)
            .HasFilter("[checkout_request_id] IS NOT NULL");

        builder.Property(m => m.MerchantRequestId)
            .HasColumnName("merchant_request_id")
            .HasMaxLength(100);

        builder.Property(m => m.MpesaReceiptNumber)
            .HasColumnName("mpesa_receipt_number")
            .HasMaxLength(50);
        builder.HasIndex(m => m.MpesaReceiptNumber)
            .IsUnique()
            .HasFilter("[mpesa_receipt_number] IS NOT NULL");

        // Stored encrypted at application layer
        builder.Property(m => m.PhoneNumber)
            .HasColumnName("phone_number")
            .HasMaxLength(500)
            .IsRequired();

        builder.Property(m => m.AmountKes)
            .HasColumnName("amount_kes")
            .HasColumnType("decimal(18,2)")
            .IsRequired();

        builder.Property(m => m.Direction)
            .HasColumnName("direction")
            .HasMaxLength(10)
            .IsRequired();

        builder.Property(m => m.Status)
            .HasColumnName("status")
            .HasMaxLength(20)
            .IsRequired();

        builder.Property(m => m.RawCallbackPayload).HasColumnName("raw_callback_payload");

        builder.Property(m => m.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.Property(m => m.UpdatedAt).HasColumnName("updated_at").IsRequired();

        builder.HasOne<Transaction>()
            .WithMany()
            .HasForeignKey(m => m.TransactionId)
            .OnDelete(DeleteBehavior.Restrict);

        builder.Ignore(m => m.DomainEvents);
    }
}
