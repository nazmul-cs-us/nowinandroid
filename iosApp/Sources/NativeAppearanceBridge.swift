import UIKit

/// Share the visible Compose surface's contrast with the native status bar.
/// Requests stay scoped to their tab, so an inactive controller cannot change it.
final class NativeAppearanceBridge {
    static let shared = NativeAppearanceBridge()
    private var requests: [Int: Bool] = [:]
    private var selectedTab = 0
    private var callLightIcons: Bool?

    func setCallAppearance(lightIcons: Bool?) {
        callLightIcons = lightIcons
        apply()
    }

    func update(owner: Int, lightIcons: Bool?) {
        requests[owner] = lightIcons
        if owner == selectedTab { apply() }
    }
    func select(_ tab: Int) {
        selectedTab = tab
        apply()
    }
    private func apply() {
        let preference = UserDefaults.standard.string(forKey: "ios_dark_theme_config")
        let fallback = preference == "DARK" ||
            (preference != "LIGHT" && UIScreen.main.traitCollection.userInterfaceStyle == .dark)
        let lightIcons = callLightIcons ?? requests[selectedTab] ?? fallback
        UIApplication.shared.setStatusBarStyle(lightIcons ? .lightContent : .darkContent, animated: true)
    }
}
