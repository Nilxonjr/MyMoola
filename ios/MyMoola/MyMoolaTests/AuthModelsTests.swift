import Testing
@testable import MyMoola

struct AuthModelsTests {
    @Test(arguments: [
        ("0712345678", "+254712345678"),
        ("712345678", "+254712345678"),
        ("254712345678", "+254712345678"),
        ("+254712345678", "+254712345678"),
        ("0712 345-678", "+254712345678")
    ])
    func normalizesKenyanPhone(input: String, expected: String) {
        #expect(input.normalizedKenyanPhone == expected)
    }

    @Test(arguments: [
        "",
        "071234567",
        "+254812345678",
        "+2547123456789",
        "hello0712345678",
        "07+12345678",
        "hello"
    ])
    func rejectsInvalidKenyanPhone(input: String) {
        #expect(input.normalizedKenyanPhone == nil)
    }

    @Test func validatesSignupFields() {
        #expect(
            AuthValidation.signup(
                name: " ",
                phone: "0712345678",
                pin: "1234"
            ) == [.nameRequired]
        )
        #expect(
            AuthValidation.signup(
                name: "Amina",
                phone: "bad",
                pin: "12"
            ) == [.invalidPhone, .invalidPIN]
        )
        #expect(
            AuthValidation.signup(
                name: " Amina ",
                phone: "0712345678",
                pin: "1234"
            ).isEmpty
        )
    }

    @Test func rejectsNonNumericPIN() {
        #expect(
            AuthValidation.login(
                phone: "0712345678",
                pin: "12ab"
            ) == [.invalidPIN]
        )
    }

    @Test func validatesLoginAndOTP() {
        #expect(
            AuthValidation.login(
                phone: "bad",
                pin: "1"
            ) == [.invalidPhone, .invalidPIN]
        )
        #expect(AuthValidation.otp("12345") == [.invalidOTP])
        #expect(AuthValidation.otp("12345a") == [.invalidOTP])
        #expect(AuthValidation.otp("123456").isEmpty)
    }
}
