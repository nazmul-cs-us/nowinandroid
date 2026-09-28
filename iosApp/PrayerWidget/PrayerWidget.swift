//
//  Copyright 2026 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import SwiftUI
import WidgetKit

/// The home-screen prayer widget — the iOS counterpart of Android's prayer
/// widgets. Reads the schedule payload the app publishes into the shared
/// app group (the same JSON the notification coordinator and Live Activity
/// consume) and renders the next prayer with a countdown plus the day's
/// five times, refreshing its timeline at each prayer boundary.
@main
struct PrayerWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextPrayerWidget()
    }
}

// MARK: - Payload (same shape as the Kotlin IosPrayerSchedulePublisher)

private struct PrayerPayload: Codable {
    let version: Int
    let locationName: String
    let timeZoneOffset: Double
    let days: [PayloadDay]
}

private struct PayloadDay: Codable {
    let date: String
    let prayers: [PayloadPrayer]
}

private struct PayloadPrayer: Codable {
    let name: String
    let hour: Int
    let minute: Int
}

// MARK: - Timeline

private struct PrayerEntry: TimelineEntry {
    let date: Date
    let locationName: String
    let nextPrayerName: String
    let nextPrayerDate: Date
    let times: [(name: String, time: Date, isNext: Bool)]

    var timeUntil: String {
        let interval = nextPrayerDate.timeIntervalSince(date)
        if interval <= 0 { return "now" }
        let hours = Int(interval) / 3600
        let minutes = (Int(interval) % 3600) / 60
        return hours > 0 ? "\(hours)h \(minutes)m" : "\(minutes)m"
    }
}

private struct PrayerProvider: TimelineProvider {
    static let appGroup = "group.com.starception.submission"
    static let payloadKey = "ios_prayer_schedule_payload"

    func placeholder(in context: Context) -> PrayerEntry {
        PrayerEntry(
            date: Date(),
            locationName: "Dubai",
            nextPrayerName: "Asr",
            nextPrayerDate: Date().addingTimeInterval(600),
            times: []
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (PrayerEntry) -> Void) {
        completion(currentEntry() ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PrayerEntry>) -> Void) {
        guard let entry = currentEntry() else {
            // No schedule yet — try again in an hour.
            let refresh = Calendar.current.date(byAdding: .hour, value: 1, to: Date())!
            completion(Timeline(entries: [placeholder(in: context)], policy: .after(refresh)))
            return
        }
        // Refresh shortly after the next prayer passes.
        let refresh = entry.nextPrayerDate.addingTimeInterval(90)
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }

    private func currentEntry() -> PrayerEntry? {
        let defaults = UserDefaults(suiteName: Self.appGroup)
        guard let encoded = defaults?.string(forKey: Self.payloadKey),
              let data = encoded.data(using: .utf8),
              let payload = try? JSONDecoder().decode(PrayerPayload.self, from: data),
              payload.version == 1 else { return nil }

        let timeZone = TimeZone(secondsFromGMT: Int(payload.timeZoneOffset * 3600))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone ?? .current

        let now = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = timeZone ?? .current
        let todayKey = formatter.string(from: now)

        guard let day = payload.days.first(where: { $0.date == todayKey }) else { return nil }

        var dated: [(name: String, time: Date, isNext: Bool)] = []
        for prayer in day.prayers where prayer.name != "Sunrise" {
            var components = DateComponents()
            components.calendar = calendar
            components.timeZone = timeZone ?? .current
            components.year = Int(todayKey.prefix(4))
            components.month = Int(todayKey.dropFirst(5).prefix(2))
            components.day = Int(todayKey.suffix(2))
            components.hour = prayer.hour
            components.minute = prayer.minute
            guard let date = calendar.date(from: components) else { continue }
            dated.append((prayer.name, date, false))
        }
        let upcoming = dated.filter { $0.time > now }.min(by: { $0.time < $1.time })
        if let upcoming {
            if let index = dated.firstIndex(where: { $0.name == upcoming.name && !$0.isNext }) {
                dated[index].isNext = true
            }
        }
        let next = upcoming ?? dated.last
        return PrayerEntry(
            date: now,
            locationName: payload.locationName,
            nextPrayerName: next?.name ?? "—",
            nextPrayerDate: next?.time ?? now,
            times: dated
        )
    }
}

// MARK: - Views

private struct NextPrayerEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PrayerEntry

    var body: some View {
        switch family {
        case .systemSmall:
            small
        default:
            medium
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            Spacer(minLength: 0)
            Text("NEXT PRAYER")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(entry.nextPrayerName)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .minimumScaleFactor(0.6)
            Text(entry.timeUntil)
                .font(.title3.weight(.semibold))
                .foregroundStyle(.tint)
            Spacer(minLength: 0)
            Text(entry.locationName)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetBackgroundCompat()
    }

    private var medium: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Spacer(minLength: 0)
                Text("NEXT PRAYER")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text(entry.nextPrayerName)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .minimumScaleFactor(0.6)
                Text(entry.timeUntil)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.tint)
                Spacer(minLength: 0)
                Text(entry.locationName)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            VStack(alignment: .trailing, spacing: 3) {
                ForEach(entry.times, id: \.name) { time in
                    HStack(spacing: 6) {
                        Text(time.name)
                            .font(.caption.weight(time.isNext ? .bold : .regular))
                            .foregroundStyle(time.isNext ? Color.primary : Color.secondary)
                        Text(time.time, style: .time)
                            .font(.caption.weight(time.isNext ? .bold : .regular))
                            .foregroundStyle(time.isNext ? Color.primary : Color.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .widgetBackgroundCompat()
    }
}

extension View {
    /// iOS 17 requires containerBackground; older families fall back.
    @ViewBuilder
    func widgetBackgroundCompat() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(for: .widget) { Color(uiColor: .systemBackground) }
        } else {
            padding(10)
        }
    }
}

struct NextPrayerWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NextPrayerWidget", provider: PrayerProvider()) { entry in
            NextPrayerEntryView(entry: entry)
        }
        .configurationDisplayName("Prayer Times")
        .description("The next prayer with a countdown and today's full schedule.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
