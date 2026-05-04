using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MyMoola.Application.Common.Interfaces;

public interface ITokenService
{
    string GenerateToken(Guid userId, string phoneNumber, string fullName);
}