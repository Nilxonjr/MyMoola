import SwiftUI

struct ContentView: View {
    private enum Route {
        case launch, onboarding, authentication
    }

    @State private var route = Route.launch
    @State private var page = 0

    var body: some View {
        Group {
            switch route {
            case .launch:
                LaunchView()
            case .onboarding:
                OnboardingView(
                    page: $page,
                    onSkip: { route = .authentication },
                    onFinish: { route = .authentication }
                )
            case .authentication:
                AuthenticationPlaceholderView()
            }
        }
        .task {
            guard route == .launch else { return }
            try? await Task.sleep(for: .milliseconds(600))
            route = .onboarding
        }
    }
}

#Preview {
    ContentView()
}
