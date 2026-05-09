using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Configuration;
using Microsoft.IdentityModel.Tokens;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class JwtTokenService(IConfiguration configuration) : ITokenService
{
    public string GenerateToken(Guid userId, string phoneNumber, string fullName)
    {
        var key = configuration["Jwt:SecretKey"]!;
        var issuer = configuration["Jwt:Issuer"]!;
        var audience = configuration["Jwt:Audience"]!;
        var expiry = int.Parse(configuration["Jwt:ExpiryMinutes"] ?? "15");


        var claims = new[]
        {
        new Claim(ClaimNames.UserId,   userId.ToString()),
        new Claim(ClaimNames.Phone,    phoneNumber),
        new Claim(ClaimNames.FullName, fullName),
        new Claim(ClaimNames.TokenId,  Guid.NewGuid().ToString()),
        };

        var signingKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(key));
        var credentials = new SigningCredentials(signingKey, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: issuer,
            audience: audience,
            claims: claims,
            expires: DateTime.UtcNow.AddMinutes(expiry),
            signingCredentials: credentials);

        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    /// <summary>
    /// Generates a cryptographically random 64-byte refresh token encoded as Base64.
    /// This raw value is returned to the client — never stored in the database.
    /// </summary>
    public string GenerateRefreshToken()
    {
        var bytes = new byte[64];
        RandomNumberGenerator.Fill(bytes);
        return Convert.ToBase64String(bytes);
    }

    /// <summary>
    /// Hashes the raw refresh token using SHA-256.
    /// Only the hash is stored in the database — if stolen, it is useless without the raw value.
    /// </summary>
    public string HashRefreshToken(string rawToken)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(rawToken));
        return Convert.ToBase64String(bytes);
    }
}