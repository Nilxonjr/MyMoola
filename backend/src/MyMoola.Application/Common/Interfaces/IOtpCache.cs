using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MyMoola.Application.Common.Interfaces;

public interface IOtpCache
{
    Task SetAsync(string phoneNumber, string otp, TimeSpan ttl, CancellationToken ct = default);
    Task<string?> GetAsync(string phoneNumber, CancellationToken ct = default);
    Task RemoveAsync(string phoneNumber, CancellationToken ct = default);
}