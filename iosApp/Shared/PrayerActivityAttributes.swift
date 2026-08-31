import ActivityKit
import Foundation

struct PrayerActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let prayerName: String
        let prayerDate: Date
        let activeUntil: Date
    }

    let locationName: String
}
