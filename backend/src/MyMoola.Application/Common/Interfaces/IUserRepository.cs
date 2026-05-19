using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface IUserRepository
{
    Task<User?> FindByPhoneAsync(string phoneNumber, CancellationToken ct = default);
    Task<User?> FindByIdAsync(Guid id, CancellationToken ct = default);
    Task<bool> ExistsByPhoneAsync(string phoneNumber, CancellationToken ct = default);
    Task AddAsync(User user, CancellationToken ct = default);
}