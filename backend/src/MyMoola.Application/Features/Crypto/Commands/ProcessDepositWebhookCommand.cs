using MediatR;

namespace MyMoola.Application.Features.Crypto.Commands;

public sealed record ProcessDepositWebhookCommand(
    string RawPayload) : IRequest;