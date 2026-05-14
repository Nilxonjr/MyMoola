using MediatR;


namespace MyMoola.Application.Features.Users.Queries;

public sealed record LookupUserByPhoneQuery(string PhoneNumber) : IRequest<LookupUserByPhoneResponse>;

public sealed record LookupUserByPhoneResponse(string FullName, string PhoneNumber);

