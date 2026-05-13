using MyMoola.Domain.Entities;

namespace MyMoola.Application.Interfaces;

public interface ILedgerEntryRepository
{
    void Add(LedgerEntry entry);
}