using MediatR;
using Microsoft.EntityFrameworkCore.ChangeTracking;
using Microsoft.EntityFrameworkCore;
using MyMoola.Domain.Common;
using MyMoola.Domain.Entities;
using System.Reflection;


namespace MyMoola.Infrastructure.Persistence;

public sealed class AppDbContext : DbContext
{
    private readonly IPublisher _publisher;

    public AppDbContext(DbContextOptions<AppDbContext> options, IPublisher publisher)
        : base(options)
    {
        _publisher = publisher; // MediatR publisher to trigger event handlers before the save commit
    }

    // --- Financial & Core Tables ---
    public DbSet<User> Users => Set<User>();
    public DbSet<Wallet> Wallets => Set<Wallet>();
    public DbSet<Transaction> Transactions => Set<Transaction>();
    public DbSet<LedgerEntry> LedgerEntries => Set<LedgerEntry>(); // The financial source of truth
    public DbSet<MpesaTransaction> MpesaTransactions => Set<MpesaTransaction>(); //[cite: 4]

    // --- System & Treasury Tables ---
    public DbSet<ExchangeRate> ExchangeRates => Set<ExchangeRate>(); //[cite: 5]
    public DbSet<TreasuryPosition> TreasuryPositions => Set<TreasuryPosition>(); //[cite: 5]
    public DbSet<SystemControl> SystemControls => Set<SystemControl>(); //[cite: 5]
    public DbSet<DepositAddress> DepositAddresses => Set<DepositAddress>(); //[cite: 2]

    // --- Infrastructure & Admin Tables ---
    public DbSet<AuditLog> AuditLogs => Set<AuditLog>(); //[cite: 1]
    public DbSet<IdempotencyKey> IdempotencyKeys => Set<IdempotencyKey>(); //[cite: 5]
    public DbSet<AdminUser> AdminUsers => Set<AdminUser>(); //[cite: 5]
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();


    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(AppDbContext).Assembly);
        base.OnModelCreating(modelBuilder);
    }

    public override async Task<int> SaveChangesAsync(CancellationToken ct = default)
    {
        // 1. AUTOMATIC AUDIT STAMPS
        // We intercept the ChangeTracker to set timestamps so developers don't have to do it manually
        foreach (var entry in ChangeTracker.Entries<BaseEntity>())
        {
            var now = DateTimeOffset.UtcNow;

            if (entry.State == EntityState.Added)
            {
                entry.Entity.SetCreatedAt(now); // Only set once on creation
            }

            if (entry.State == EntityState.Modified || entry.State == EntityState.Added)
            {
                entry.Entity.SetUpdatedAt(now); // Updated on every change[cite: 7, 8]
            }
        }

        // 2. DOMAIN EVENT DISPATCHING (Approach A)
        // We find every entity in memory that has raised an event (e.g., WalletCreditedEvent)
        var entitiesWithEvents = ChangeTracker
            .Entries<BaseEntity>()
            .Select(e => e.Entity)
            .Where(e => e.DomainEvents.Any())
            .ToList();

        var domainEvents = entitiesWithEvents
            .SelectMany(e => e.DomainEvents)
            .ToList();

        // CRITICAL: Clear events before publishing. 
        // This prevents an infinite loop if a handler triggers another SaveChangesAsync
        entitiesWithEvents.ForEach(e => e.ClearDomainEvents());

        // 3. PUBLISH EVENTS BEFORE SAVE
        // These handlers (like LedgerEntryHandler) will add new records to the ChangeTracker.
        // Because we haven't called base.SaveChangesAsync yet, these are NOT in the database yet.
        foreach (var domainEvent in domainEvents)
        {
            await _publisher.Publish(domainEvent, ct);
        }

        // 4. THE ATOMIC COMMIT
        // EF Core wraps all tracked changes (Wallet balance + Ledger Entry + Timestamps) 
        // into a single SQL Transaction. All succeed, or all roll back.
        return await base.SaveChangesAsync(ct);
    }
}