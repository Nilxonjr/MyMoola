import SwiftUI

struct OTPView: View {
    @Binding var flow: AuthFlowState
    let service: AuthService
    let session: AuthSession
    let phoneNumber: String
    let purpose: OTPPurpose
    let onAuthenticated: () -> Void

    @State private var otp = ""
    @State private var errors = [String]()
    @State private var successMessage: String?
    @State private var secondsRemaining = 30
    @State private var countdownID = 0

    var body: some View {
        authPage(onBack: goBack) {
            Text("Verify Phone Number")
                .font(AppTypography.title)
                .foregroundStyle(Color.brandText)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("Enter the 6-digit code sent to \(maskedPhoneNumber)")
                .foregroundStyle(Color.brandMuted)
                .frame(maxWidth: .infinity, alignment: .leading)

            SecureField("6-digit OTP", text: $otp)
                .textFieldStyle(AppTextFieldStyle())
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .onChange(of: otp) { _, value in
                    otp = String(value.filter(\.isNumber).prefix(6))
                }
                .disabled(flow.isSubmitting)

            Text(countdownText)
                .font(.footnote)
                .foregroundStyle(Color.brandMuted)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button(flow.isSubmitting ? "Please wait..." : "Resend Code") {
                resend()
            }
            .buttonStyle(SecondaryButtonStyle())
            .disabled(secondsRemaining > 0 || flow.isSubmitting)

            Button(flow.isSubmitting ? "Verifying..." : "Verify") {
                verify()
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(flow.isSubmitting)

            authErrors(errors)

            if let successMessage {
                Text(successMessage)
                    .font(.footnote)
                    .foregroundStyle(.green)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityLabel("Success: \(successMessage)")
            }
        }
        .task(id: countdownID) {
            await runCountdown()
        }
    }

    private func goBack() {
        guard !flow.isSubmitting else { return }
        otp = ""
        flow.goBack()
    }

    private var maskedPhoneNumber: String {
        guard phoneNumber.count > 7 else { return phoneNumber }
        return phoneNumber.prefix(5)
            + String(repeating: "*", count: phoneNumber.count - 7)
            + phoneNumber.suffix(2)
    }

    private var countdownText: String {
        secondsRemaining > 0
            ? "Resend code in 00:\(String(format: "%02d", secondsRemaining))"
            : "You can resend the code now."
    }

    private func runCountdown() async {
        for second in stride(from: 30, through: 1, by: -1) {
            guard !Task.isCancelled else { return }
            secondsRemaining = second
            try? await Task.sleep(for: .seconds(1))
        }
        guard !Task.isCancelled else { return }
        secondsRemaining = 0
    }

    private func resend() {
        guard secondsRemaining == 0, flow.beginSubmission() else { return }
        errors = []
        successMessage = nil

        Task {
            defer { flow.endSubmission() }
            do {
                let response = try await service.resendOTP(
                    phoneNumber: phoneNumber,
                    purpose: purpose
                )
                successMessage = response.message
                countdownID += 1
            } catch {
                errors = [authErrorMessage(error)]
            }
        }
    }

    private func verify() {
        errors = AuthValidation.otp(otp).map(\.rawValue)
        guard errors.isEmpty, flow.beginSubmission() else { return }
        successMessage = nil

        Task {
            defer { flow.endSubmission() }
            do {
                let response = try await service.verifyOTP(
                    phoneNumber: phoneNumber,
                    otp: otp,
                    purpose: purpose
                )
                try session.save(
                    AuthTokens(
                        accessToken: response.accessToken,
                        refreshToken: response.refreshToken
                    )
                )
                otp = ""
                onAuthenticated()
            } catch {
                errors = [authErrorMessage(error)]
            }
        }
    }
}
