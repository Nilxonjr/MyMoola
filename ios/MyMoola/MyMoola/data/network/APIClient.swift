import Foundation

nonisolated enum APIError: Error, Equatable {
    case invalidURL
    case encoding
    case transport
    case invalidResponse
    case httpStatus(Int, String)
    case decoding

    var message: String {
        switch self {
        case .invalidURL:
            "Invalid API URL."
        case .encoding:
            "Could not prepare request."
        case .transport:
            "Network error. Try again."
        case .invalidResponse:
            "Invalid server response."
        case let .httpStatus(_, message):
            message
        case .decoding:
            "Could not read server response."
        }
    }
}

nonisolated struct APIClient {
    let baseURL: URL
    var session: URLSession = .shared

    func get<Response: Decodable>(
        _ path: String,
        headers: [String: String] = [:],
        as type: Response.Type
    ) async throws -> Response {
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw APIError.invalidURL
        }

        var request = URLRequest(url: url)
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        let data: Data
        let response: URLResponse

        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.transport
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        guard 200..<300 ~= httpResponse.statusCode else {
            throw APIError.httpStatus(
                httpResponse.statusCode,
                Self.errorMessage(from: data, statusCode: httpResponse.statusCode)
            )
        }

        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }

    func send<Body: Encodable, Response: Decodable>(
        _ body: Body,
        path: String,
        method: String = "POST",
        headers: [String: String] = [:],
        expectedStatus: Int = 200,
        as type: Response.Type
    ) async throws -> Response {
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw APIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        do {
            request.httpBody = try JSONEncoder().encode(body)
        } catch {
            throw APIError.encoding
        }

        let data: Data
        let response: URLResponse

        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.transport
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        guard httpResponse.statusCode == expectedStatus else {
            throw APIError.httpStatus(
                httpResponse.statusCode,
                Self.errorMessage(from: data, statusCode: httpResponse.statusCode)
            )
        }

        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }

    private static func errorMessage(from data: Data, statusCode: Int) -> String {
        if let problem = try? JSONDecoder().decode(ProblemDetails.self, from: data) {
            if let detail = problem.detail, !detail.isEmpty {
                return detail
            }
            if let title = problem.title, !title.isEmpty {
                return title
            }
        }

        return "Request failed with status \(statusCode)."
    }
}

private nonisolated struct ProblemDetails: Decodable {
    let detail: String?
    let title: String?
}

extension APIClient {
    nonisolated static func local(bundle: Bundle = .main) throws -> APIClient {
        guard
            let value = bundle.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
            let url = URL(string: value)
        else {
            throw APIError.invalidURL
        }

        return APIClient(baseURL: url)
    }
}
