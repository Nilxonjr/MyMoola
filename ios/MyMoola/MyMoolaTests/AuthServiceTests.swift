import Foundation
import Testing
@testable import MyMoola

@Suite(.serialized)
struct AuthServiceTests {
    @Test func registerUsesExpectedContract() async throws {
        let service = makeService(
            status: 201,
            body: #"{"userId":"user-1","message":"OTP sent"}"#
        )

        let response = try await service.register(
            fullName: "Amina",
            phoneNumber: "+254712345678",
            pin: "1234"
        )

        #expect(response.userId == "user-1")
        #expect(response.message == "OTP sent")
        #expect(AuthMockURLProtocol.request?.url?.path == "/api/auth/register")
        #expect(AuthMockURLProtocol.request?.httpMethod == "POST")
        #expect(
            AuthMockURLProtocol.request?.value(
                forHTTPHeaderField: "Idempotency-Key"
            ) == "fixed-id"
        )
        #expect(requestJSON() == [
            "fullName": "Amina",
            "phoneNumber": "+254712345678",
            "pin": "1234"
        ])
    }

    @Test func loginUsesExpectedContract() async throws {
        let service = makeService(
            status: 200,
            body: #"{"message":"OTP sent"}"#
        )

        let response = try await service.login(
            phoneNumber: "+254712345678",
            pin: "1234"
        )

        #expect(response.message == "OTP sent")
        #expect(AuthMockURLProtocol.request?.url?.path == "/api/auth/login")
        #expect(requestJSON() == [
            "phoneNumber": "+254712345678",
            "pin": "1234"
        ])
    }

    @Test func verifyOTPUsesExpectedContract() async throws {
        let service = makeService(
            status: 200,
            body: tokenBody
        )

        let response = try await service.verifyOTP(
            phoneNumber: "+254712345678",
            otp: "123456",
            purpose: .login
        )

        #expect(response.accessToken == "access")
        #expect(response.refreshToken == "refresh")
        #expect(AuthMockURLProtocol.request?.url?.path == "/api/auth/verify-otp")
        #expect(requestJSON() == [
            "otp": "123456",
            "phoneNumber": "+254712345678",
            "purpose": "Login"
        ])
    }

    @Test func resendOTPUsesExpectedContract() async throws {
        let service = makeService(
            status: 200,
            body: #"{"message":"OTP resent"}"#
        )

        let response = try await service.resendOTP(
            phoneNumber: "+254712345678",
            purpose: .registration
        )

        #expect(response.message == "OTP resent")
        #expect(AuthMockURLProtocol.request?.url?.path == "/api/auth/otp/resend")
        #expect(requestJSON() == [
            "phoneNumber": "+254712345678",
            "purpose": "Registration"
        ])
    }

    @Test func refreshUsesExpectedContract() async throws {
        let service = makeService(status: 200, body: tokenBody)

        let response = try await service.refresh(refreshToken: "old-refresh")

        #expect(response == AuthTokenResponse(
            accessToken: "access",
            refreshToken: "refresh",
            tokenType: "Bearer"
        ))
        #expect(AuthMockURLProtocol.request?.url?.path == "/api/auth/refresh")
        #expect(requestJSON() == ["refreshToken": "old-refresh"])
    }

    @Test func verifyRejectsBlankTokens() async {
        let service = makeService(
            status: 200,
            body: #"{"accessToken":"","refreshToken":"refresh","tokenType":"Bearer"}"#
        )

        await #expect(throws: AuthServiceError.invalidTokens) {
            try await service.verifyOTP(
                phoneNumber: "+254712345678",
                otp: "123456",
                purpose: .login
            )
        }
    }

    @Test func refreshRejectsBlankTokens() async {
        let service = makeService(
            status: 200,
            body: #"{"accessToken":"access","refreshToken":"","tokenType":"Bearer"}"#
        )

        await #expect(throws: AuthServiceError.invalidTokens) {
            try await service.refresh(refreshToken: "old-refresh")
        }
    }

    private var tokenBody: String {
        #"{"accessToken":"access","refreshToken":"refresh","tokenType":"Bearer"}"#
    }

    private func makeService(status: Int, body: String) -> AuthService {
        AuthMockURLProtocol.status = status
        AuthMockURLProtocol.data = Data(body.utf8)
        AuthMockURLProtocol.request = nil

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AuthMockURLProtocol.self]
        let client = APIClient(
            baseURL: URL(string: "http://localhost:8080/")!,
            session: URLSession(configuration: configuration)
        )
        return AuthService(client: client, idempotencyKey: { "fixed-id" })
    }

    private func requestJSON() -> [String: String] {
        guard let data = AuthMockURLProtocol.request?.httpBody,
              let json = try? JSONDecoder().decode(
                  [String: String].self,
                  from: data
              )
        else {
            return [:]
        }
        return json
    }
}

private final class AuthMockURLProtocol: URLProtocol {
    static var status = 200
    static var data = Data()
    static var request: URLRequest?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.request = request
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: Self.status,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
