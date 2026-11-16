import SwiftUI

struct LaunchView: View {
    var body: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            VStack(spacing: AppSpacing.medium) {
                Image("MyMoola")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 96, height: 96)
                    .clipShape(.rect(cornerRadius: 20))
                    .accessibilityHidden(true)
                Text("MyMoola")
                    .font(.largeTitle.bold())
                    .foregroundStyle(Color.brandText)
            }
        }
    }
}
