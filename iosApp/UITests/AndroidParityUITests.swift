import XCTest

@MainActor
final class AndroidParityUITests: XCTestCase {
    func testExpandedQuranShowsReaderAndCloseAction() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--expand-insight=2"]
        app.launch()
        let heading = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "SURAH ")).firstMatch
        XCTAssertTrue(heading.waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertTrue(app.buttons["Collapse insight"].exists, app.debugDescription)
        let passageCount = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]+ AYAT$")).firstMatch
        XCTAssertTrue(passageCount.waitForExistence(timeout: 30), app.debugDescription)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Expanded Quran parity"
        image.lifetime = .keepAlways
        add(image)
    }

    func testSettingsHidesNativeTabBar() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--start-settings"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 30))
        let hidden = NSPredicate { _, _ in !app.tabBars.firstMatch.isHittable }
        expectation(for: hidden, evaluatedWith: nil)
        waitForExpectations(timeout: 10)
    }
}
