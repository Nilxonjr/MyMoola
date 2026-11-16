import SwiftUI

struct LoginView: View {
    @Binding var flow: AuthFlowState
    let service: AuthService
    let onBack: () -> Void

    @State private var phone = ""
    @State private var pin = ""
    @State private var showsPIN = false
    @State private var errors = [String]()

    var body: some View {
        authPage(onBack: goBack) {
            Text("Login")
                .font(AppTypography.title)
                .foregroundStyle(Color.brandText)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("Sign in with your phone number and 4-digit PIN.")
                .foregroundStyle(Color.brandMuted)
                .frame(maxWidth: .infinity, alignment: .leading)

            TextField("Phone Number", text: $phone)
                .textFieldStyle(AppTextFieldStyle())
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .disabled(flow.isSubmitting)

            Group {
                if showsPIN {
                    TextField("4-digit PIN", text: $pin)
                } else {
                    SecureField("4-digit PIN", text: $pin)
                }
            }
            .textFieldStyle(AppTextFieldStyle())
            .keyboardType(.numberPad)
            .textContentType(.password)
            .onChange(of: pin) { _, value in
                pin = String(value.filter(\.isNumber).prefix(4))
            }
            .disabled(flow.isSubmitting)

            Button(showsPIN ? "Hide PIN" : "Show PIN") {
                showsPIN.toggle()
            }
            .buttonStyle(SecondaryButtonStyle())
            .disabled(flow.isSubmitting)

            Button(flow.isSubmitting ? "Logging in..." : "Login") {
                submit()
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(flow.isSubmitting)

            authErrors(errors)

            Button {
                pin = ""
                flow.showSignup()
            } label: {
                Text("Need an account? ")
                    .foregroundStyle(Color.brandMuted)
                + Text("Register")
                    .foregroundStyle(Color.brandAccent)
                    .bold()
            }
            .disabled(flow.isSubmitting)
        }
    }

    private func goBack() {
        pin = ""
        onBack()
    }

    private func submit() {
        errors = AuthValidation.login(phone: phone, pin: pin).map(\.rawValue)
        guard errors.isEmpty, let normalizedPhone = phone.normalizedKenyanPhone else {
            return
        }
        guard flow.beginSubmission() else { return }

        Task {
            defer { flow.endSubmission() }
            do {
                _ = try await service.login(
                    phoneNumber: normalizedPhone,
                    pin: pin
                )
                pin = ""
                flow.showOTP(phoneNumber: normalizedPhone, purpose: .login)
            } catch {
                errors = [authErrorMessage(error)]
            }
        }
    }
}

@ViewBuilder
func authErrors(_ errors: [String]) -> some View {
    ForEach(errors, id: \.self) { error in
        Text("• \(error)")
            .font(.footnote)
            .foregroundStyle(.red)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityLabel("Error: \(error)")
    }
}
