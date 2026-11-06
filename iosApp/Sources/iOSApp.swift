import Shared
import SwiftUI
import UIKit

extension Notification.Name {
    static let openPrayerLiveActivity = Notification.Name("openPrayerLiveActivity")
}

@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        application.registerForRemoteNotifications()
        FamilySystemCalls.shared.start()
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
    @State private var showingStartup = !ProcessInfo.processInfo.arguments.contains { $0.hasPrefix("--start-") }
    @AppStorage("ios_dark_theme_config") private var savedDarkTheme = "FOLLOW_SYSTEM"

    private var appPreferredColorScheme: ColorScheme? {
        if savedDarkTheme == "LIGHT" {
            return .light
        }
        if savedDarkTheme == "DARK" {
            return .dark
        }
        return nil
    }

    init() {
        CelestialNativeBridgeKt.RegisterCelestialNativeViewProvider(
            provider: CelestialNativeViewProvider.shared
        )
        // Proves the framework is linked and shared code executes, before any UI
        // draws. Appears in the Xcode console via NSLog.
        SharedFramework.shared.logSmokeTest()
        PrayerNotificationCoordinator.shared.start()
    }

    @State private var showingFamily = false

    var body: some Scene {
        WindowGroup {
            ZStack {
            Group {
                if #available(iOS 26.0, *) {
                    NativePrayerTabsView()
                } else {
                    ComposePrayerTimesView()
                        .ignoresSafeArea(.container)
                }
            }
                if showingStartup {
                    StartupSparkView {
                        withAnimation(.easeOut(duration: 0.25)) { showingStartup = false }
                    }
                    .ignoresSafeArea(.container)
                    .transition(.opacity)
                    .zIndex(10)
                }
            }
                .modifier(FamilyCallPresentation(enabled: !showingFamily))
                .overlay(alignment: .topTrailing) {
                    if #available(iOS 26.0, *) { EmptyView() }
                    else {
                        Button { showingFamily = true } label: {
                            Label("Family", systemImage: "person.2.fill")
                                .padding(10).background(.regularMaterial, in: Capsule())
                        }.padding(.trailing, 12)
                    }
                }
                .sheet(isPresented: $showingFamily) {
                    NavigationStack { FamilyView().modifier(FamilyCallPresentation()) }
                }
                .preferredColorScheme(appPreferredColorScheme)
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("sharedBarAppearanceChanged"))) { notification in
            guard let value = notification.object as? String else { return }
            let fields = value.split(separator: ":")
            guard fields.count == 2, let owner = Int(fields[0]) else { return }
            NativeAppearanceBridge.shared.update(owner: owner, lightIcons: fields[1] == "system" ? nil : fields[1] == "true")
        }
                .onOpenURL { url in
                    guard url.scheme == "deenly" else { return }
                    if url.host == "family-call" {
                        showingFamily = true
                        if let id = FamilyCallLink().callId(input: url.absoluteString) {
                            Task { if !(await FamilyStore.shared.resumeForPush(id)) { FamilyStore.shared.message = "This call is no longer ringing. Open Family to call back." } }
                        }
                        return
                    }
                    guard url.host == "prayer-times" else { return }
                    IosComposeRootKt.OpenPrayerTimesFromLiveActivity()
                    NotificationCenter.default.post(name: .openPrayerLiveActivity, object: nil)
                }
                .onAppear { FamilyStore.shared.activityChanged(scenePhase == .active) }
                .onChange(of: scenePhase) { phase in
                    FamilyStore.shared.activityChanged(phase == .active)
                    if phase == .active {
                        PrayerNotificationCoordinator.shared.refresh()
                    }
                }
        }
    }
}

private struct StartupSparkView: UIViewControllerRepresentable {
    let onFinished: () -> Void
    func makeUIViewController(context: Context) -> UIViewController {
        IosComposeRootKt.DeenlyStartupViewController(onFinished: onFinished)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) { }
}
