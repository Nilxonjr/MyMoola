import SwiftUI

extension Color {
    static let brandAccent = Color(red: 10 / 255, green: 124 / 255, blue: 106 / 255)
    static let brandText = Color(red: 15 / 255, green: 23 / 255, blue: 42 / 255)
    static let brandMuted = Color(red: 100 / 255, green: 116 / 255, blue: 139 / 255)
    static let pageBackground = Color(red: 248 / 255, green: 250 / 255, blue: 252 / 255)
    static let panelBorder = Color(red: 226 / 255, green: 232 / 255, blue: 240 / 255)
}

enum AppSpacing {
    static let small: CGFloat = 8
    static let medium: CGFloat = 12
    static let page: CGFloat = 16
    static let large: CGFloat = 20
}

enum AppTypography {
    static let title = Font.title2.weight(.semibold)
    static let body = Font.body
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Color.brandAccent)
            .clipShape(.rect(cornerRadius: 12))
    }
}

struct AppTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(AppSpacing.medium)
            .background(.white)
            .clipShape(.rect(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.panelBorder)
            }
    }
}
