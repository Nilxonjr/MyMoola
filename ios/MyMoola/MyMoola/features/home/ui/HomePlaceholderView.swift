import SwiftUI

struct HomePlaceholderView: View {
    let onLogout: () throws -> Void

    @State private var errorMessage: String?

    var body: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            VStack(spacing: AppSpacing.page) {
                Image("MyMoola")
                    .resizable()
                    .scaledToFit()
                    .frame(height: 72)
                    .accessibilityHidden(true)
                Text("You're signed in")
                    .font(AppTypography.title)
                    .foregroundStyle(Color.brandText)
                Text("Home dashboard arrives in Step 7.")
                    .foregroundStyle(Color.brandMuted)
                Button("Logout") {
                    logout()
                }
                .buttonStyle(SecondaryButtonStyle())

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .accessibilityLabel("Error: \(errorMessage)")
                }
            }
            .padding(AppSpacing.large)
            .frame(maxWidth: 520)
        }
    }

    private func logout() {
        do {
            try onLogout()
        } catch {
            errorMessage = "Could not securely log out. Try again."
        }
    }
}
