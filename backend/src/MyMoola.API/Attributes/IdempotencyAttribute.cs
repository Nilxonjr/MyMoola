namespace MyMoola.API.Attributes;

[AttributeUsage(AttributeTargets.Method | AttributeTargets.Class)]
public sealed class IdempotencyAttribute : Attribute { }