using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Common.Interfaces;

public interface IWalletRepository
{
    Task AddRangeAsync(IEnumerable<Wallet> wallets, CancellationToken ct = default);
    Task<Wallet?> FindByUserAndCurrencyAsync(Guid userId, Currency currency, CancellationToken ct = default);
}