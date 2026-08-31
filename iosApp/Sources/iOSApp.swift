import Shared
import SwiftUI
import UIKit

@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        PrayerNotificationCoordinator.shared.didRegisterForRemoteNotifications(
            deviceToken: deviceToken
        )
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        PrayerNotificationCoordinator.shared.didFailToRegisterForRemoteNotifications(error: error)
    }
}

/// Application entry point.
///
/// Deliberately thin. This is a host for shared Kotlin, not a place for logic —
/// anything that decides behaviour belongs in `shared/` where both platforms get
/// it. The screen itself is Compose, written once in `shared/commonMain`.
@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Proves the framework is linked and shared code executes, before any UI
        // draws. Appears in the Xcode console via NSLog.
        SharedFramework.shared.logSmokeTest()
        PrayerNotificationCoordinator.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ComposePrayerTimesView()
                .ignoresSafeArea()
                .onChange(of: scenePhase) { phase in
                    if phase == .active {
                        PrayerNotificationCoordinator.shared.refresh()
                    }
                }
        }
    }
}
