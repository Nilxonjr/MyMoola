namespace MyMoola.Application.Common.Helpers;

public static class ReferenceCodeGenerator
{
    private const string Characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public static string Generate(string prefix = "TXN")
    {
        var datePart = DateTimeOffset.UtcNow.ToString("yyyyMMdd");
        var randomPart = new string(
            Enumerable.Range(0, 6)
                .Select(_ => Characters[Random.Shared.Next(Characters.Length)])
                .ToArray());

        return $"{prefix}-{datePart}-{randomPart}";
    }
}