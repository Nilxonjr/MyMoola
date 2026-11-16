import Foundation
import Testing
@testable import MyMoola

@Suite(.serialized)
struct AuthSessionTests {
    @Test func savesLoadsReplacesAndClearsTokens() throws {
        let store = KeychainStore(
            service: "com.mymoola.tests.\(UUID().uuidString)"
        )
        let session = AuthSession(store: store)
        defer { try? session.clear() }

        try session.save(
            AuthTokens(
                accessToken: "access-1",
                refreshToken: "refresh-1"
            )
        )
        #expect(
            try session.load() == AuthTokens(
                accessToken: "access-1",
                refreshToken: "refresh-1"
            )
        )

        try session.save(
            AuthTokens(
                accessToken: "access-2",
                refreshToken: "refresh-2"
            )
        )
        #expect(
            try session.load() == AuthTokens(
                accessToken: "access-2",
                refreshToken: "refresh-2"
            )
        )

        try session.clear()
        #expect(try session.load() == nil)
    }

    @Test func corruptPayloadLoadsAsNoSession() throws {
        let store = KeychainStore(
            service: "com.mymoola.tests.\(UUID().uuidString)"
        )
        defer { try? store.delete(account: "tokens") }

        try store.save(Data("not-json".utf8), account: "tokens")

        #expect(try AuthSession(store: store).load() == nil)
    }
}
