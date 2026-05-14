using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Users.Queries;


namespace MyMoola.Application.Features.Users.Handlers;


public sealed class LookupUserByPhoneHandler(
	ICurrentUserService currentUser,
	IUserRepository users,
	ILogger<LookupUserByPhoneHandler> logger)
	: IRequestHandler<LookupUserByPhoneQuery, LookupUserByPhoneResponse>
{
	public async Task<LookupUserByPhoneResponse> Handle(
		LookupUserByPhoneQuery request, CancellationToken ct)
	{
		if (currentUser.UserId is null)
			throw new UnauthorizedException();

		var user = await users.FindByPhoneAsync(request.PhoneNumber, ct)
			?? throw new NotFoundException(nameof(User), request.PhoneNumber);

		user.EnsureActive();

		logger.LogInformation(
			"Phone lookup performed. RequestedBy={RequestedBy} Phone={Phone}",
			currentUser.UserId, request.PhoneNumber);

		return new LookupUserByPhoneResponse(
			FullName: user.FullName,
			PhoneNumber: user.PhoneNumberValue);
	}
}