//
//  MyMoolaUITests.swift
//  MyMoolaUITests
//
//  Created by Austin Mwenda Muriithi on 14/09/2026.
//

import XCTest

final class MyMoolaUITests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    @MainActor
    func testOnboardingAdvancesToAuthentication() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Welcome to MyMoola"].waitForExistence(timeout: 3))
        app.buttons["Next"].tap()
        XCTAssertTrue(app.staticTexts["Pay with M-PESA"].exists)
        app.buttons["Next"].tap()
        XCTAssertTrue(app.staticTexts["Send Crypto Easily"].exists)
        app.buttons["Get Started"].tap()
        XCTAssertTrue(app.staticTexts["Authentication"].exists)
    }

    @MainActor
    func testLaunchPerformance() throws {
        // This measures how long it takes to launch your application.
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}
