import XCTest

@MainActor
final class SettingsParityUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--start-settings"]
        app.launch()
    }

    func testSettingsSectionsAndContentStorageAreUsable() {
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 20))

        let sectionTitles = [
            "Appearance",
            "Prayer Times",
            "Notifications",
            "Travel Dua",
            "Voice Recognition",
            "Text-to-Speech",
            "Content & Storage",
            "About",
        ]
        for title in sectionTitles {
            XCTAssertTrue(app.staticTexts[title].exists, "Missing Settings section: \(title)")
        }

        let contentStorage = app.staticTexts["Content & Storage"]
        scrollToHittable(contentStorage)
        contentStorage.tap()

        let hadithCollections = app.staticTexts["Hadith Collections"]
        XCTAssertTrue(hadithCollections.waitForExistence(timeout: 60))
        scrollToHittable(hadithCollections)
        hadithCollections.tap()
        XCTAssertTrue(app.staticTexts["Sahih Bukhari"].waitForExistence(timeout: 10))

        let download = app.buttons["Download Sahih Bukhari"]
        let delete = app.buttons["Delete Sahih Bukhari"]
        if delete.exists {
            delete.tap()
            XCTAssertTrue(download.waitForExistence(timeout: 20))
        }
        XCTAssertTrue(download.waitForExistence(timeout: 10))
        download.tap()
        XCTAssertTrue(delete.waitForExistence(timeout: 120))
        delete.tap()
        XCTAssertTrue(download.waitForExistence(timeout: 20))
    }

    private func scrollToHittable(_ element: XCUIElement) {
        for _ in 0..<12 where !element.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(element.isHittable)
    }
}
