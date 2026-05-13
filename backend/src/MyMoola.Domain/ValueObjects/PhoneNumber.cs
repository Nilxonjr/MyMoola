using System.Text.RegularExpressions;

namespace MyMoola.Domain.ValueObjects;

public sealed record PhoneNumber
{
    public string Value { get; }

    private static readonly Regex E164Regex = new(@"^\+[1-9]\d{7,14}$", RegexOptions.Compiled);

    public PhoneNumber(string value)
    {
        if (string.IsNullOrWhiteSpace(value) || !E164Regex.IsMatch(value))
            throw new ArgumentException($"Invalid phone number format. Must be E.164 e.g. +254712345678. Got: {value}");

        Value = value;
    }

    public override string ToString() => Value;
}
