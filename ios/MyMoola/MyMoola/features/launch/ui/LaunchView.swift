import SwiftUI

struct LaunchView: View {
    var body: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            VStack(spacing: AppSpacing.medium) {
                Image(systemName: "wallet.bifold.fill")
                    .font(.system(size: 54))
                    .foregroundStyle(Color.brandAccent)
                    .accessibilityHidden(true)
                Text("MyMoola")
                    .font(.largeTitle.bold())
                    .foregroundStyle(Color.brandText)
            }
        }
    }
}
