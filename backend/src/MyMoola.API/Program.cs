using System.Threading.RateLimiting;
using FluentValidation;
using MediatR;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using MyMoola.Application;
using MyMoola.Application.Features.Transactions.OutboxHandlers;
using MyMoola.Application.Common.Behaviours;
using MyMoola.Application.Common.Interfaces;
using MyMoola.API.Middleware;
using MyMoola.Infrastructure.Persistence;
using MyMoola.Infrastructure.Persistence.Repositories;
using MyMoola.Infrastructure.Services;
using System.Text;
using Microsoft.Extensions.Options;
using Polly;
using System.Text.Json.Serialization;
using MyMoola.Application.Interfaces;
using StackExchange.Redis;
using MyMoola.API.Filters;
using MyMoola.Application.Common.Services;
using MyMoola.Infrastructure.Settings;
using MyMoola.Infrastructure.BackgroundJobs;
using MyMoola.Infrastructure.Persistence.Interceptors;
using Polly.Extensions.Http;
using MyMoola.Application.Common.Options;
using Microsoft.AspNetCore.HttpOverrides;
using MyMoola.Application.Features.Crypto.OutboxHandlers;


var builder = WebApplication.CreateBuilder(args);

// ── Controllers ───────────────────────────────────────────────────────────────
builder.Services.AddControllers()
    .AddJsonOptions(options =>
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter()));

// ── API Documentation ─────────────────────────────────────────────────────────
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new()
    {
        Title = "MyMoola API",
        Version = "v1",
        Description = "MyMoola crypto wallet backend API"
    });

    // User JWT scheme
    var userScheme = new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Description = "User JWT — Enter: Bearer {your JWT}",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        Reference = new OpenApiReference
        {
            Type = ReferenceType.SecurityScheme,
            Id = JwtBearerDefaults.AuthenticationScheme
        }
    };

    // Admin JWT scheme
    var adminScheme = new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Description = "Admin JWT — Enter: Bearer {your admin JWT}",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        Reference = new OpenApiReference
        {
            Type = ReferenceType.SecurityScheme,
            Id = "AdminBearer"
        }
    };

    options.AddSecurityDefinition(JwtBearerDefaults.AuthenticationScheme, userScheme);
    options.AddSecurityDefinition("AdminBearer", adminScheme);

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        { userScheme, Array.Empty<string>() }
    });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        { adminScheme, Array.Empty<string>() }
    });

    options.OperationFilter<IdempotencyHeaderOperationFilter>();
});

// ── Database ──────────────────────────────────────────────────────────────────
builder.Services.AddScoped<AuditInterceptor>();

builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseNpgsql(
        builder.Configuration.GetConnectionString("DefaultConnection"),
        sql => sql.MigrationsAssembly("MyMoola.Infrastructure")));

// ── MediatR ───────────────────────────────────────────────────────────────────
builder.Services.AddMediatR(cfg =>
{
    cfg.RegisterServicesFromAssemblies(
        typeof(AppDbContext).Assembly,
        AssemblyReference.Assembly);

    cfg.AddBehavior(typeof(IPipelineBehavior<,>), typeof(ValidationBehaviour<,>));
});

// ── FluentValidation ──────────────────────────────────────────────────────────
builder.Services.AddValidatorsFromAssembly(AssemblyReference.Assembly);

// ── Memory Cache (OTP store) ──────────────────────────────────────────────────
//builder.Services.AddMemoryCache();

// ── Repositories ──────────────────────────────────────────────────────────────
builder.Services.AddScoped<IUserRepository, UserRepository>();
builder.Services.AddScoped<IWalletRepository, WalletRepository>();
builder.Services.AddScoped<ITransactionRepository, TransactionRepository>();
builder.Services.AddScoped<IRefreshTokenRepository, RefreshTokenRepository>();
builder.Services.AddScoped<ILedgerEntryRepository, LedgerEntryRepository>();
builder.Services.AddScoped<ISystemControlRepository, SystemControlRepository>();
builder.Services.AddScoped<IAdminRepository, AdminRepository>();
builder.Services.AddScoped<IAuditLogRepository, AuditLogRepository>();
builder.Services.AddScoped<IExchangeRateRepository, ExchangeRateRepository>();
builder.Services.AddScoped<IOtpCache, RedisOtpCache>();
builder.Services.AddScoped<IOutboxRepository, OutboxRepository>();
builder.Services.AddScoped<IMpesaTransactionRepository, MpesaTransactionRepository>();
builder.Services.AddScoped<IDepositAddressRepository, DepositAddressRepository>();
// ── Unit of Work ──────────────────────────────────────────────────────────────
builder.Services.AddScoped<IUnitOfWork, UnitOfWork>();

