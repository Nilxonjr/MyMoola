import SwiftUI

struct AuthenticationPlaceholderView: View {
    var body: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            VStack(spacing: AppSpacing.page) {
                Image(systemName: "person.crop.circle.badge.checkmark")
                    .font(.system(size: 52))
                    .foregroundStyle(Color.brandAccent)
                    .accessibilityHidden(true)
                Text("Authentication")
                    .font(AppTypography.title)
                    .foregroundStyle(Color.brandText)
                Text("Sign up and login arrive in next step.")
                    .foregroundStyle(Color.brandMuted)
            }
            .padding(AppSpacing.page)
        }
    }
}
