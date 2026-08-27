import Shared
import SwiftUI
import UIKit

/// Hosts the shared Compose UI inside SwiftUI.
///
/// This is the entire iOS UI boundary. Swift owns the app lifecycle and the
/// window; everything drawn inside is Compose from `shared/commonMain`, the same
/// code that will render on Android. Adding a screen should mean writing a
/// composable in `shared/`, not another SwiftUI view here.
struct ComposePrayerTimesView: UIViewControllerRepresentable {
    /// The Android test device's real location and settings, so the two apps can
    /// be compared directly. Core Location replaces this with the location slice.
    private static let latitude = 25.1030198
    private static let longitude = 55.1677409
    private static let timeZoneOffset = 4.0
    private static let placeName = "Nad Al Hamar, Dubai"

    /// UAE_IACAD: 18.2° for both Fajr and Isha. Shadow factor 1 is the standard
    /// (Shafi'i/Maliki/Hanbali) madhhab; Hanafi would be 2.
    private static let fajrAngle = 18.2
    private static let ishaAngle = 18.2
    private static let asrShadowFactor: Int32 = 1

    func makeUIViewController(context: Context) -> UIViewController {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: Date())

        return IosComposeRootKt.PrayerTimesViewController(
            year: Int32(parts.year ?? 2026),
            month: Int32(parts.month ?? 1),
            day: Int32(parts.day ?? 1),
            latitude: Self.latitude,
            longitude: Self.longitude,
            timeZoneOffset: Self.timeZoneOffset,
            placeName: Self.placeName,
            fajrAngle: Self.fajrAngle,
            ishaAngle: Self.ishaAngle,
            asrShadowFactor: Self.asrShadowFactor
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Nothing to push down yet; the composable reads its data on creation.
        // State flows from Kotlin once the prayer slice brings its ViewModel over.
    }
}
