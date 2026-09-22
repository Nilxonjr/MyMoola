import Foundation

nonisolated enum AuthServiceError: Error, Equatable {
    case invalidTokens
}

nonisolated struct AuthService: Sendable {
    let client: APIClient
    var idempotencyKey: @Sendable () -> String = { UUID().uuidString }

    func register(
        fullName: String,
        phoneNumber: String,
        pin: String
    ) async throws -> RegisterResponse {
        // Retrying registration must not create the same account twice.
        try await client.send(
            RegisterRequest(
                phoneNumber: phoneNumber,
                pin: pin,
                fullName: fullName
            ),
            path: "api/auth/register",
            headers: ["Idempotency-Key": idempotencyKey()],
            expectedStatus: 201,
            as: RegisterResponse.self
        )
    }

    func login(
        phoneNumber: String,
        pin: String
    ) async throws -> LoginInitiatedResponse {
        try await client.send(
            LoginRequest(phoneNumber: phoneNumber, pin: pin),
            path: "api/auth/login",
            as: LoginInitiatedResponse.self
        )
    }

    func verifyOTP(
        phoneNumber: String,
        otp: String,
        purpose: OTPPurpose
    ) async throws -> AuthTokenResponse {
        let response = try await client.send(
            VerifyOTPRequest(
                phoneNumber: phoneNumber,
                otp: otp,
                purpose: purpose
            ),
            path: "api/auth/verify-otp",
            as: AuthTokenResponse.self
        )
        return try validated(response)
    }

    func resendOTP(
        phoneNumber: String,
        purpose: OTPPurpose
    ) async throws -> ResendOTPResponse {
        try await client.send(
            ResendOTPRequest(
                phoneNumber: phoneNumber,
                purpose: purpose
            ),
            path: "api/auth/otp/resend",
            as: ResendOTPResponse.self
        )
    }

    func refresh(refreshToken: String) async throws -> AuthTokenResponse {
        let response = try await client.send(
            RefreshTokenRequest(refreshToken: refreshToken),
            path: "api/auth/refresh",
            as: AuthTokenResponse.self
        )
        return try validated(response)
    }

    private func validated(_ response: AuthTokenResponse) throws -> AuthTokenResponse {
        guard
            !response.accessToken.trimmingCharacters(
                in: .whitespacesAndNewlines
            ).isEmpty,
            !response.refreshToken.trimmingCharacters(
                in: .whitespacesAndNewlines
            ).isEmpty
        else {
            throw AuthServiceError.invalidTokens
        }

        return response
    }
}
