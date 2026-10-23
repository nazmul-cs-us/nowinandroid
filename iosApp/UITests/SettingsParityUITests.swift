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

    func testHeaderMicOpensSharedVoiceSearch() {
        app.terminate()
        app.launchArguments = []
        app.launch()

        // The header mic is the single voice-search surface, matching Android.
        let mic = app.buttons["Voice search"].firstMatch
        XCTAssertTrue(mic.waitForExistence(timeout: 30))
        mic.tap()

        XCTAssertTrue(app.buttons["Close search"].waitForExistence(timeout: 10))
    }

    func testMoreHubKeepsCourseAndNudgeBotAvailable() {
        app.terminate()
        app.launchArguments = []
        app.launch()

        XCTAssertTrue(app.tabBars.buttons["Interests"].waitForExistence(timeout: 30))
        let more = app.buttons["More"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()

        let course = app.buttons["Course"]
        XCTAssertTrue(course.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["For you"].exists)
        course.tap()
        XCTAssertTrue(
            app.staticTexts["Small lessons. Meaningful progress."].waitForExistence(timeout: 20)
        )

        // The Now Nudge bot is the separate circular Voice control beside the tab bar.
        let voice = app.tabBars.buttons["Voice"]
        XCTAssertTrue(voice.waitForExistence(timeout: 10))
        voice.tap()
        XCTAssertTrue(app.staticTexts["Ask Now Nudge"].waitForExistence(timeout: 10))
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

    func testDeenlyNudgeBotSummonsAndActsOnSuggestion() {
        app.terminate()
        app.launchArguments = ["--preview-deenly-nudge"]
        app.launch()

        // The bot is summoned from the separate circular Voice control; the
        // composer's ambient "Surprise me" chip drives the null-prompt flow.
        let voice = app.tabBars.buttons["Voice"]
        XCTAssertTrue(voice.waitForExistence(timeout: 30))
        voice.tap()

        let surprise = app.buttons["surpriseMeChip"]
        XCTAssertTrue(surprise.waitForExistence(timeout: 10))
        surprise.tap()

        // The thinking pill runs its informative messages before the card
        // reveals, so allow for the full generation + sparkle pacing. The
        // card exposes one combined element; its label is the nudge text.
        let nudge = app.descendants(matching: .any)
            .matching(identifier: "nowNudgeCard")
            .firstMatch
        XCTAssertTrue(nudge.waitForExistence(timeout: 30))
        XCTAssertTrue(nudge.label.contains("Mark Asr prayed"))
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)

        nudge.tap()
        XCTAssertTrue(nudge.waitForNonExistence(timeout: 5))
    }

    /// Every insight tile long-presses into its Android-parity expanded
    /// stage: prayer line, salah hadith, reading preview, Qibla radar, and
    /// the grounded suggestion. Driven by the --expand-insight=N launch hook
    /// so no gesture races the pager's scroll.
    func testInsightTilesExpandWithParityStages() {
        func launchExpanded(_ index: Int) {
            app.terminate()
            app.launchArguments = ["--expand-insight=\(index)"]
            app.launch()
        }

        func attach(_ name: String) {
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            shot.name = name
            add(shot)
        }

        // 1. Prayer now → today's prayer line.
        launchExpanded(0)
        XCTAssertTrue(
            app.staticTexts["TODAY'S PRAYER LINE"].waitForExistence(timeout: 30),
            "Prayer line stage did not appear"
        )
        attach("expanded-prayer-line")

        // 2. Today's salah → the staged hadith for the next prayer.
        launchExpanded(1)
        let hadith = NSPredicate(format: "label BEGINSWITH %@", "HADITH FOR")
        XCTAssertTrue(
            app.staticTexts.matching(hadith).firstMatch.waitForExistence(timeout: 30),
            "Salah hadith stage did not appear"
        )
        attach("expanded-salah-hadith")

        // 3. Today's reading → the passage preview.
        launchExpanded(2)
        let passage = NSPredicate(format: "label BEGINSWITH %@", "SURAH ")
        XCTAssertTrue(
            app.staticTexts.matching(passage).firstMatch.waitForExistence(timeout: 30),
            "Reading preview stage did not appear"
        )
        attach("expanded-reading-preview")

        // 4. Qibla → the live radar with turn guidance.
        launchExpanded(3)
        let radar = NSPredicate(
            format: "label CONTAINS %@ OR label BEGINSWITH %@",
            "to Makkah",
            "Turn ",
        )
        XCTAssertTrue(
            app.staticTexts.matching(radar).firstMatch.waitForExistence(timeout: 30),
            "Qibla radar stage did not appear"
        )
        attach("expanded-qibla-radar")
        XCTAssertFalse(app.staticTexts["3D globe"].exists, "The home tile must show the compass")
        app.staticTexts["Direction to Makkah"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Qibla direction"].waitForExistence(timeout: 10))
        attach("qibla-direction-globe")
        app.buttons["Done"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Direction to Makkah"].firstMatch.waitForExistence(timeout: 10),
                      "Closing the globe must preserve the expanded compass tile")

        // 5. AI suggested → the grounded suggestion, the model download
        //    card, or the loading beat while the model generates (the
        //    focused test covers the completed card). Predicates, not
        //    exact matches: the sync strip can merge the loading label
        //    with the media bar's narration subtitle.
        launchExpanded(4)
        let reading = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Reading your downloaded sources")
        ).firstMatch
        let grounded = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "GROUNDED SUGGESTION")
        ).firstMatch
        let missingModel = app.staticTexts["On-device AI model"]
        if reading.waitForExistence(timeout: 15) {
            // The GGUF is present and generation is in flight — the honest
            // interim state on the CPU-only simulator.
            attach("expanded-reading-model")
        } else if grounded.waitForExistence(timeout: 10) {
            attach("expanded-grounded-suggestion")
        } else {
            XCTAssertTrue(
                missingModel.waitForExistence(timeout: 10),
                "Neither the grounded suggestion nor the model download card appeared"
            )
            attach("expanded-missing-model-card")
        }
    }

    /// Tapping Download on the missing-model card must never freeze the UI:
    /// the ~986 MB category download (and its SHA-256 verification) runs on
    /// a background dispatcher owned by the runtime, so the UI stays
    /// interactive and the download survives leaving the tile.
    func testMissingModelDownloadStaysResponsive() throws {
        app.terminate()
        app.launchArguments = ["--expand-insight=4"]
        app.launch()

        let card = app.staticTexts["On-device AI model"]
        if !card.waitForExistence(timeout: 30) {
            // The GGUF is already assembled on this simulator — the download
            // card is the honest state only when the model is absent.
            throw XCTSkip("The Deenly question model is already downloaded")
        }

        let download = app.buttons["Download"].firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 10))
        download.tap()

        // The progress label appears once the CDN transfer starts flowing.
        let downloading = NSPredicate(format: "label BEGINSWITH %@", "Downloading")
        XCTAssertTrue(
            app.staticTexts.matching(downloading).firstMatch.waitForExistence(timeout: 30),
            "Download progress label did not appear"
        )

        // Responsiveness probe: while the 986 MB download runs, switching to
        // the More tab must still work — a blocked main thread cannot serve
        // the tab bar.
        app.tabBars.buttons["More"].tap()
        XCTAssertTrue(
            app.staticTexts["Course"].waitForExistence(timeout: 15),
            "Tab switch froze while the model downloaded"
        )

        // The runtime owns the download: returning to Home must show it
        // still in progress rather than restarted or cancelled.
        app.tabBars.buttons["Home"].tap()
        XCTAssertTrue(
            app.staticTexts.matching(downloading).firstMatch.waitForExistence(timeout: 15),
            "Download did not survive leaving the AI tile"
        )
    }

    /// With the question model downloaded, the enlarged AI tile generates the
    /// grounded suggestion on-device. The first run pays the 986 MB model
    /// load, so the wait is generous; the accessibility marker distinguishes
    /// the model card from the deterministic fallback, which shares the
    /// "GROUNDED SUGGESTION" header.
    func testExpandedAiTileGeneratesModelSuggestion() {
        app.terminate()
        app.launchArguments = ["--expand-insight=4"]
        app.launch()

        let generated = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Model-generated suggestion"))
            .firstMatch
        let missing = app.staticTexts["On-device AI model"]

        // Cold load (986 MB) plus CPU-only decode can take several minutes on
        // the simulator, which has no Metal GPU; on-device is far faster.
        if generated.waitForExistence(timeout: 300) {
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            shot.name = "expanded-model-generated-suggestion"
            add(shot)
        } else {
            // The model card never appeared: acceptable only when the GGUF
            // is genuinely absent (the download card is the honest state).
            XCTAssertTrue(
                missing.waitForExistence(timeout: 10),
                "Neither the model suggestion nor the download card appeared"
            )
        }
    }

    private func scrollToHittable(_ element: XCUIElement) {
        for _ in 0..<12 where !element.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(element.isHittable)
    }
}
