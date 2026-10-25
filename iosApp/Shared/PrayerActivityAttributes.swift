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
    /// The app theme selected when this activity was created. Live Activity
    /// extensions cannot read the host app's Compose theme environment.
    let themeBrand: String

    init(locationName: String, themeBrand: String = "COASTAL") {
        self.locationName = locationName
        self.themeBrand = themeBrand
    }

    private enum CodingKeys: String, CodingKey {
        case locationName, themeBrand
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        locationName = try values.decode(String.self, forKey: .locationName)
        themeBrand = try values.decodeIfPresent(String.self, forKey: .themeBrand) ?? "COASTAL"
    }

    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encode(locationName, forKey: .locationName)
        try values.encode(themeBrand, forKey: .themeBrand)
    }
}
