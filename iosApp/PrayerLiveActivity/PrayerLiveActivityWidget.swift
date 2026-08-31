import ActivityKit
import SwiftUI
import WidgetKit

struct PrayerLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PrayerActivityAttributes.self) { context in
            HStack(spacing: 12) {
                Image(systemName: "moon.stars.fill")
                    .font(.title2)
                    .foregroundStyle(.teal)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Next prayer")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(context.state.prayerName)
                        .font(.headline)
                    Text(context.attributes.locationName)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                Text(context.state.prayerDate, style: .timer)
                    .font(.title3.monospacedDigit().weight(.semibold))
                    .foregroundStyle(.teal)
            }
            .padding()
            .activityBackgroundTint(Color(uiColor: .secondarySystemBackground))
            .activitySystemActionForegroundColor(.primary)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.state.prayerName, systemImage: "moon.stars.fill")
                        .foregroundStyle(.teal)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.prayerDate, style: .timer)
                        .monospacedDigit()
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Text("Prayer time")
                        Spacer()
                        Text(context.attributes.locationName).lineLimit(1)
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "moon.stars.fill").foregroundStyle(.teal)
            } compactTrailing: {
                Text(context.state.prayerDate, style: .timer).monospacedDigit()
            } minimal: {
                Image(systemName: "moon.stars.fill").foregroundStyle(.teal)
            }
        }
    }
}
