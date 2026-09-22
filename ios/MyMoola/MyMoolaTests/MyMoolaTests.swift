import Foundation
import Testing
@testable import MyMoola

@Suite(.serialized)
struct MyMoolaTests {
    private struct Probe: Decodable {
        let openapi: String
    }

    private struct EchoRequest: Encodable {
        let value: String
    }

    private struct EchoResponse: Decodable {
        let accepted: Bool
    }

    private struct ThrowingRequest: Encodable {
        func encode(to encoder: Encoder) throws {
            throw EncodingError.invalidValue(
                "secret",
                .init(codingPath: [], debugDescription: "Expected test failure")
            )
        }
    }

    @Test func getDecodesSuccessfulResponse() async throws {
        let client = makeClient(status: 200, data: Data(#"{"openapi":"3.0.1"}"#.utf8))

        let probe = try await client.get("swagger/v1/swagger.json", as: Probe.self)

        #expect(probe.openapi == "3.0.1")
    }

    @Test func getRejectsErrorStatus() async {
        let client = makeClient(status: 500, data: Data())

        await #expect(throws: APIError.httpStatus(500, "Request failed with status 500.")) {
            try await client.get("swagger/v1/swagger.json", as: Probe.self)
        }
    }

    @Test func getRejectsMalformedJSON() async {
        let client = makeClient(status: 200, data: Data("not json".utf8))

        await #expect(throws: APIError.decoding) {
            try await client.get("swagger/v1/swagger.json", as: Probe.self)
        }
    }

    @Test func localClientUsesConfiguredBaseURL() throws {
        let client = try APIClient.local(bundle: .main)

        #expect(client.baseURL == URL(string: "http://localhost:8080/"))
    }

    @Test func sendEncodesJSONHeadersAndResponse() async throws {
        let client = makeClient(
            status: 201,
            data: Data(#"{"accepted":true}"#.utf8)
        )

        let result = try await client.send(
            EchoRequest(value: "safe"),
            path: "api/test",
            method: "POST",
            headers: ["Idempotency-Key": "request-id"],
            expectedStatus: 201,
            as: EchoResponse.self
        )

        #expect(result.accepted)
        #expect(MockURLProtocol.lastRequest?.httpMethod == "POST")
        #expect(
            MockURLProtocol.lastRequest?.value(
                forHTTPHeaderField: "Content-Type"
            ) == "application/json"
        )
        #expect(
            MockURLProtocol.lastRequest?.value(
                forHTTPHeaderField: "Idempotency-Key"
            ) == "request-id"
        )
        #expect(MockURLProtocol.lastRequest?.url?.path == "/api/test")
        #expect(
            MockURLProtocol.lastRequest?.httpBody
                == Data(#"{"value":"safe"}"#.utf8)
        )
    }

    @Test func sendExtractsProblemDetail() async {
        let client = makeClient(
            status: 409,
            data: Data(
                #"{"title":"Conflict","detail":"Phone already registered."}"#.utf8
            )
        )

        await #expect(
            throws: APIError.httpStatus(
                409,
                "Phone already registered."
            )
        ) {
            try await client.send(
                EchoRequest(value: "safe"),
                path: "api/test",
                as: EchoResponse.self
            )
        }
    }

    @Test func sendFallsBackToProblemTitle() async {
        let client = makeClient(
            status: 400,
            data: Data(#"{"title":"Invalid request"}"#.utf8)
        )

        await #expect(throws: APIError.httpStatus(400, "Invalid request")) {
            try await client.send(
                EchoRequest(value: "safe"),
                path: "api/test",
                as: EchoResponse.self
            )
        }
    }

    @Test func sendFallsBackToStatusMessageForEmptyErrorBody() async {
        let client = makeClient(status: 429, data: Data())

        await #expect(
            throws: APIError.httpStatus(
                429,
                "Request failed with status 429."
            )
        ) {
            try await client.send(
                EchoRequest(value: "safe"),
                path: "api/test",
                as: EchoResponse.self
            )
        }
    }

    @Test func sendRejectsUnencodableBody() async {
        let client = makeClient(status: 200, data: Data())

        await #expect(throws: APIError.encoding) {
            try await client.send(
                ThrowingRequest(),
                path: "api/test",
                as: EchoResponse.self
            )
        }
    }

    @Test func sendRejectsMalformedSuccessJSON() async {
        let client = makeClient(status: 200, data: Data("not json".utf8))

        await #expect(throws: APIError.decoding) {
            try await client.send(
                EchoRequest(value: "safe"),
                path: "api/test",
                as: EchoResponse.self
            )
        }
    }

    @Test func sendRejectsInvalidURL() async {
        let client = makeClient(status: 200, data: Data())

        await #expect(throws: APIError.invalidURL) {
            try await client.send(
                EchoRequest(value: "safe"),
                path: "\0",
                as: EchoResponse.self
            )
        }
    }

    @Test func sendMapsTransportFailure() async {
        let client = makeClient(
            status: 200,
            data: Data(),
            error: URLError(.notConnectedToInternet)
        )

        await #expect(throws: APIError.transport) {
            try await client.send(
                EchoRequest(value: "safe"),
                path: "api/test",
                as: EchoResponse.self
            )
        }
    }

    @Test func errorMessageDoesNotExposeRequestBody() {
        #expect(
            APIError.httpStatus(401, "Unauthorized").message
                == "Unauthorized"
        )
        #expect(APIError.transport.message == "Network error. Try again.")
    }

    @Test func simulatorReachesDockerAPI() async throws {
        let client = try APIClient.local(bundle: .main)

        let probe = try await client.get(
            "swagger/v1/swagger.json",
            as: Probe.self
        )

        #expect(!probe.openapi.isEmpty)
    }

    private func makeClient(
        status: Int,
        data: Data,
        error: Error? = nil
    ) -> APIClient {
        MockURLProtocol.response = HTTPURLResponse(
            url: URL(string: "http://localhost:8080/swagger/v1/swagger.json")!,
            statusCode: status,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )
        MockURLProtocol.data = data
        MockURLProtocol.error = error
        MockURLProtocol.lastRequest = nil

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        return APIClient(
            baseURL: URL(string: "http://localhost:8080/")!,
            session: URLSession(configuration: configuration)
        )
    }
}

private final class MockURLProtocol: URLProtocol {
    static var response: HTTPURLResponse?
    static var data = Data()
    static var error: Error?
    static var lastRequest: URLRequest?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lastRequest = request

        if let error = Self.error {
            client?.urlProtocol(self, didFailWithError: error)
            return
        }

        guard let response = Self.response else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
