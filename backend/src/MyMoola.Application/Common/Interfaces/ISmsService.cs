using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MyMoola.Application.Common.Interfaces;

public interface ISmsService
{
    Task SendAsync(string to, string message, CancellationToken ct = default);
}