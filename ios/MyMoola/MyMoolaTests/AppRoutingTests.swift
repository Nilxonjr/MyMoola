import Testing
@testable import MyMoola

struct AppRoutingTests {
    @Test func accessTokenRoutesHomeWithoutRefresh() async {
        let restorer = SessionRestorer(
            load: {
                AuthTokens(accessToken: "access", refreshToken: "refresh")
            },
            refresh: { _ in
                Issue.record("Refresh should not run")
                return nil
            },
            clear: {}
        )

        #expect(await restorer.restore() == .home)
    }

    @Test func refreshOnlySuccessSavesTokensAndRoutesHome() async {
        let saved = TokenCapture()
        let restorer = SessionRestorer(
            load: {
                AuthTokens(accessToken: "", refreshToken: "refresh")
            },
            refresh: { _ in
                AuthTokens(accessToken: "new", refreshToken: "rotated")
            },
            save: { saved.tokens = $0 },
            clear: {}
        )

        #expect(await restorer.restore() == .home)
        #expect(
            saved.tokens == AuthTokens(
                accessToken: "new",
                refreshToken: "rotated"
            )
        )
    }

    @Test func refreshFailureClearsAndRoutesLogin() async {
        let cleared = Flag()
        let restorer = SessionRestorer(
            load: {
                AuthTokens(accessToken: "", refreshToken: "stale")
            },
            refresh: { _ in nil },
            clear: { cleared.value = true }
        )

        #expect(await restorer.restore() == .login)
        #expect(cleared.value)
    }

    @Test func noSessionRoutesOnboarding() async {
        let restorer = SessionRestorer(
            load: { nil },
            refresh: { _ in
                Issue.record("Refresh should not run")
                return nil
            },
            clear: {}
        )

        #expect(await restorer.restore() == .onboarding)
    }

    @Test func logoutClearsAndRoutesLogin() throws {
        let cleared = Flag()
        let restorer = SessionRestorer(
            load: { nil },
            refresh: { _ in nil },
            clear: { cleared.value = true }
        )

        #expect(try restorer.logout() == .login)
        #expect(cleared.value)
    }
}

private final class Flag: @unchecked Sendable {
    var value = false
}

private final class TokenCapture: @unchecked Sendable {
    var tokens: AuthTokens?
}
