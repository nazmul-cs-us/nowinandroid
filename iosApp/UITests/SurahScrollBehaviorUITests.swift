import XCTest

@MainActor
final class SurahScrollBehaviorUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private func snap(_ tag: String) {
        let shot = XCUIScreen.main.screenshot()
        let dir = NSTemporaryDirectory() + "/surah-shots"
        try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        FileManager.default.createFile(atPath: "\(dir)/\(tag).png", contents: shot.pngRepresentation)
        NSLog("SURAHSHOT:\(dir)/\(tag).png")
    }

    func testSurahScrollPhases() {
        app.launchArguments = ["--start-surah=1"]
        app.launch()
        sleep(6)
        snap("rest")

        let screen = app.windows.firstMatch
        screen.swipeUp(velocity: .fast)
        sleep(1)
        screen.swipeUp(velocity: .fast)
        sleep(2)
        let fabAfterUp = app.buttons["Play surah"].exists || app.buttons["Pause recitation"].exists
        // The pinned toolbar keeps Back on screen while the header is collapsed.
        let backWhileDeep = app.buttons["Back"].firstMatch.exists
        snap("deep")

        screen.swipeDown(velocity: .fast)
        sleep(1)
        screen.swipeDown(velocity: .fast)
        sleep(1)
        screen.swipeDown(velocity: .fast)
        sleep(2)
        let fabAfterDown = app.buttons["Play surah"].exists || app.buttons["Pause recitation"].exists
        snap("back")

        NSLog("SURAHRESULT fabAfterUp=\(fabAfterUp) fabAfterDown=\(fabAfterDown) backWhileDeep=\(backWhileDeep)")
        // Swiping up reads deeper: the FAB must hide. Swiping back down
        // toward the top must bring it back.
        XCTAssertFalse(fabAfterUp, "FAB should hide while scrolling up (deeper)")
        XCTAssertTrue(fabAfterDown, "FAB should reappear while scrolling down toward the top")
        XCTAssertTrue(backWhileDeep, "Pinned toolbar should keep the back button visible while collapsed")
    }

    func testSurahAudioContinuesAfterReturningHome() {
        app.launchArguments = ["--start-surah=1"]
        app.launch()

        let play = app.buttons["Play surah"].firstMatch
        XCTAssertTrue(play.waitForExistence(timeout: 30), "Surah play button did not appear")
        play.tap()

        XCTAssertTrue(
            app.buttons["Pause recitation"].firstMatch.waitForExistence(timeout: 120),
            "Surah audio did not download and begin playback"
        )

        let back = app.buttons["Back"].firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 10), "Back button did not appear")
        back.tap()

        XCTAssertTrue(
            app.staticTexts["Playing"].firstMatch.waitForExistence(timeout: 15),
            "Returning home stopped or cleared the active recitation"
        )
    }
}
