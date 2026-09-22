import SwiftUI

struct SignUpView: View {
    @Binding var flow: AuthFlowState
    let service: AuthService
    let onBack: () -> Void

    @State private var name = ""
    @State private var phone = ""
    @State private var pin = ""
    @State private var showsPIN = false
    @State private var errors = [String]()

    var body: some View {
        authPage(onBack: goBack) {
            Text("Create Account")
                .font(AppTypography.title)
                .foregroundStyle(Color.brandText)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("Use your name, phone number, and 4-digit PIN.")
                .foregroundStyle(Color.brandMuted)
                .frame(maxWidth: .infinity, alignment: .leading)

            TextField("Name", text: $name)
                .textFieldStyle(AppTextFieldStyle())
                .textContentType(.name)
                .disabled(flow.isSubmitting)

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
            .textContentType(.newPassword)
            .onChange(of: pin) { _, value in
                pin = String(value.filter(\.isNumber).prefix(4))
            }
            .disabled(flow.isSubmitting)

            Button(showsPIN ? "Hide PIN" : "Show PIN") {
                showsPIN.toggle()
            }
            .buttonStyle(SecondaryButtonStyle())
            .disabled(flow.isSubmitting)

            Button(flow.isSubmitting ? "Registering..." : "Register") {
                submit()
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(flow.isSubmitting)

            authErrors(errors)

            Button {
                pin = ""
                flow.showLogin()
            } label: {
                Text("Already have an account? ")
                    .foregroundStyle(Color.brandMuted)
                + Text("Sign in")
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
        errors = AuthValidation.signup(
            name: name,
            phone: phone,
            pin: pin
        ).map(\.rawValue)
        guard errors.isEmpty, let normalizedPhone = phone.normalizedKenyanPhone else {
            return
        }
        guard flow.beginSubmission() else { return }

        Task {
            defer { flow.endSubmission() }
            do {
                _ = try await service.register(
                    fullName: name.trimmingCharacters(
                        in: .whitespacesAndNewlines
                    ),
                    phoneNumber: normalizedPhone,
                    pin: pin
                )
                pin = ""
                flow.showOTP(
                    phoneNumber: normalizedPhone,
                    purpose: .registration
                )
            } catch {
                errors = [authErrorMessage(error)]
            }
        }
    }
}