// ── Application Services ──────────────────────────────────────────────────────
builder.Services.AddScoped<ITokenService, JwtTokenService>();
builder.Services.AddScoped<ILedgerService, LedgerService>();
builder.Services.AddScoped<IAuditLogService, AuditLogService>();
builder.Services.AddScoped<IAdminTokenService, AdminTokenService>();
builder.Services.AddScoped<ICurrentAdminService, CurrentAdminService>();
builder.Services.AddScoped<ICurrencyExchangeService, CurrencyExchangeService>();
builder.Services.AddScoped<DatabaseSeeder>();
builder.Services.AddScoped<IOutboxService, OutboxService>();
builder.Services.AddHostedService<OutboxProcessor>();
builder.Services.AddHostedService<DepositConfirmationPollerJob>();
builder.Services.AddHostedService<WithdrawalConfirmationPollerJob>();
builder.Services.AddScoped<IExchangeRateQuoteService, ExchangeRateQuoteService>();
builder.Services.AddScoped<IBlockchainService, BlockchainService>();

builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<ICurrentUserService, CurrentUserService>();

builder.Services.AddScoped<IOutboxMessageHandler, StkPushOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, StkCallbackOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, B2COutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, B2CCallbackOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, B2CTimeoutOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, B2BOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, B2BCallbackOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, B2BTimeoutOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, PochiOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, DepositDetectedOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, DepositConfirmedOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, AddressSweepOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, WithdrawalBroadcastOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, WithdrawalConfirmedOutboxHandler>();
builder.Services.AddScoped<IOutboxMessageHandler, WithdrawalFailedOutboxHandler>();

// Idempotency
builder.Services.AddScoped<IIdempotencyContext, HttpIdempotencyContext>();
builder.Services.AddScoped<IIdempotencyService, RedisIdempotencyService>();


builder.Services.AddHttpClient();
builder.Services.AddHttpClient<BlockchainService>();

builder.Services.Configure<HdWalletOptions>(
    builder.Configuration.GetSection(HdWalletOptions.Section));

builder.Services.Configure<AlchemyOptions>(
    builder.Configuration.GetSection(AlchemyOptions.Section));

builder.Services.Configure<MpesaOptions>(
    builder.Configuration.GetSection(MpesaOptions.SectionName));

builder.Services.AddHttpClient<IMpesaService, MpesaService>(
    (sp, client) =>
    {
        var opts = sp.GetRequiredService<IOptions<MpesaOptions>>().Value;
        client.BaseAddress = new Uri(opts.BaseUrl);
        client.Timeout = TimeSpan.FromSeconds(30);
    })
    .AddTransientHttpErrorPolicy(p =>
        p.WaitAndRetryAsync(3, retry =>
            TimeSpan.FromSeconds(Math.Pow(2, retry))));

builder.Services.AddHttpClient<ResendEmailService>(client =>
{
    client.BaseAddress = new Uri("https://api.resend.com/");
    client.DefaultRequestHeaders.Add(
        "Authorization",
        $"Bearer {builder.Configuration["Email:ResendApiKey"]
            ?? throw new InvalidOperationException("Email:ResendApiKey is not configured.")}");
});

builder.Services.AddTransient<IEmailService>(
    sp => sp.GetRequiredService<ResendEmailService>());


builder.Services.Configure<OutboxProcessorOptions>(
    builder.Configuration.GetSection(OutboxProcessorOptions.SectionName));

builder.Services.Configure<ExchangeRateSettings>(
    builder.Configuration.GetSection(ExchangeRateSettings.Section));

builder.Services.Configure<TestingOptions>(
    builder.Configuration.GetSection(TestingOptions.SectionName));

builder.Services.AddHttpClient<BinanceRateFetcher>(client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ExternalApis:Binance:BaseUrl"]!);
    client.Timeout = TimeSpan.FromSeconds(
        builder.Configuration.GetValue<int>("ExternalApis:Binance:TimeoutSeconds"));
});

