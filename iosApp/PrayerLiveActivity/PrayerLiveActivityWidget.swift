import ActivityKit
import SwiftUI
import WidgetKit

private let prayerDeepLink = URL(string: "deenly://prayer-times")!
private let prayerTint = Color.teal

/// A factual presentation that stays valid when the app is suspended. System
/// timer/progress views advance themselves; phase changes must not depend on
/// a periodic TimelineView running in a Live Activity extension.
private struct PrayerPresentation {
    let state: PrayerActivityAttributes.ContentState
    let isStale: Bool
    let now: Date

    init(context: ActivityViewContext<PrayerActivityAttributes>) {
        self.init(state: context.state, isStale: context.isStale)
    }

    init(state: PrayerActivityAttributes.ContentState, isStale: Bool = false, now: Date = Date()) {
        self.state = state
        self.isStale = isStale
        self.now = now
    }

    var hasEnded: Bool { now >= state.activeUntil }
    var canShowTimer: Bool {
        !isStale && !hasEnded && state.activeUntil > state.prayerDate
    }
    var status: LocalizedStringKey {
        hasEnded ? "Prayer window ended" : "Open Deenly for an update"
    }
    var countdownInterval: ClosedRange<Date> {
        min(now, state.prayerDate)...max(state.activeUntil, min(now, state.prayerDate))
    }
    var windowInterval: ClosedRange<Date> {
        state.prayerDate...max(state.activeUntil, state.prayerDate)
    }
}

private struct PrayerSymbol: View {
    var body: some View {
        // A content symbol without an app-icon container, as recommended by HIG.
        Image(systemName: "moon.stars.fill")
            .foregroundStyle(prayerTint)
            .accessibilityHidden(true)
    }
}

private struct PrayerCountdown: View {
    let presentation: PrayerPresentation
    var width: CGFloat? = nil
    var showsHours = true
    @ScaledMetric(relativeTo: .title2) private var readableWidth: CGFloat = 100

    init(presentation: PrayerPresentation, width: CGFloat? = nil, showsHours: Bool = true) {
        self.presentation = presentation
        self.width = width
        self.showsHours = showsHours
    }

    var body: some View {
        Group {
            if presentation.canShowTimer {
                Text(timerInterval: presentation.countdownInterval, countsDown: true, showsHours: showsHours)
                    .monospacedDigit()
                    .accessibilityLabel("Prayer window ends in")
            } else {
                Text("–")
                    .accessibilityLabel(presentation.status)
            }
        }
        // Timer text is horizontally flexible in an archived widget. Reserve
        // its full width instead of relying on scale-to-fit or ViewThatFits.
        .frame(width: width ?? readableWidth, alignment: .trailing)
        .multilineTextAlignment(.trailing)
    }
}

private struct PrayerIdentity: View {
    let state: PrayerActivityAttributes.ContentState

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            PrayerSymbol()
                .font(.headline)
            VStack(alignment: .leading, spacing: 3) {
                Text(state.prayerName)
                    .font(.headline.weight(.semibold))
                if !state.prayerArabicName.isEmpty {
                    Text(state.prayerArabicName)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                Text("Adhan at \(state.prayerTime)")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct PrayerWindowProgress: View {
    let presentation: PrayerPresentation

    var body: some View {
        if presentation.canShowTimer {
            ProgressView(timerInterval: presentation.windowInterval, countsDown: true)
                .labelsHidden()
                .tint(prayerTint)
                .accessibilityLabel("Prayer window remaining")
        }
    }
}

struct PrayerLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PrayerActivityAttributes.self) { context in
            LockScreenPrayerActivityView(presentation: PrayerPresentation(context: context))
                .activitySystemActionForegroundColor(.primary)
                .widgetURL(prayerDeepLink)
        } dynamicIsland: { context in
            let presentation = PrayerPresentation(context: context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    PrayerIdentity(state: context.state)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 4) {
                        PrayerCountdown(presentation: presentation)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(prayerTint)
                        Text(presentation.canShowTimer ? "Window ends in" : "Update needed")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if presentation.canShowTimer {
                        PrayerWindowProgress(presentation: presentation)
                    } else {
                        Text(presentation.status)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                }
            } compactLeading: {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 4) {
                        PrayerSymbol()
                        Text(context.state.prayerName)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .fixedSize()
                    }
                    PrayerSymbol()
                }
                .accessibilityLabel("\(context.state.prayerName) prayer")
            } compactTrailing: {
                if presentation.canShowTimer {
                    PrayerCountdown(presentation: presentation, width: 56, showsHours: false)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(prayerTint)
                } else {
                    Image(systemName: "clock.badge.exclamationmark")
                        .foregroundStyle(prayerTint)
                        .accessibilityLabel(presentation.status)
                }
            } minimal: {
                if presentation.canShowTimer {
                    VStack(spacing: 2) {
                        PrayerSymbol()
                            .font(.caption.weight(.semibold))
                        PrayerWindowProgress(presentation: presentation)
                            .frame(width: 20)
                    }
                    .frame(width: 24, height: 24)
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("\(context.state.prayerName) prayer window")
                } else {
                    Image(systemName: "clock.badge.exclamationmark")
                        .foregroundStyle(prayerTint)
                        .accessibilityLabel(presentation.status)
                }
            }
            // One destination for every presentation, including both compact halves.
            .widgetURL(prayerDeepLink)
            .keylineTint(prayerTint)
        }
    }
}

