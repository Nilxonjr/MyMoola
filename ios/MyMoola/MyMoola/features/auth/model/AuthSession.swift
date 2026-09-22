import Foundation

nonisolated struct AuthTokens: Codable, Equatable, Sendable {
    let accessToken: String
    let refreshToken: String
}

nonisolated struct AuthSession: Sendable {
    private static let account = "tokens"
    private let store: KeychainStore

    init(store: KeychainStore = KeychainStore(service: "com.mymoola.auth")) {
        self.store = store
    }

    func load() throws -> AuthTokens? {
        guard let data = try store.read(account: Self.account) else {
            return nil
        }
        return try? JSONDecoder().decode(AuthTokens.self, from: data)
    }

    func save(_ tokens: AuthTokens) throws {
        // One payload keeps rotated access and refresh tokens from different sessions apart.
        let data = try JSONEncoder().encode(tokens)
        try store.save(data, account: Self.account)
    }

    func clear() throws {
        try store.delete(account: Self.account)
    }
}
