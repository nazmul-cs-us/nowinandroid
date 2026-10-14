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
}
