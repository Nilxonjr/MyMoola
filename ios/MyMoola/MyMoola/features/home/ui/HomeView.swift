import SwiftUI

enum HomeAction: String, CaseIterable, Hashable {
    case buyCrypto
    case sellCrypto
    case withdrawCrypto
    case receiveCrypto
    case payWithMpesa
    case sendToOtherUsers
    case viewRecords
    case viewRates

    var title: String {
        switch self {
        case .buyCrypto: "Buy Crypto"
        case .sellCrypto: "Sell Crypto"
        case .withdrawCrypto: "Withdraw Crypto"
        case .receiveCrypto: "Receive Crypto"
        case .payWithMpesa: "Pay with MPESA"
        case .sendToOtherUsers: "Send to Other Users"
        case .viewRecords: "View Records"
        case .viewRates: "View Rates"
        }
    }

    var imageName: String {
        switch self {
        case .buyCrypto: "onb_buy_mpesa"
        case .sellCrypto: "onb_sell_kes"
        case .withdrawCrypto, .sendToOtherUsers: "onb_send_crypto"
        case .receiveCrypto: "onb_receive_crypto"
        case .payWithMpesa: "onb_pay_till"
        case .viewRecords: "onb_payment_records"
        case .viewRates: "onb_view_rates"
        }
    }
}

struct HomeView: View {
    let service: HomeService?
    let authService: AuthService?
    let session: AuthSession
    let onLogout: () throws -> Void

