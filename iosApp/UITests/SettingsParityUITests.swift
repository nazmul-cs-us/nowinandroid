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

    func testNativeVoiceTabOpensSharedVoiceSearch() {
        app.terminate()
        app.launchArguments = []
        app.launch()

        let voice = app.buttons["Voice"]
        XCTAssertTrue(voice.waitForExistence(timeout: 30))
        voice.tap()

        XCTAssertTrue(app.buttons["Close search"].waitForExistence(timeout: 10))
    }

    func testMoreHubKeepsCourseInterestsAndVoiceAvailable() {
        app.terminate()
        app.launchArguments = []
        app.launch()

        let more = app.buttons["More"]
        XCTAssertTrue(more.waitForExistence(timeout: 30))
        more.tap()

        let course = app.buttons["Course"]
        XCTAssertTrue(course.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Interests"].exists)
        course.tap()
        XCTAssertTrue(
            app.staticTexts["Small lessons. Meaningful progress."].waitForExistence(timeout: 20)
        )

        app.buttons["Voice"].tap()
        XCTAssertTrue(app.buttons["Close search"].waitForExistence(timeout: 10))
    }

    func testPrayerScheduleCanExpand() {
        app.terminate()
        app.launchArguments = []
        app.launch()

        let showAllPrayers = app.buttons["Show All Prayers"]
        XCTAssertTrue(showAllPrayers.waitForExistence(timeout: 30))
        showAllPrayers.tap()

        XCTAssertTrue(app.buttons["Show Less"].waitForExistence(timeout: 10))
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testPrayerVolumeUsesNativeSlider() {
        app.terminate()
        app.launchArguments = []
        app.launch()

        let tuneSchedule = app.buttons["Tune schedule"]
        XCTAssertTrue(tuneSchedule.waitForExistence(timeout: 30))
        tuneSchedule.tap()

        let fajrVolume = app.buttons["Adjust Fajr adhan volume"].firstMatch
        XCTAssertTrue(fajrVolume.waitForExistence(timeout: 10))
        fajrVolume.tap()

        XCTAssertTrue(app.staticTexts["Fajr adhan volume"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.sliders.firstMatch.exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testDeenlyNudgeUsesCompactActionCapsule() {
        app.terminate()
        app.launchArguments = ["--preview-deenly-nudge"]
        app.launch()

        let nudge = app.staticTexts["Mark Asr prayed"]
        XCTAssertTrue(nudge.waitForExistence(timeout: 30))
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)

        nudge.tap()
        XCTAssertTrue(nudge.waitForNonExistence(timeout: 5))
    }

    private func scrollToHittable(_ element: XCUIElement) {
        for _ in 0..<12 where !element.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(element.isHittable)
    }
}
