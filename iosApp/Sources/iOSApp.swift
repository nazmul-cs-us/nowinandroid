import Shared
import SwiftUI

/// Application entry point.
///
/// Deliberately thin. This is a host for shared Kotlin, not a place for logic —
/// anything that decides behaviour belongs in `shared/` where both platforms get
/// it. The screen itself is Compose, written once in `shared/commonMain`.
@main
struct iOSApp: App {
    init() {
        // Proves the framework is linked and shared code executes, before any UI
        // draws. Appears in the Xcode console via NSLog.
        SharedFramework.shared.logSmokeTest()
    }

    var body: some Scene {
        WindowGroup {
            ComposePrayerTimesView()
                .ignoresSafeArea()
        }
    }
}
