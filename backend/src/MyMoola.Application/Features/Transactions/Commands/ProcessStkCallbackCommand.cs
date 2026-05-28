// MyMoola.Application/Features/Transactions/Commands/ProcessStkCallbackCommand.cs
using MediatR;
using MyMoola.Application.Features.Transactions.DTOs;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record ProcessStkCallbackCommand(
    StkCallbackPayload Callback) : IRequest;