import Testing
@testable import MyMoola

struct AuthFlowStateTests {
    @Test func authenticationStartsOnRequestedRoute() {
        #expect(AuthFlowState(route: .signup).route == .signup)
        #expect(AuthFlowState(route: .login).route == .login)
    }

    @Test func signupAndLoginLeadToCorrectOTPPurpose() {
        var state = AuthFlowState()

        state.showSignup()
        state.showOTP(
            phoneNumber: "+254712345678",
            purpose: .registration
        )
        #expect(
            state.route == .otp(
                phoneNumber: "+254712345678",
                purpose: .registration
            )
        )

        state.showLogin()
        state.showOTP(
            phoneNumber: "+254712345678",
            purpose: .login
        )
        #expect(
            state.route == .otp(
                phoneNumber: "+254712345678",
                purpose: .login
            )
        )
    }

    @Test func submissionGuardPreventsDuplicateWork() {
        var state = AuthFlowState()

        let firstSubmission = state.beginSubmission()
        let duplicateSubmission = state.beginSubmission()
        #expect(firstSubmission)
        #expect(!duplicateSubmission)
        state.endSubmission()
        let submissionAfterCompletion = state.beginSubmission()
        #expect(submissionAfterCompletion)
    }

    @Test func backFromOTPReturnsToItsOrigin() {
        var state = AuthFlowState()

        state.showOTP(
            phoneNumber: "+254712345678",
            purpose: .registration
        )
        state.goBack()
        #expect(state.route == .signup)

        state.showOTP(
            phoneNumber: "+254712345678",
            purpose: .login
        )
        state.goBack()
        #expect(state.route == .login)
    }
}