    @State private var profile: HomeProfile?
    @State private var balance: HomeBalance?
    @State private var selectedCurrency: String?
    @State private var activities: [HomeTransaction] = []
    @State private var isActivityLoading = true
    @State private var activityError: String?
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            homeContent
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: HomeAction.self) { action in
                    HomeFeaturePlaceholderView(action: action)
                }
        }
    }

    private var homeContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                Text("Welcome, \(profile?.fullName.nilIfBlank ?? "User")")
                    .font(.headline)
                    .foregroundStyle(Color.brandText)
                balanceCard
                quickActions
                recentActivity
                if let errorMessage {
                    VStack(alignment: .leading, spacing: AppSpacing.small) {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                            .accessibilityLabel("Error: \(errorMessage)")
                        Button("Try again") { Task { await load() } }
                    }
                    .font(.footnote)
                }
            }
            .padding(AppSpacing.page)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .background(Color.pageBackground)
        // Keep the request alive if SwiftUI cancels the pull gesture task.
        .refreshable { await Task { await load() }.value }
        .task { await load() }
    }

    private var quickActions: some View {
        VStack(spacing: 12) {
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 3),
                spacing: 12
            ) {
                ForEach(HomeAction.allCases.prefix(6), id: \.self) { action in
                    quickActionLink(action)
                }
            }
            HStack(spacing: 0) {
                Spacer(minLength: 0)
                quickActionLink(.viewRecords)
                    .frame(width: 94)
                Spacer(minLength: 0)
                quickActionLink(.viewRates)
                    .frame(width: 94)
                Spacer(minLength: 0)
            }
        }
        .padding(16)
        .background(.white)
        .clipShape(.rect(cornerRadius: 18))
        .overlay { RoundedRectangle(cornerRadius: 18).stroke(Color.panelBorder) }
    }

    private func quickActionLink(_ action: HomeAction) -> some View {
        NavigationLink(value: action) {
            VStack(spacing: 8) {
                Image(action.imageName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                    .frame(width: 34, height: 34)
                    .background(Color.brandAccent.opacity(0.12))
                    .clipShape(.rect(cornerRadius: 8))
                Text(action.title)
                    .font(.caption.weight(.medium))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity)
                    .frame(height: 32)
            }
            .foregroundStyle(Color.brandText)
            .frame(maxWidth: 94)
            .frame(height: 94)
            .background(Color.pageBackground)
            .clipShape(.rect(cornerRadius: 12))
            .overlay { RoundedRectangle(cornerRadius: 12).stroke(Color.panelBorder) }
        }
        .accessibilityLabel(action.title)
        .accessibilityHint("Opens a coming soon screen")
    }

    private var header: some View {
        HStack(spacing: 10) {
            Image("MyMoola")
                .resizable()
                .scaledToFit()
                .frame(width: 42, height: 42)
                .clipShape(.rect(cornerRadius: 12))
                .accessibilityLabel("MyMoola app icon")
            VStack(alignment: .leading) {
                Text("MyMoola")
                    .font(AppTypography.title)
                    .foregroundStyle(Color.brandText)
                Text("Wallet")
                    .font(.subheadline)
                    .foregroundStyle(Color.brandMuted)
            }
            Spacer()
            Button("Logout") { logout() }
                .font(.subheadline.weight(.medium))
        }
        .padding(.top, 18)
    }

    private var balanceCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("TOTAL BALANCE")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.brandMuted)
                Spacer()
                if let balance, !balance.wallets.isEmpty {
                    Menu {
                        ForEach(balance.wallets, id: \.currency) { wallet in
                            Button(wallet.currency) { selectedCurrency = wallet.currency }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if let selectedCurrency {
                                Image(walletImage(for: selectedCurrency))
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 16, height: 16)
                            }
                            Text(selectedCurrency ?? "Wallet")
                            Image(systemName: "chevron.down")
                        }
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Color.brandText)
                        .padding(8)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 10))
                        .overlay { RoundedRectangle(cornerRadius: 10).stroke(Color.panelBorder) }
                    }
                }
            }

            if let balance {
                Text("\(balance.displayCurrency) \(balance.totalFiatEquivalent.formatted(.number.precision(.fractionLength(2)).grouping(.automatic)))")
                    .font(.largeTitle.weight(.semibold))
                    .foregroundStyle(Color.brandText)
                    .accessibilityLabel("Total balance, \(balance.displayCurrency) \(balance.totalFiatEquivalent.formatted(.number.precision(.fractionLength(2))))")
                if let wallet = balance.wallets.first(where: { $0.currency == selectedCurrency }) {
                    Text("\(wallet.total.formatted(.number.precision(.fractionLength(0...8)))) \(wallet.currency)")
                        .foregroundStyle(Color.brandMuted)
                } else {
                    Text("No wallet balances yet.")
                        .foregroundStyle(Color.brandMuted)
                }
            } else {
                Text(isLoading ? "Loading balance..." : "Balance unavailable")
                    .font(.title2.weight(.semibold))
                Text(isLoading ? "Fetching wallets..." : "Pull down to refresh.")
                    .foregroundStyle(Color.brandMuted)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white)
        .clipShape(.rect(cornerRadius: 18))
        .overlay { RoundedRectangle(cornerRadius: 18).stroke(Color.panelBorder) }
    }

    private var recentActivity: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Recent Activity")
                .font(.headline)
                .foregroundStyle(Color.brandText)

            if isActivityLoading {
                Text("Loading activity...")
                    .foregroundStyle(Color.brandMuted)
            } else if let activityError {
                Text(activityError)
                    .foregroundStyle(.red)
                    .accessibilityLabel("Error: \(activityError)")
            } else if activities.isEmpty {
                Text("No transactions yet.")
                    .foregroundStyle(Color.brandMuted)
            } else {
                ForEach(activities, id: \.id) { activity in
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 8) {
                                Text(activity.displayType(for: profile?.id ?? ""))
                                    .fontWeight(.medium)
                                    .foregroundStyle(Color.brandText)
                                Text(activity.status.lowercased())
                                    .font(.caption)
                                    .foregroundStyle(
                                        activity.isFailed
                                            ? .red : Color.brandAccent
                                    )
                            }
                            Text("\(activity.currency) • \(activity.displayDate)")
                                .font(.caption)
                                .foregroundStyle(Color.brandMuted)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 4)
                        Text("\(activity.amountPrefix(for: profile?.id ?? ""))\(activity.amount.formatted(.number.precision(.fractionLength(0...8)))) \(activity.currency)")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(
                                activity.isFailed
                                    ? .red
                                    : (activity.amountPrefix(for: profile?.id ?? "") == "+"
                                        ? Color(red: 16 / 255, green: 185 / 255, blue: 129 / 255)
                                        : Color(red: 239 / 255, green: 68 / 255, blue: 68 / 255))
                            )
                            .lineLimit(1)
                    }
                    .padding(12)
                    .background(.white)
                    .clipShape(.rect(cornerRadius: 14))
                    .overlay { RoundedRectangle(cornerRadius: 14).stroke(Color.panelBorder) }
                }
            }
        }
    }

    private func load() async {
        guard !isLoading else { return }
        isLoading = true
        isActivityLoading = true
        activityError = nil
        errorMessage = nil
        defer {
            isLoading = false
            isActivityLoading = false
        }
        guard let service, let tokens = try? session.load() else {
            errorMessage = "Could not load your session. Log out and sign in again."
            activityError = "Activity unavailable."
            return
        }

        do {
            try await fetch(using: tokens.accessToken, service: service)
        } catch APIError.httpStatus(401, _) {
            guard let authService else {
                errorMessage = "Session expired. Log out and sign in again."
                activityError = "Activity unavailable."
                return
            }
            do {
                let refreshed = try await authService.refresh(refreshToken: tokens.refreshToken)
                try session.save(AuthTokens(
                    accessToken: refreshed.accessToken,
                    refreshToken: refreshed.refreshToken
                ))
                try await fetch(using: refreshed.accessToken, service: service)
            } catch {
                errorMessage = "Session expired. Log out and sign in again."
                activityError = "Activity unavailable."
            }
        } catch let error as APIError {
            errorMessage = error.message
            activityError = "Activity unavailable."
        } catch {
            errorMessage = "Could not load Home. Try again."
            activityError = "Activity unavailable."
        }
    }

    private func fetch(using token: String, service: HomeService) async throws {
        async let loadedProfile = service.profile(accessToken: token)
        async let loadedBalance = service.balance(accessToken: token)
        let (newProfile, newBalance) = try await (loadedProfile, loadedBalance)
        profile = newProfile
        balance = newBalance
        if !newBalance.wallets.contains(where: { $0.currency == selectedCurrency }) {
            selectedCurrency = newBalance.wallets.first(where: { $0.total > 0 })?.currency
                ?? newBalance.wallets.first?.currency
        }
        do {
            activities = try await service.recentTransactions(accessToken: token).items
        } catch APIError.httpStatus(401, _) {
            throw APIError.httpStatus(401, "Unauthorized")
        } catch let error as APIError {
            activityError = error.message
        } catch {
            activityError = "Could not load activity. Pull down to retry."
        }
    }

    private func walletImage(for currency: String) -> String {
        switch currency.uppercased() {
        case "BTC": "bitcoin_logo"
        case "ETH": "ethereum_logo"
        case "USDC": "usdc_logo"
        default: "onb_wallet_manage"
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

private struct HomeFeaturePlaceholderView: View {
    let action: HomeAction

    var body: some View {
        VStack(spacing: AppSpacing.page) {
            Image(action.imageName)
                .resizable()
                .scaledToFit()
                .frame(width: 72, height: 72)
                .accessibilityHidden(true)
            Text(action.title)
                .font(AppTypography.title)
                .foregroundStyle(Color.brandText)
            Text("Coming soon")
                .foregroundStyle(Color.brandMuted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.pageBackground)
        .navigationTitle(action.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
    }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
