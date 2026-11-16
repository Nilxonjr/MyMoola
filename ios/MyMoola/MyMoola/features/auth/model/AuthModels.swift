import Foundation

nonisolated enum OTPPurpose: String, Codable, Sendable {
    case registration = "Registration"
    case login = "Login"
}

nonisolated struct RegisterRequest: Encodable, Sendable {
    let phoneNumber: String
    let pin: String
    let fullName: String
}

nonisolated struct RegisterResponse: Decodable, Sendable {
    let userId: String
    let message: String
}

nonisolated struct LoginRequest: Encodable, Sendable {
    let phoneNumber: String
    let pin: String
}

nonisolated struct LoginInitiatedResponse: Decodable, Sendable {
    let message: String
}

nonisolated struct VerifyOTPRequest: Encodable, Sendable {
    let phoneNumber: String
    let otp: String
    let purpose: OTPPurpose
}

nonisolated struct ResendOTPRequest: Encodable, Sendable {
    let phoneNumber: String
    let purpose: OTPPurpose
}

nonisolated struct ResendOTPResponse: Decodable, Sendable {
    let message: String
}

nonisolated struct RefreshTokenRequest: Encodable, Sendable {
    let refreshToken: String
}

nonisolated struct AuthTokenResponse: Codable, Equatable, Sendable {
    let accessToken: String
    let refreshToken: String
    let tokenType: String
}

nonisolated enum AuthValidationError: String, Equatable, Sendable {
    case nameRequired = "Name is required."
    case invalidPhone = "Phone number must be in format +2547XXXXXXXX."
    case invalidPIN = "PIN must be exactly 4 digits."
    case invalidOTP = "OTP must be exactly 6 digits."
}

nonisolated enum AuthValidation {
    static func signup(
        name: String,
        phone: String,
        pin: String
    ) -> [AuthValidationError] {
        var errors = [AuthValidationError]()

        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errors.append(.nameRequired)
        }
        if phone.normalizedKenyanPhone == nil {
            errors.append(.invalidPhone)
        }
        if !pin.isNumeric(length: 4) {
            errors.append(.invalidPIN)
        }

        return errors
    }

    static func login(phone: String, pin: String) -> [AuthValidationError] {
        var errors = [AuthValidationError]()

        if phone.normalizedKenyanPhone == nil {
            errors.append(.invalidPhone)
        }
        if !pin.isNumeric(length: 4) {
            errors.append(.invalidPIN)
        }

        return errors
    }

    static func otp(_ value: String) -> [AuthValidationError] {
        value.isNumeric(length: 6) ? [] : [.invalidOTP]
    }
}

extension String {
    /// Accepts common local forms and returns the backend's +2547XXXXXXXX format.
    nonisolated var normalizedKenyanPhone: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        let separators = CharacterSet(charactersIn: " -()")
        guard value.unicodeScalars.allSatisfy({ scalar in
            CharacterSet.decimalDigits.contains(scalar)
                || separators.contains(scalar)
                || scalar == "+"
        }) else {
            return nil
        }
        if value.contains("+") {
            guard value.first == "+", value.filter({ $0 == "+" }).count == 1 else {
                return nil
            }
        }

        let digits = value.filter(\.isNumber)
        let normalized: String

        if digits.count == 10, digits.hasPrefix("07") {
            normalized = "+254" + digits.dropFirst()
        } else if digits.count == 9, digits.hasPrefix("7") {
            normalized = "+254" + digits
        } else if digits.count == 12, digits.hasPrefix("2547") {
            normalized = "+" + digits
        } else {
            return nil
        }

        return normalized.count == 13 ? normalized : nil
    }

    fileprivate nonisolated func isNumeric(length: Int) -> Bool {
        count == length && allSatisfy(\.isNumber)
    }
}
