import Foundation
import Testing
@testable import MyMoola

@Suite(.serialized)
struct HomeServiceTests {
    @Test func allAndroidQuickActionsHaveDestinationsAndAssets() {
        #expect(HomeAction.allCases.map(\.title) == [
            "Buy Crypto", "Sell Crypto", "Withdraw Crypto", "Receive Crypto",
            "Pay with MPESA", "Send to Other Users", "View Records", "View Rates"
        ])
        #expect(HomeAction.allCases.map(\.imageName) == [
            "onb_buy_mpesa", "onb_sell_kes", "onb_send_crypto", "onb_receive_crypto",
            "onb_pay_till", "onb_send_crypto", "onb_payment_records", "onb_view_rates"
        ])
    }

    @Test func decodesProfileAndBalance() async throws {
        let profile = try JSONDecoder().decode(
            HomeProfile.self,
            from: Data(#"{"id":"1","fullName":"Amina","phone":"+254700000000"}"#.utf8)
        )
        let balance = try JSONDecoder().decode(
            HomeBalance.self,
            from: Data(#"{"displayCurrency":"KES","totalFiatEquivalent":1250.5,"wallets":[{"currency":"BTC","total":0.01}]}"#.utf8)
        )

        #expect(profile.fullName == "Amina")
        #expect(balance.displayCurrency == "KES")
        #expect(balance.totalFiatEquivalent == 1250.5)
        #expect(balance.wallets.first?.currency == "BTC")
        #expect(balance.wallets.first?.total == 0.01)
    }

    @Test func homeRequestsUseAuthorizedEndpoints() async throws {
        HomeURLProtocol.requests = []
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [HomeURLProtocol.self]
        let client = APIClient(
            baseURL: URL(string: "http://localhost:8080/")!,
            session: URLSession(configuration: config)
        )
        let service = HomeService(client: client)

        let profile = try await service.profile(accessToken: "access")
        let balance = try await service.balance(accessToken: "access")

        #expect(profile.fullName == "Amina")
        #expect(balance.wallets.count == 1)
        #expect(HomeURLProtocol.requests.map { $0.url?.path } == [
            "/api/users/me", "/api/users/me/balance"
        ])
        #expect(HomeURLProtocol.requests.last?.url?.query == "currency=KES")
        #expect(HomeURLProtocol.requests.allSatisfy {
            $0.value(forHTTPHeaderField: "Authorization") == "Bearer access"
        })
    }

    @Test func recentActivityUsesNewestPageAndDecodesEmptyResponse() async throws {
        HomeURLProtocol.requests = []
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [HomeURLProtocol.self]
        let client = APIClient(
            baseURL: URL(string: "http://localhost:8080/")!,
            session: URLSession(configuration: config)
        )

        let page = try await HomeService(client: client).recentTransactions(accessToken: "access")

        #expect(page.items.isEmpty)
        #expect(HomeURLProtocol.requests.last?.url?.path == "/api/users/me/transactions")
        #expect(HomeURLProtocol.requests.last?.url?.query == "page=1&pageSize=3")
        #expect(HomeURLProtocol.requests.last?.value(forHTTPHeaderField: "Authorization") == "Bearer access")
    }

    @Test func transactionFormatsDirectionAndDate() throws {
        let page = try JSONDecoder().decode(
            HomeTransactionsPage.self,
            from: Data(#"{"items":[{"id":"tx1","type":"Send","status":"Completed","initiatorUserId":"user1","counterpartyUserId":"user2","currency":"USDC","amount":12.5,"createdAt":"2026-09-23T10:15:00Z"}]}"#.utf8)
        )

        #expect(page.items.count == 1)
        #expect(page.items[0].displayType(for: "user1") == "Send")
        #expect(page.items[0].displayType(for: "user2") == "Receive")
        #expect(page.items[0].amountPrefix(for: "user1") == "-")
        #expect(page.items[0].amountPrefix(for: "user2") == "+")
        #expect(page.items[0].displayDate == "23-09-2026")
    }

    @Test func declinedTransactionHasNoSignedAmount() throws {
        let page = try JSONDecoder().decode(
            HomeTransactionsPage.self,
            from: Data(#"{"items":[{"id":"tx2","type":"Send","status":"Declined","initiatorUserId":"user1","counterpartyUserId":"user2","currency":"USDC","amount":12.5,"createdAt":"2026-09-23T10:15:00Z"}]}"#.utf8)
        )

        #expect(page.items[0].isFailed)
        #expect(page.items[0].amountPrefix(for: "user1") == "")
    }
}

private final class HomeURLProtocol: URLProtocol {
    static var requests: [URLRequest] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.requests.append(request)
        let isBalance = request.url?.path.hasSuffix("balance") == true
        let json: String
        if request.url?.path.hasSuffix("transactions") == true {
            json = #"{"items":[],"page":1,"pageSize":3,"totalCount":0,"totalPages":0}"#
        } else if isBalance {
            json = #"{"displayCurrency":"KES","totalFiatEquivalent":1250.5,"wallets":[{"currency":"BTC","total":0.01}]}"#
        } else {
            json = #"{"id":"1","fullName":"Amina","phone":"+254700000000"}"#
        }
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(json.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
