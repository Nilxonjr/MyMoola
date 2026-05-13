namespace MyMoola.Domain.Common;

using MyMoola.Domain.Exceptions;
using System;
public abstract class BaseEntity
{
    public Guid Id { get; private set; } = Guid.NewGuid();
    
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset UpdatedAt { get; private set; }

    private readonly List<IDomainEvent> _domainEvents = [];
    
    public IReadOnlyCollection<IDomainEvent> DomainEvents => _domainEvents.AsReadOnly();

    protected void AddDomainEvent(IDomainEvent domainEvent) => _domainEvents.Add(domainEvent);
    public void ClearDomainEvents() => _domainEvents.Clear();
    public void SetCreatedAt(DateTimeOffset now) => CreatedAt = now;

    public void SetUpdatedAt(DateTimeOffset now) => UpdatedAt = now;

}