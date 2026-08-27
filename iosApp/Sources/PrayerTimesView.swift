import Shared
import SwiftUI

/// Today's prayer schedule, computed by the shared Kotlin engine.
///
/// The astronomy — solar declination, hour angles, refraction, the Asr shadow
/// rule — all runs in `:core:prayer-engine`, the same code the Android app uses.
/// This view only formats the result, which is the whole point: one calculation,
/// two platforms, no chance of the two drifting on what time a prayer falls.
struct PrayerTimesView: View {
    /// Dubai. Hardcoded for this first slice; Core Location comes with the
    /// location slice rather than being guessed at here.
    private static let latitude = 25.276987
    private static let longitude = 55.296249
    private static let timeZoneOffset = 4.0
    private static let placeName = "Dubai, UAE"

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
            fajrAngle: 18.0,
            ishaAngle: 17.0,
            asrShadowFactor: 1
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
