import ActivityKit
import Foundation

struct PrayerActivityAttributes: ActivityAttributes {
    /// One prayer in today's schedule for the Live Activity's summary strip.
    struct PrayerTime: Codable, Hashable {
        let name: String
        let arabic: String
        let time: String
        let isNext: Bool
    }

    struct ContentState: Codable, Hashable {
        let prayerName: String
        let prayerArabicName: String
        /// The wall-clock prayer time, e.g. "4:35 PM", preformatted in the
        /// schedule's time zone so the widget never needs a formatter.
        let prayerTime: String
        let prayerDate: Date
        /// Start of the inter-prayer segment the progress bar fills across.
        let previousPrayerDate: Date?
        let activeUntil: Date
        /// Today's five prayers for the Android-parity schedule strip.
        let times: [PrayerTime]
    }

    let locationName: String
}
