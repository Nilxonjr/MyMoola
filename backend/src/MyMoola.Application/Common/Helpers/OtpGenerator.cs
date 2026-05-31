// Application/Common/Helpers/OtpGenerator.cs
namespace MyMoola.Application.Common.Helpers;

public static class OtpGenerator
{
    public static string Generate()
    {
        var bytes = new byte[4];
        System.Security.Cryptography.RandomNumberGenerator.Fill(bytes);
        var number = Math.Abs(BitConverter.ToInt32(bytes, 0)) % 1_000_000;
        return number.ToString("D6");
    }
}