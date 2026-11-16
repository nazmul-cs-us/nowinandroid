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
        let page = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "^Page [0-9]+ of [0-9]+$")).firstMatch
        XCTAssertTrue(page.waitForExistence(timeout: 30), app.debugDescription)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Expanded Quran parity"
        image.lifetime = .keepAlways
        add(image)
    }

    func testHomeUsesTheAvailableWideWindow() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        let profile = app.buttons["Open local profile"]
        let settings = app.buttons["Prayer settings"]
        XCTAssertTrue(profile.waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertTrue(settings.waitForExistence(timeout: 10), app.debugDescription)
        let window = app.windows.firstMatch.frame
        guard window.width >= 1300 else {
            throw XCTSkip("Run on a landscape iPad or Mac window at least 1300 points wide.")
        }
        // Allow the side-navigation gutter and ordinary safe-area/panel padding.
        // A centered 1200-point dashboard fails this on a wide window.
        XCTAssertGreaterThan(settings.frame.maxX - profile.frame.minX, window.width - 180,
                             "Home should expand with its window instead of staying in a centered column.")
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Home wide window"
        image.lifetime = .keepAlways
        add(image)
    }

    func testFamilyIsReachableFromTheNativeTabBar() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        let family = app.tabBars.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Family")).firstMatch
        XCTAssertTrue(family.waitForExistence(timeout: 30), app.debugDescription)
        family.tap()
        XCTAssertTrue(app.staticTexts["Family"].waitForExistence(timeout: 10) ||
            app.buttons["Family contacts"].waitForExistence(timeout: 10), app.debugDescription)
        let back = app.buttons["family-back-to-app"]
        XCTAssertTrue(back.waitForExistence(timeout: 10), app.debugDescription)
        back.tap()
        let homeSelected = NSPredicate { _, _ in app.tabBars.buttons["Home"].isSelected }
        expectation(for: homeSelected, evaluatedWith: nil)
        waitForExpectations(timeout: 10)
    }

    func testFamilyCanLaunchDirectlyAndReturnHome() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--start-family", "--classic-tabs"]
        app.launch()
        let family = app.tabBars.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Family")).firstMatch
        let back = app.buttons["family-back-to-app"]
        XCTAssertTrue(back.waitForExistence(timeout: 30), app.debugDescription)
        if family.exists { XCTAssertTrue(family.isSelected, app.debugDescription) }
        XCTAssertFalse(app.sheets.firstMatch.exists, app.debugDescription)
        XCTAssertTrue(back.waitForExistence(timeout: 10), app.debugDescription)
        back.tap()
        XCTAssertTrue(app.tabBars.buttons["Home"].isSelected, app.debugDescription)
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
