using MediatR;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.ChangeTracking;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Common;
using MyMoola.Domain.Entities;
using MyMoola.Infrastructure.Persistence.Interceptors;
using System.Reflection;

namespace MyMoola.Infrastructure.Persistence;

public sealed class AppDbContext : DbContext
{
    private readonly IPublisher _publisher;
    private readonly AuditInterceptor _auditInterceptor;

    public AppDbContext(
        DbContextOptions<AppDbContext> options,
        IPublisher publisher,
        AuditInterceptor auditInterceptor)
        : base(options)
    {
        _publisher = publisher;
        _auditInterceptor = auditInterceptor;
    }

    // --- Financial & Core Tables ---
    public DbSet<User> Users => Set<User>();
    public DbSet<Wallet> Wallets => Set<Wallet>();
    public DbSet<Transaction> Transactions => Set<Transaction>();
    public DbSet<LedgerEntry> LedgerEntries => Set<LedgerEntry>();
    public DbSet<MpesaTransaction> MpesaTransactions => Set<MpesaTransaction>();

    // --- System & Treasury Tables ---
    public DbSet<ExchangeRate> ExchangeRates => Set<ExchangeRate>();
    public DbSet<TreasuryPosition> TreasuryPositions => Set<TreasuryPosition>();
    public DbSet<SystemControl> SystemControls => Set<SystemControl>();
    public DbSet<DepositAddress> DepositAddresses => Set<DepositAddress>();

    // --- Infrastructure & Admin Tables ---
    public DbSet<AuditLog> AuditLogs => Set<AuditLog>();
    public DbSet<IdempotencyKey> IdempotencyKeys => Set<IdempotencyKey>();
    public DbSet<AdminUser> AdminUsers => Set<AdminUser>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();

    public DbSet<OutboxMessage> OutboxMessages => Set<OutboxMessage>();

    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        // Register the interceptor here so it participates in every SaveChangesAsync call.
        // We inject it rather than constructing it inline so its own dependencies
        // (ICurrentUserService, ICurrentAdminService) are resolved from DI.
        optionsBuilder.AddInterceptors(_auditInterceptor);
    }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(AppDbContext).Assembly);
        base.OnModelCreating(modelBuilder);
    }

    public override async Task<int> SaveChangesAsync(CancellationToken ct = default)
    {

        // 1. DOMAIN EVENT DISPATCHING
        var entitiesWithEvents = ChangeTracker
            .Entries<BaseEntity>()
            .Select(e => e.Entity)
            .Where(e => e.DomainEvents.Any())
            .ToList();

        var domainEvents = entitiesWithEvents
            .SelectMany(e => e.DomainEvents)
            .ToList();

        // Clear before publishing to prevent infinite loops if a handler
        // triggers another SaveChangesAsync
        entitiesWithEvents.ForEach(e => e.ClearDomainEvents());

        // 2. PUBLISH EVENTS BEFORE SAVE
        // Handlers (e.g. LedgerEntryHandler) add records to the ChangeTracker here.
        // AuditInterceptor fires AFTER this, inside base.SaveChangesAsync,
        // so it will also see those handler-created entities if they are auditable.
        foreach (var domainEvent in domainEvents)
            await _publisher.Publish(domainEvent, ct);

        // 3. AUTOMATIC AUDIT STAMPS
        foreach (var entry in ChangeTracker.Entries<BaseEntity>())
        {
            var now = DateTimeOffset.UtcNow;
            if (entry.State == EntityState.Added)
                entry.Entity.SetCreatedAt(now);

            if (entry.State == EntityState.Modified || entry.State == EntityState.Added)
                entry.Entity.SetUpdatedAt(now);
        }

        // 4. THE ATOMIC COMMIT
        // AuditInterceptor.SavingChangesAsync runs here, before the SQL is sent.
        // Audit entries are added to the change tracker and committed in the same transaction.
        return await base.SaveChangesAsync(ct);
    }
}