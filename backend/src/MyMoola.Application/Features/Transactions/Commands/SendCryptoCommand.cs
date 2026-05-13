using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record SendCryptoCommand(
    string RecipientPhone,
    Currency Currency,
    decimal Amount,
    string Pin) : IRequest<SendCryptoResponse>;

public sealed record SendCryptoResponse(
    Guid TransactionId,
    string ReferenceCode,
    string Message);