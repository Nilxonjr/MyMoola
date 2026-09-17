import Foundation

nonisolated enum APIError: Error, Equatable {
    case invalidURL
    case transport
    case invalidResponse
    case httpStatus(Int)
    case decoding
}

nonisolated struct APIClient {
    let baseURL: URL
    var session: URLSession = .shared

    func get<Response: Decodable>(
        _ path: String,
        as type: Response.Type
    ) async throws -> Response {
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw APIError.invalidURL
        }

        let data: Data
        let response: URLResponse

        do {
            (data, response) = try await session.data(from: url)
        } catch {
            throw APIError.transport
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        guard 200..<300 ~= httpResponse.statusCode else {
            throw APIError.httpStatus(httpResponse.statusCode)
        }

        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }
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