private struct LockScreenPrayerActivityView: View {
    let presentation: PrayerPresentation

    private var countdown: some View {
        VStack(alignment: .trailing, spacing: 4) {
            PrayerCountdown(presentation: presentation)
                .font(.title2.weight(.semibold))
                .foregroundStyle(prayerTint)
            Text("Window ends in")
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if presentation.canShowTimer {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .top, spacing: 16) {
                        PrayerIdentity(state: presentation.state)
                            .fixedSize(horizontal: true, vertical: false)
                        Spacer(minLength: 8)
                        countdown
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        PrayerIdentity(state: presentation.state)
                        countdown
                    }
                }
                PrayerWindowProgress(presentation: presentation)
            } else {
                PrayerIdentity(state: presentation.state)
                Text(presentation.status)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
            }
        }
        // Keep the system material for light/dark, Always-On and StandBy.
        // Do not disclose precise location or squeeze in a full-day schedule.
        .padding(14)
    }
}

#if DEBUG
private struct PrayerLiveActivityPreviews: PreviewProvider {
    private static let now = Date()

    private static func presentation(startOffset: TimeInterval, endOffset: TimeInterval, stale: Bool = false) -> PrayerPresentation {
        PrayerPresentation(
            state: PrayerActivityAttributes.ContentState(
                prayerName: "Dhuhr", prayerArabicName: "الظهر", prayerTime: "12:35 PM",
                prayerDate: now.addingTimeInterval(startOffset), previousPrayerDate: nil,
                activeUntil: now.addingTimeInterval(endOffset), times: []
            ),
            isStale: stale, now: now
        )
    }

    static var previews: some View {
        Group {
            LockScreenPrayerActivityView(presentation: presentation(startOffset: 1200, endOffset: 2100))
                .preferredColorScheme(.light)
                .previewDisplayName("Upcoming · Light")
            LockScreenPrayerActivityView(presentation: presentation(startOffset: -300, endOffset: 600))
                .preferredColorScheme(.dark)
                .previewDisplayName("Prayer window · Dark")
            LockScreenPrayerActivityView(presentation: presentation(startOffset: -1800, endOffset: -900, stale: true))
                .previewDisplayName("Expired")
            LockScreenPrayerActivityView(presentation: presentation(startOffset: 1200, endOffset: 2100, stale: true))
                .previewDisplayName("Stale before deadline")
            LockScreenPrayerActivityView(presentation: presentation(startOffset: -300, endOffset: 600))
                .environment(\.dynamicTypeSize, .accessibility1)
                .previewDisplayName("Large text")
            LockScreenPrayerActivityView(presentation: presentation(startOffset: -300, endOffset: 600))
                .environment(\.layoutDirection, .rightToLeft)
                .previewDisplayName("Right to left")
        }
        .previewLayout(.fixed(width: 360, height: 160))
    }
}
#endif
