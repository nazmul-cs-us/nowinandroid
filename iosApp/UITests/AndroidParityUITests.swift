import XCTest
import UIKit

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
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        let app = XCUIApplication()
        app.launchArguments = []
        app.launch()
        let cover = app.descendants(matching: .any)["home-artwork-cover"].firstMatch
        XCTAssertTrue(cover.waitForExistence(timeout: 30), app.debugDescription)
        let window = app.windows.firstMatch.frame
        guard window.width >= 1300 else {
            throw XCTSkip("Run on a landscape iPad or Mac window at least 1300 points wide.")
        }
        // Artwork uses the window; the text and prayer grid have readable widths.
        XCTAssertGreaterThan(cover.frame.width, window.width - 48)
        XCTAssertGreaterThanOrEqual(cover.frame.minX, window.minX)
        XCTAssertLessThanOrEqual(cover.frame.maxX, window.maxX)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Home wide window"
        image.lifetime = .keepAlways
        add(image)
    }

    func testIPadUsesCurrentArtworkHomeInBothOrientations() throws {
        continueAfterFailure = false
        defer { XCUIDevice.shared.orientation = .portrait }
        for orientation: UIDeviceOrientation in [.landscapeLeft, .portrait] {
            XCUIDevice.shared.orientation = orientation
            let app = XCUIApplication()
            app.launchArguments = []
            app.launch()
            let skip = app.buttons["Skip for now"]
            if skip.waitForExistence(timeout: 5) { skip.tap() }
            XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 30), app.debugDescription)
            for _ in 0..<8 {
                let dismiss = app.buttons["appGuideTipDismiss"]
                if !dismiss.waitForExistence(timeout: 1) { break }
                dismiss.tap()
            }
            let window = app.windows.firstMatch.frame
            guard min(window.width, window.height) >= 600 else {
                throw XCTSkip("Run this Home design check on an iPad.")
            }
            let cover = app.descendants(matching: .any)["home-artwork-cover"].firstMatch
            XCTAssertTrue(cover.waitForExistence(timeout: 10), app.debugDescription)
            XCTAssertFalse(app.staticTexts["Insights"].exists, "The old tablet dashboard must not be used.")
            XCTAssertLessThanOrEqual(cover.frame.maxY, window.maxY)
            let image = XCTAttachment(screenshot: app.screenshot())
            image.name = orientation == .portrait ? "Current iPad Home portrait" : "Current iPad Home landscape"
            image.lifetime = .keepAlways
            add(image)
            cover.swipeLeft()
            XCTAssertTrue(app.staticTexts["TODAY'S SALAH"].waitForExistence(timeout: 10), app.debugDescription)
            app.terminate()
        }
    }

    func testHomeFitsTheWindowAndScrollsToItsLocationRow() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = []
        app.launch()
        let skip = app.buttons["Skip for now"]
        if skip.waitForExistence(timeout: 5) { skip.tap() }
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 30), app.debugDescription)
        for _ in 0..<8 {
            let dismiss = app.buttons["appGuideTipDismiss"]
            if !dismiss.waitForExistence(timeout: 1) { break }
            dismiss.tap()
        }
        let window = app.windows.firstMatch.frame
        let schedule = app.descendants(matching: .any)["home-prayer-schedule"].firstMatch
        XCTAssertTrue(schedule.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertGreaterThanOrEqual(schedule.frame.minX, window.minX)
        XCTAssertLessThanOrEqual(schedule.frame.maxX, window.maxX)
        for name in ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"] {
            let card = app.descendants(matching: .any)["home-prayer-" + name].firstMatch
            if card.exists {
                XCTAssertGreaterThanOrEqual(card.frame.minX, window.minX)
                XCTAssertLessThanOrEqual(card.frame.maxX, window.maxX)
                XCTAssertLessThanOrEqual(card.frame.width, 550)
            }
        }
        let location = app.descendants(matching: .any)["home-location-weather"].firstMatch
        XCTAssertTrue(location.exists, app.debugDescription)
        for _ in 0..<6 {
            if location.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(location.isHittable, "The bottom of Home must remain reachable by scrolling.")
        XCTAssertLessThanOrEqual(location.frame.maxY, window.maxY)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Responsive Android-parity Home"
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
