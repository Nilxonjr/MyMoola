using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.Extensions.Configuration;
using Microsoft.IdentityModel.Tokens;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Services;

public sealed class AdminTokenService(
    IConfiguration configuration) : IAdminTokenService
{
    public string GenerateToken(AdminUser admin)
    {
        var key = configuration["AdminJwt:SecretKey"]
            ?? throw new InvalidOperationException("AdminJwt:SecretKey is not configured.");
        var issuer = configuration["AdminJwt:Issuer"]
            ?? throw new InvalidOperationException("AdminJwt:Issuer is not configured.");
        var audience = configuration["AdminJwt:Audience"]
            ?? throw new InvalidOperationException("AdminJwt:Audience is not configured.");
        var expiry = int.Parse(configuration["AdminJwt:ExpiryMinutes"] ?? "60");

        var claims = new[]
        {
            new Claim(AdminClaimNames.AdminId, admin.Id.ToString()),
            new Claim(AdminClaimNames.Email, admin.Email),
            new Claim(AdminClaimNames.Role, admin.Role.ToString()),
            new Claim(AdminClaimNames.TokenId, Guid.NewGuid().ToString()),
            new Claim(AdminClaimNames.MustChangePassword,
                admin.MustChangePassword.ToString().ToLower())
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
}