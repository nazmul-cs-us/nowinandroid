import Shared
import SwiftUI

/// Today's prayer schedule, computed by the shared Kotlin engine.
///
/// The astronomy — solar declination, hour angles, refraction, the Asr shadow
/// rule — all runs in `:core:prayer-engine`, the same code the Android app uses.
/// This view only formats the result, which is the whole point: one calculation,
/// two platforms, no chance of the two drifting on what time a prayer falls.
struct PrayerTimesView: View {
    /// Hardcoded for this first slice; Core Location arrives with the location
    /// slice rather than being guessed at here.
    ///
    /// These are the Android test device's real coordinates and settings, taken
    /// from its own logs, so the two apps can be compared directly. Passing
    /// different angles here was the entire cause of an apparent mismatch that
    /// briefly looked like an engine bug — the engine agreed all along.
    private static let latitude = 25.1030198
    private static let longitude = 55.1677409
    private static let timeZoneOffset = 4.0
    private static let placeName = "Nad Al Hamar, Dubai"

    /// UAE_IACAD uses 18.2° for both Fajr and Isha. Asr shadow factor 1 is the
    /// standard (Shafi'i/Maliki/Hanbali) madhhab; Hanafi would be 2.
    private static let fajrAngle = 18.2
    private static let ishaAngle = 18.2
    private static let asrShadowFactor: Int32 = 1

    private let slots: [SharedPrayerSlot]
    private let dateLabel: String

    init() {
        let now = Date()
        let calendar = Calendar.current
        let parts = calendar.dateComponents([.year, .month, .day], from: now)

        slots = PrayerSchedule.shared.forDate(
            year: Int32(parts.year ?? 2026),
            month: Int32(parts.month ?? 1),
            day: Int32(parts.day ?? 1),
            latitude: Self.latitude,
            longitude: Self.longitude,
            timeZoneOffset: Self.timeZoneOffset,
            fajrAngle: Self.fajrAngle,
            ishaAngle: Self.ishaAngle,
            asrShadowFactor: Self.asrShadowFactor
        )

        let formatter = DateFormatter()
        formatter.dateStyle = .full
        dateLabel = formatter.string(from: now)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(slots, id: \.name) { slot in
                        HStack {
                            Text(slot.name)
                                .font(.body)
                            Spacer()
                            Text(slot.display)
                                .font(.body.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                        // Read as one unit rather than two fragments.
                        .accessibilityElement(children: .combine)
                    }
                } header: {
                    Text(Self.placeName)
                } footer: {
                    Text("Calculated on device by the shared Kotlin engine — the same code the Android app uses.")
                }
            }
            .navigationTitle("Prayer Times")
            .navigationBarTitleDisplayMode(.large)
        }
    }
}

#Preview {
    PrayerTimesView()
}
