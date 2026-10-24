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
        if #available(iOS 16.2, *) {
            self.init(state: context.state, isStale: context.isStale)
        } else {
            // iOS 16.1 has no isStale signal. hasEnded still checks the
            // configured deadline whenever the system renders the content.
            self.init(state: context.state)
        }
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
    var textAlignment: TextAlignment = .trailing
    @ScaledMetric(relativeTo: .title2) private var readableWidth: CGFloat = 100

    init(presentation: PrayerPresentation, width: CGFloat? = nil, showsHours: Bool = true, textAlignment: TextAlignment = .trailing) {
        self.presentation = presentation
        self.width = width
        self.showsHours = showsHours
        self.textAlignment = textAlignment
    }

    private var frameAlignment: Alignment {
        switch textAlignment {
        case .leading: return .leading
        case .center: return .center
        case .trailing: return .trailing
        }
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
        .frame(width: width ?? readableWidth, alignment: frameAlignment)
        .multilineTextAlignment(textAlignment)
    }
}

/// Shared identity anchor: upper leading in both expanded and Lock Screen.
private struct PrayerIdentity: View {
    let state: PrayerActivityAttributes.ContentState

    var body: some View {
        Label {
            Text(state.prayerName)
                .lineLimit(1)
        } icon: {
            PrayerSymbol()
        }
        .font(.headline.weight(.semibold))
        .accessibilityElement(children: .combine)
    }
}

/// Secondary information belongs below the camera, not in a tall side column.
private struct PrayerActivityDetails: View {
    let presentation: PrayerPresentation

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if presentation.canShowTimer {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Adhan at \(presentation.state.prayerTime)")
                        if !presentation.state.prayerArabicName.isEmpty {
                            Text(presentation.state.prayerArabicName)
                        }
                    }
                    Spacer(minLength: 0)
                    Text("Window ends in")
                        .multilineTextAlignment(.trailing)
                }
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                PrayerWindowProgress(presentation: presentation)
            } else {
                Text(presentation.status)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
            }
        }
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
                    PrayerCountdown(presentation: presentation)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(prayerTint)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    PrayerActivityDetails(presentation: presentation)
                }
            } compactLeading: {
                // Stay within the smaller 52.33pt reference slot. Keep the
                // identity against the camera; don't add outer padding.
                ViewThatFits(in: .horizontal) {
                    Text(context.state.prayerName)
                        .font(.caption2.weight(.semibold))
                        .lineLimit(1)
                        .fixedSize()
                    PrayerSymbol()
                        .font(.caption.weight(.semibold))
                }
                .foregroundStyle(prayerTint)
                .frame(width: 48, alignment: .trailing)
                .accessibilityLabel("\(context.state.prayerName) prayer")
            } compactTrailing: {
                if presentation.canShowTimer {
                    PrayerCountdown(presentation: presentation, width: 48, showsHours: false, textAlignment: .leading)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(prayerTint)
                } else {
                    Image(systemName: "clock.badge.exclamationmark")
                        .foregroundStyle(prayerTint)
                        .frame(width: 48, alignment: .leading)
                        .accessibilityLabel(presentation.status)
                }
            } minimal: {
                // One live value in the detached oval; no miniature card or
                // unreadable symbol-plus-bar composition. Minutes stay valid
                // for the full event without needing a scheduled unit switch.
                if presentation.canShowTimer {
                    PrayerCountdown(presentation: presentation, width: 40, showsHours: false, textAlignment: .center)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(prayerTint)
                        .accessibilityLabel("\(context.state.prayerName) prayer window ends in")
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

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Same identity-leading / value-trailing anchors as the expanded
            // island, with the secondary content underneath both anchors.
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 16) {
                    PrayerIdentity(state: presentation.state)
                        .fixedSize(horizontal: true, vertical: false)
                    Spacer(minLength: 8)
                    PrayerCountdown(presentation: presentation)
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(prayerTint)
                }
                VStack(alignment: .leading, spacing: 8) {
                    PrayerIdentity(state: presentation.state)
                    PrayerCountdown(presentation: presentation, textAlignment: .leading)
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(prayerTint)
                }
            }
            PrayerActivityDetails(presentation: presentation)
        }
        // The system supplies material, corners and StandBy scaling. Only the
        // Lock Screen layout owns this 14pt margin; island slots have no padding.
        .padding(14)
    }
}

#if DEBUG
@available(iOS 16.2, *)
private struct PrayerLiveActivityPreviews: PreviewProvider {
    private static let now = Date()
    private static let attributes = PrayerActivityAttributes(locationName: "Preview")
    private static var previewState: PrayerActivityAttributes.ContentState {
        presentation(startOffset: -300, endOffset: 600).state
    }

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
            attributes.previewContext(previewState, viewKind: .dynamicIsland(.compact))
                .previewDisplayName("Anatomy · Compact (one activity)")
            attributes.previewContext(previewState, viewKind: .dynamicIsland(.minimal))
                .previewDisplayName("Anatomy · Minimal (multiple activities)")
            attributes.previewContext(previewState, viewKind: .dynamicIsland(.expanded))
                .previewDisplayName("Anatomy · Expanded (long press)")
            attributes.previewContext(previewState, viewKind: .content)
                .previewDisplayName("Anatomy · Lock Screen")
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
    }
}
#endif
