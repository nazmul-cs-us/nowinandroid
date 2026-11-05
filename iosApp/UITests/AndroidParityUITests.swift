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
    func testSurahReciterPickerHasScrollableFiltersAndSearch() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--start-surah=1"]
        app.launch()
        let change = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Change reciter, selected:")).firstMatch
        XCTAssertTrue(change.waitForExistence(timeout: 30), app.debugDescription)
        change.tap()
        XCTAssertTrue(app.staticTexts["Choose reciter"].waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.textViews["reciter-search"].exists, app.debugDescription)
        XCTAssertTrue(app.buttons["Preview Mishary Rashid Al-Alafasy"].exists, app.debugDescription)
        app.textViews["reciter-search"].tap()
        app.textViews["reciter-search"].typeText("no-such-reciter-987654")
        XCTAssertTrue(app.staticTexts["No matching reciters"].waitForExistence(timeout: 10), app.debugDescription)
        app.buttons["Clear filters"].tap()
        XCTAssertTrue(app.buttons["Preview Mishary Rashid Al-Alafasy"].waitForExistence(timeout: 10), app.debugDescription)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Surah reciter picker"
        image.lifetime = .keepAlways
        add(image)
    }

    func testStorageOpensDedicatedReciterLibrary() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--start-settings", "--start-reciter-storage"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Reciters & offline audio"].waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertTrue(app.textViews["reciter-search"].exists, app.debugDescription)
        XCTAssertTrue(app.buttons["Close reciters"].exists, app.debugDescription)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Content and Storage reciter library"
        image.lifetime = .keepAlways
        add(image)
    }

}