builder.Services.AddHttpClient<CoinGeckoRateFetcher>(client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ExternalApis:CoinGecko:BaseUrl"]!);
    client.Timeout = TimeSpan.FromSeconds(
        builder.Configuration.GetValue<int>("ExternalApis:CoinGecko:TimeoutSeconds"));
});

builder.Services.AddHostedService<ExchangeRateRefreshJob>();

// ── SMS (Africa's Talking) ────────────────────────────────────────────────────
builder.Services.AddHttpClient<AfricasTalkingSmsService>(client =>
{
    var baseUrl = builder.Configuration["AfricasTalking:BaseUrl"]
        ?? "https://api.africastalking.com/";
    client.BaseAddress = new Uri(baseUrl);
    client.DefaultRequestHeaders.Add("apiKey",
        builder.Configuration["AfricasTalking:ApiKey"]
            ?? throw new InvalidOperationException("AfricasTalking:ApiKey is not configured."));
})
.ConfigurePrimaryHttpMessageHandler(() => new SocketsHttpHandler
{
    EnableMultipleHttp2Connections = false,
    SslOptions = new System.Net.Security.SslClientAuthenticationOptions
    {
        ApplicationProtocols =
        [
            System.Net.Security.SslApplicationProtocol.Http11
        ]
    }
})
.AddTransientHttpErrorPolicy(policy =>
    policy.WaitAndRetryAsync(
        retryCount: 3,
        sleepDurationProvider: attempt => TimeSpan.FromMilliseconds(200 * Math.Pow(2, attempt))));

builder.Services.AddTransient<ISmsService>(
    sp => sp.GetRequiredService<AfricasTalkingSmsService>());
// Redis
builder.Services.AddSingleton<IConnectionMultiplexer>(_ =>
    ConnectionMultiplexer.Connect(
        builder.Configuration["Redis:ConnectionString"]!));


// ── JWT Authentication ────────────────────────────────────────────────────────
var jwtKey = builder.Configuration["Jwt:SecretKey"]
    ?? throw new InvalidOperationException("Jwt:SecretKey is not configured.");
var jwtIssuer = builder.Configuration["Jwt:Issuer"]
    ?? throw new InvalidOperationException("Jwt:Issuer is not configured.");
var jwtAud = builder.Configuration["Jwt:Audience"]
    ?? throw new InvalidOperationException("Jwt:Audience is not configured.");

var adminJwtKey = builder.Configuration["AdminJwt:SecretKey"]
    ?? throw new InvalidOperationException("AdminJwt:SecretKey is not configured.");
var adminJwtIssuer = builder.Configuration["AdminJwt:Issuer"]
    ?? throw new InvalidOperationException("AdminJwt:Issuer is not configured.");
var adminJwtAud = builder.Configuration["AdminJwt:Audience"]
    ?? throw new InvalidOperationException("AdminJwt:Audience is not configured.");

builder.Services
    .AddAuthentication(options =>
    {
        options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
        options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
    })
    .AddJwtBearer(options =>
    {
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(jwtKey)),
            ValidateIssuer = true,
            ValidIssuer = jwtIssuer,
            ValidateAudience = true,
            ValidAudience = jwtAud,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromSeconds(30),
            NameClaimType = "sub"
        };
        options.Events = new JwtBearerEvents
        {
            OnChallenge = ctx =>
            {
                ctx.HandleResponse();
                ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
                ctx.Response.ContentType = "application/problem+json";
                return ctx.Response.WriteAsync(
                    """{"title":"Unauthorized","status":401,"detail":"A valid Bearer token is required."}""");
            },
            OnForbidden = ctx =>
            {
                ctx.Response.StatusCode = StatusCodes.Status403Forbidden;
                ctx.Response.ContentType = "application/problem+json";
                return ctx.Response.WriteAsync(
                    """{"title":"Forbidden","status":403,"detail":"You do not have permission to access this resource."}""");
            }
        };
    })
    .AddJwtBearer("AdminBearer", options =>
    {
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(adminJwtKey)),
            ValidateIssuer = true,
            ValidIssuer = adminJwtIssuer,
            ValidateAudience = true,
            ValidAudience = adminJwtAud,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromSeconds(30),
            NameClaimType = "sub"
        };
        options.Events = new JwtBearerEvents
        {
            OnChallenge = ctx =>
            {
                ctx.HandleResponse();
                ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
                ctx.Response.ContentType = "application/problem+json";
                return ctx.Response.WriteAsync(
                    """{"title":"Unauthorized","status":401,"detail":"A valid admin Bearer token is required."}""");
            },
            OnForbidden = ctx =>
            {
                ctx.Response.StatusCode = StatusCodes.Status403Forbidden;
                ctx.Response.ContentType = "application/problem+json";
                return ctx.Response.WriteAsync(
                    """{"title":"Forbidden","status":403,"detail":"You do not have permission to access this resource."}""");
            }
        };
    });

