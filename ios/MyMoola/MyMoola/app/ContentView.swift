import SwiftUI

nonisolated enum AppRoute: Equatable {
    case launch
    case onboarding
    case signup
    case login
    case home
}

nonisolated struct SessionRestorer {
    let load: () throws -> AuthTokens?
    let refresh: (String) async throws -> AuthTokens?
    var save: (AuthTokens) throws -> Void = { _ in }
    let clear: () throws -> Void

    func restore() async -> AppRoute {
        do {
            guard let tokens = try load() else {
                return .onboarding
            }

            // Prefer a usable access token; refresh only when it is missing.
            if !tokens.accessToken.trimmingCharacters(
                in: .whitespacesAndNewlines
            ).isEmpty {
                return .home
            }

            guard
                !tokens.refreshToken.trimmingCharacters(
                    in: .whitespacesAndNewlines
                ).isEmpty,
                let refreshed = try await refresh(tokens.refreshToken)
            else {
                try? clear()
                return .login
            }

            try save(refreshed)
            return .home
        } catch {
            try? clear()
            return .login
        }
    }

    func logout() throws -> AppRoute {
        try clear()
        return .login
    }
}

struct ContentView: View {
    @State private var route = AppRoute.launch
    @State private var page = 0

    private let session = AuthSession()
    private let service: AuthService? = {
        guard let client = try? APIClient.local() else { return nil }
        return AuthService(client: client)
    }()

    var body: some View {
        Group {
            switch route {
            case .launch:
                LaunchView()
            case .onboarding:
                OnboardingView(
                    page: $page,
                    onSignUp: { route = .signup },
                    onLogin: { route = .login }
                )
            case .signup, .login:
                AuthenticationView(
                    initialRoute: route == .signup ? .signup : .login,
                    service: service,
                    session: session,
                    onBack: { route = .onboarding },
                    onAuthenticated: { route = .home }
                )
            case .home:
                HomeView(
                    service: service.map { HomeService(client: $0.client) },
                    authService: service,
                    session: session
                ) {
                    route = try sessionRestorer.logout()
                }
            }
        }
        .task {
            guard route == .launch else { return }
            try? await Task.sleep(for: .milliseconds(600))
            route = await sessionRestorer.restore()
        }
    }

    private var sessionRestorer: SessionRestorer {
        SessionRestorer(
            load: { try session.load() },
            refresh: { refreshToken in
                guard let service else { return nil }
                let response = try await service.refresh(
                    refreshToken: refreshToken
                )
                return AuthTokens(
                    accessToken: response.accessToken,
                    refreshToken: response.refreshToken
                )
            },
            save: { try session.save($0) },
            clear: { try session.clear() }
        )
    }
}

#Preview {
    ContentView()
}
