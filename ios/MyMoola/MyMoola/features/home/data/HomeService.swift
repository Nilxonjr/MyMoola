import Foundation

nonisolated struct HomeProfile: Decodable, Sendable {
    let id: String
    let fullName: String
}

nonisolated struct HomeBalance: Decodable, Sendable {
    let displayCurrency: String
    let totalFiatEquivalent: Decimal
    let wallets: [HomeWallet]
}

nonisolated struct HomeWallet: Decodable, Sendable {
    let currency: String
    let total: Decimal
}

nonisolated struct HomeTransactionsPage: Decodable, Sendable {
    let items: [HomeTransaction]
}

nonisolated struct HomeTransaction: Decodable, Sendable {
    let id: String
    let type: String
    let status: String
    let initiatorUserId: String?
    let counterpartyUserId: String?
    let currency: String
    let amount: Decimal
    let createdAt: String

    var isFailed: Bool {
        ["Failed", "Declined", "Cancelled", "Canceled", "Timeout"].contains {
            status.caseInsensitiveCompare($0) == .orderedSame
        }
    }

    func displayType(for userID: String) -> String {
        if type.caseInsensitiveCompare("Send") == .orderedSame,
           counterpartyUserId?.caseInsensitiveCompare(userID) == .orderedSame {
            return "Receive"
        }
        return type
    }

    func amountPrefix(for userID: String) -> String {
        if isFailed { return "" }
        if type.caseInsensitiveCompare("Send") == .orderedSame {
            return counterpartyUserId?.caseInsensitiveCompare(userID) == .orderedSame ? "+" : "-"
        }
        return ["Buy", "Deposit", "Receive"].contains {
            type.caseInsensitiveCompare($0) == .orderedSame
        } ? "+" : "-"
    }

    var displayDate: String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from: createdAt) ?? {
            formatter.formatOptions = [.withInternetDateTime]
            return formatter.date(from: createdAt)
        }()
        guard let date else { return createdAt }
        let output = DateFormatter()
        output.dateFormat = "dd-MM-yyyy"
        return output.string(from: date)
    }
}

nonisolated struct HomeService: Sendable {
    let client: APIClient

    func profile(accessToken: String) async throws -> HomeProfile {
        try await client.get(
            "api/users/me",
            headers: ["Authorization": "Bearer \(accessToken)"],
            as: HomeProfile.self
        )
    }

    func balance(accessToken: String) async throws -> HomeBalance {
        try await client.get(
            "api/users/me/balance?currency=KES",
            headers: ["Authorization": "Bearer \(accessToken)"],
            as: HomeBalance.self
        )
    }

    func recentTransactions(accessToken: String) async throws -> HomeTransactionsPage {
        try await client.get(
            "api/users/me/transactions?page=1&pageSize=3",
            headers: ["Authorization": "Bearer \(accessToken)"],
            as: HomeTransactionsPage.self
        )
    }
}
