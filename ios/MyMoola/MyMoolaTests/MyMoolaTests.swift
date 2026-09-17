import Foundation
import Testing
@testable import MyMoola

@Suite(.serialized)
struct MyMoolaTests {
    private struct Probe: Decodable {
        let openapi: String
    }

    @Test func getDecodesSuccessfulResponse() async throws {
        let client = makeClient(status: 200, data: Data(#"{"openapi":"3.0.1"}"#.utf8))

        let probe = try await client.get("swagger/v1/swagger.json", as: Probe.self)

        #expect(probe.openapi == "3.0.1")
    }

    @Test func getRejectsErrorStatus() async {
        let client = makeClient(status: 500, data: Data())

        await #expect(throws: APIError.httpStatus(500)) {
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

    @Test func simulatorReachesDockerAPI() async throws {
        let client = try APIClient.local(bundle: .main)

        let probe = try await client.get(
            "swagger/v1/swagger.json",
            as: Probe.self
        )

        #expect(!probe.openapi.isEmpty)
    }

    private func makeClient(status: Int, data: Data) -> APIClient {
        MockURLProtocol.response = HTTPURLResponse(
            url: URL(string: "http://localhost:8080/swagger/v1/swagger.json")!,
            statusCode: status,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )
        MockURLProtocol.data = data

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

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
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
