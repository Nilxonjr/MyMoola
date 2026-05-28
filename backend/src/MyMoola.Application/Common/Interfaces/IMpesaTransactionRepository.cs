// MyMoola.Application/Common/Interfaces/IMpesaTransactionRepository.cs
using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface IMpesaTransactionRepository
{
    Task AddAsync(MpesaTransaction transaction, CancellationToken ct = default);

    Task<MpesaTransaction?> FindByIdAsync(Guid id, CancellationToken ct = default);

    Task<MpesaTransaction?> FindByCheckoutRequestIDAsync(
        string checkoutRequestID, CancellationToken ct = default);

    Task<MpesaTransaction?> FindByConversationIDAsync(
        string conversationID, CancellationToken ct = default);
}