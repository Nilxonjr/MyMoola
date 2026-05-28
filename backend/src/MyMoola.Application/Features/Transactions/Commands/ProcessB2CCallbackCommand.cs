// MyMoola.Application/Features/Transactions/Commands/ProcessB2CCallbackCommand.cs
using MediatR;
using MyMoola.Application.Features.Transactions.DTOs;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record ProcessB2CCallbackCommand(
    B2CCallbackPayload Callback) : IRequest;

public sealed record ProcessB2CTimeoutCommand(
    B2CCallbackPayload Callback) : IRequest;