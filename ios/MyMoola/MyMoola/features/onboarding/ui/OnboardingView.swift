import SwiftUI

struct OnboardingView: View {
    @Binding var page: Int
    let onSkip: () -> Void
    let onFinish: () -> Void

    private let pages = [
        OnboardingPage(
            title: "Welcome to MyMoola",
            subtitle: "Buy, sell, send, and spend crypto with M-PESA support in Kenya.",
            features: [
                ("onb_buy_mpesa", "Buy crypto using M-PESA"),
                ("onb_sell_kes", "Sell crypto back to KES"),
                ("onb_wallet_manage", "Manage everything from one wallet")
            ]
        ),
        OnboardingPage(
            title: "Pay with M-PESA",
            subtitle: "Use your wallet to pay Till Numbers, PayBills, and everyday services.",
            features: [
                ("onb_pay_till", "Pay Till Numbers"),
                ("onb_paybill", "Pay PayBills"),
                ("onb_payment_records", "Keep payment records")
            ]
        ),
        OnboardingPage(
            title: "Send Crypto Easily",
            subtitle: "Send crypto to friends, family, or supported wallet addresses quickly and securely.",
            features: [
                ("onb_send_crypto", "Send crypto to other users"),
                ("onb_receive_crypto", "Receive crypto in your wallet"),
                ("onb_tx_history", "View your transaction history")
            ]
        )
    ]

    var body: some View {
        let content = pages[page]

        ZStack {
            Color.pageBackground.ignoresSafeArea()

            VStack(spacing: AppSpacing.large) {
                HStack {
                    Text("MyMoola")
                        .font(.title2.bold())
                        .foregroundStyle(Color.brandText)
                    Spacer()
                    Button("Skip", action: onSkip)
                        .foregroundStyle(Color.brandAccent)
                }

                Spacer(minLength: 24)

                VStack(alignment: .leading, spacing: AppSpacing.page) {
                    Text(content.title)
                        .font(AppTypography.title)
                        .foregroundStyle(Color.brandText)

                    Text(content.subtitle)
                        .font(AppTypography.body)
                        .foregroundStyle(Color.brandMuted)

                    ForEach(Array(content.features.enumerated()), id: \.offset) { _, feature in
                        HStack(spacing: AppSpacing.medium) {
                            Image(feature.image)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 30, height: 30)
                                .background(Color.brandAccent.opacity(0.12))
                                .clipShape(.rect(cornerRadius: 8))
                                .accessibilityHidden(true)
                            Text(feature.text)
                                .foregroundStyle(Color.brandText)
                            Spacer()
                        }
                        .padding(AppSpacing.medium)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 12))
                        .overlay {
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.panelBorder)
                        }
                    }

                    HStack(spacing: AppSpacing.small) {
                        ForEach(pages.indices, id: \.self) { index in
                            Circle()
                                .fill(index == page ? Color.brandAccent : Color.panelBorder)
                                .frame(width: index == page ? 10 : 8, height: index == page ? 10 : 8)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Page \(page + 1) of \(pages.count)")

                    HStack(spacing: AppSpacing.medium) {
                        if page > 0 {
                            Button("Back") { page -= 1 }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .overlay {
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.panelBorder)
                                }
                        }

                        Button(page == pages.count - 1 ? "Get Started" : "Next") {
                            if page == pages.count - 1 {
                                onFinish()
                            } else {
                                page += 1
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                    }
                }
                .padding(AppSpacing.large)
                .background(.white)
                .clipShape(.rect(cornerRadius: 16))
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.panelBorder)
                }
            }
            .padding(AppSpacing.large)
        }
    }
}
