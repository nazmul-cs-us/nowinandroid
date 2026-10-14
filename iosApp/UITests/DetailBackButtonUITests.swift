import XCTest

@MainActor
final class DetailBackButtonUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
    }

    /// Launches with the given detail-screen hook, taps the toolbar Back
    /// button, and asserts the reader pops back to the home tab.
    private func assertBackPopsHome(launchArguments: [String], screen: String) {
        app.launchArguments = launchArguments
        app.launch()

        let back = app.buttons["Back"].firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 30), "Back button missing on \(screen)")
        back.tap()

        let homeMic = app.buttons["Voice search"].firstMatch
        XCTAssertTrue(homeMic.waitForExistence(timeout: 15), "Back on \(screen) did not return to home")
    }

    func testFortressChapterBackPopsToHome() {
        assertBackPopsHome(launchArguments: ["--start-fortress=1"], screen: "Fortress chapter")
    }

    func testDuaDetailBackPopsToHome() {
        assertBackPopsHome(launchArguments: ["--start-dua=1"], screen: "Dua detail")
    }

    func testBukhariHadithBackPopsToHome() {
        assertBackPopsHome(launchArguments: ["--start-bukhari=1"], screen: "Bukhari hadith detail")
    }
}
