import SwiftUI

nonisolated enum AuthRoute: Equatable {
    case login
    case signup
    case otp(phoneNumber: String, purpose: OTPPurpose)
}

nonisolated struct AuthFlowState: Equatable {
    var route = AuthRoute.signup
    private(set) var isSubmitting = false

    mutating func showLogin() { route = .login }
    mutating func showSignup() { route = .signup }

    mutating func showOTP(phoneNumber: String, purpose: OTPPurpose) {
        route = .otp(phoneNumber: phoneNumber, purpose: purpose)
    }

    mutating func goBack() {
        switch route {
        case .login, .signup:
            break
        case let .otp(_, purpose):
            route = purpose == .registration ? .signup : .login
        }
    }

    mutating func beginSubmission() -> Bool {
        guard !isSubmitting else { return false }
        isSubmitting = true
        return true
    }

    mutating func endSubmission() {
        isSubmitting = false
    }
}

struct AuthenticationView: View {
    @State private var flow = AuthFlowState()

    private let service: AuthService?
    private let session: AuthSession
    private let onBack: () -> Void
    private let onAuthenticated: () -> Void

    init(
        initialRoute: AuthRoute = .signup,
        service: AuthService? = nil,
        session: AuthSession = AuthSession(),
        onBack: @escaping () -> Void = {},
        onAuthenticated: @escaping () -> Void = {}
    ) {
        _flow = State(initialValue: AuthFlowState(route: initialRoute))
        self.service = service ?? (try? AuthService(client: APIClient.local()))
        self.session = session
        self.onBack = onBack
        self.onAuthenticated = onAuthenticated
    }

    var body: some View {
        Group {
            if let service {
                switch flow.route {
                case .login:
                    LoginView(flow: $flow, service: service, onBack: onBack)
                case .signup:
                    SignUpView(flow: $flow, service: service, onBack: onBack)
                case let .otp(phoneNumber, purpose):
                    OTPView(
                        flow: $flow,
                        service: service,
                        session: session,
                        phoneNumber: phoneNumber,
                        purpose: purpose,
                        onAuthenticated: onAuthenticated
                    )
                }
            } else {
                authPage {
                    Text("Authentication unavailable")
                        .font(AppTypography.title)
                    Text("The API address is not configured.")
                        .foregroundStyle(Color.brandMuted)
                }
            }
        }
    }

}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Color.brandAccent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(.white)
            .clipShape(.rect(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.panelBorder)
            }
    }
}

@ViewBuilder
func authPage<Content: View>(
    onBack: (() -> Void)? = nil,
    @ViewBuilder content: @escaping () -> Content
) -> some View {
    ZStack {
        Color.pageBackground.ignoresSafeArea()

        VStack {
            Text("MyMoola")
                .font(.largeTitle.weight(.semibold))
                .foregroundStyle(Color.brandText)
                .padding(.top, 44)

            Spacer(minLength: 32)

            GeometryReader { proxy in
                ScrollView {
                    VStack {
                        Spacer(minLength: 0)

                        VStack(spacing: AppSpacing.medium) {
                            content()
                        }
                        .padding(AppSpacing.large)
                        .frame(maxWidth: .infinity)
                        .background(.white)
                        .clipShape(.rect(cornerRadius: 16))
                        .overlay {
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.panelBorder)
                        }
                    }
                    .frame(minHeight: proxy.size.height)
                }
                .scrollIndicators(.hidden)
            }
            .frame(maxWidth: 520)
            .padding(.horizontal, AppSpacing.large)
        }

        if let onBack {
            VStack {
                HStack {
                    Button(action: onBack) {
                        Image("ic_back")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 24, height: 24)
                            .frame(width: 44, height: 44)
                            .background(Color.pageBackground)
                            .clipShape(.rect(cornerRadius: 12))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.panelBorder)
                            }
                    }
                    .accessibilityLabel("Back")
                    Spacer()
                }
                Spacer()
            }
            .padding(.horizontal, AppSpacing.page)
            .padding(.top, AppSpacing.small)
        }
    }
}

func authErrorMessage(_ error: Error) -> String {
    if let apiError = error as? APIError {
        return apiError.message
    }
    if error is AuthServiceError {
        return "The server returned an invalid session. Try again."
    }
    return "Something went wrong. Try again."
}