builder.Services.AddAuthorization();

// ── Rate Limiting ─────────────────────────────────────────────────────────────
builder.Services.AddRateLimiter(limiter =>
{
    // ── Auth Endpoints (IP-based) ─────────────────────────────────────────────

    limiter.AddPolicy("register", context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 5,
                Window = TimeSpan.FromMinutes(15),
                SegmentsPerWindow = 3,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 0
            }));

    limiter.AddPolicy("verify-otp", context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 5,
                Window = TimeSpan.FromMinutes(15),
                SegmentsPerWindow = 3,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 0
            }));

    limiter.AddPolicy("login", context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 10,
                Window = TimeSpan.FromMinutes(15),
                SegmentsPerWindow = 3,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 0
            }));

    // Resend OTP — strict per IP, prevents SMS bombing
    limiter.AddPolicy("resend-otp", context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 3,
                Window = TimeSpan.FromMinutes(15),
                SegmentsPerWindow = 3,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 0,
            }));

    // ── Transaction Endpoints (user ID-based) ─────────────────────────────────
    // Keyed by authenticated user ID from JWT sub claim.
    // Unauthenticated requests share a single "unauthenticated" bucket which
    // throttles immediately — [Authorize] should prevent this case entirely
    // but the shared key surfaces misconfiguration fast.

    limiter.AddPolicy("transactions", context =>
    {
        var userId = context.User.FindFirst("sub")?.Value;
        var key = string.IsNullOrWhiteSpace(userId)
            ? "unauthenticated"
            : $"user:{userId}";

        return RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: key,
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 1,
                Window = TimeSpan.FromSeconds(30),
                SegmentsPerWindow = 3,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 0
            });
    });

    // ── Admin Auth Endpoints (IP-based) ───────────────────────────────────────
    // Strict limit on admin login — high value target, brute force risk

    limiter.AddPolicy("admin-login", context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 5,
                Window = TimeSpan.FromMinutes(15),
                SegmentsPerWindow = 3,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 0
            }));

    // ── Admin Management Endpoints (admin ID-based) ───────────────────────────
    // Generic limiter for all authenticated admin actions.
    // Same unauthenticated shared key pattern as transactions.

    limiter.AddPolicy("admin-management", context =>
    {
        var adminId = context.User.FindFirst("sub")?.Value;
        var key = string.IsNullOrWhiteSpace(adminId)
            ? "unauthenticated"
            : $"admin:{adminId}";

        return RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: key,
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 60,
                Window = TimeSpan.FromMinutes(1),
                SegmentsPerWindow = 3,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 0
            });
    });

    limiter.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    limiter.OnRejected = async (ctx, token) =>
    {
        ctx.HttpContext.Response.ContentType = "application/problem+json";
        await ctx.HttpContext.Response.WriteAsync("""
            {
                "title": "Too Many Requests",
                "status": 429,
                "detail": "You have made too many requests. Please try again later."
            }
            """, token);
    };
});
// ── Build ─────────────────────────────────────────────────────────────────────
var app = builder.Build();

// ── Middleware Pipeline ───────────────────────────────────────────────────────
app.UseMiddleware<ExceptionHandlingMiddleware>();
app.UseMiddleware<RequestLoggingMiddleware>();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI(options =>
    {
        options.SwaggerEndpoint("/swagger/v1/swagger.json", "MyMoola API v1");
        options.RoutePrefix = string.Empty;
    });
}

app.UseHttpsRedirection();
app.UseAuthentication();
app.UseAuthorization();
app.UseForwardedHeaders(new ForwardedHeadersOptions
{
    ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto
});
app.UseRateLimiter();
app.UseMiddleware<IdempotencyMiddleware>();
app.UseMiddleware<AlchemyWebhookMiddleware>();
app.MapControllers();
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.MigrateAsync();
}
await using (var scope = app.Services.CreateAsyncScope())
{
    var seeder = scope.ServiceProvider.GetRequiredService<DatabaseSeeder>();
    await seeder.SeedAsync();
}
app.Run();

