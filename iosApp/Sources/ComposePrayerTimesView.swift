import Shared
import SwiftUI
import UIKit

/// Hosts the shared Compose UI inside SwiftUI.
///
/// This is the entire iOS UI boundary. Swift owns the app lifecycle and the
/// window; everything drawn inside is Compose from `shared/commonMain`, the same
/// code that will render on Android. Adding a screen should mean writing a
/// composable in `shared/`, not another SwiftUI view here.
///
/// It passes nothing: location, date and calculation settings are all resolved
/// in Kotlin. Anything decided here would be a decision Android could not share.
struct ComposePrayerTimesView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosComposeRootKt.PrayerTimesViewController(
            sherpaService: SherpaSpeechService.shared
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Nothing to push down: the composable owns its own state.
    }
}
