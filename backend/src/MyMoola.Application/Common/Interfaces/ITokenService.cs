namespace MyMoola.Application.Common.Interfaces;

public interface ITokenService
{
    string GenerateToken(Guid userId, string phoneNumber, string fullName);
    string GenerateRefreshToken();
    string HashRefreshToken(string rawToken);
}