using MyMoola.Application.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class LedgerEntryRepository(AppDbContext db) : ILedgerEntryRepository
{
    public void Add(LedgerEntry entry) => db.LedgerEntries.Add(entry);
}