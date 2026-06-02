// MyMoola.Application/Features/Transactions/Commands/ProcessB2BCallbackCommand.cs
using MediatR;
using MyMoola.Application.Features.Transactions.DTOs;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record ProcessB2BCallbackCommand(
    B2BCallbackPayload Callback) : IRequest;

public sealed record ProcessB2BTimeoutCommand(
    B2BCallbackPayload Callback) : IRequest;