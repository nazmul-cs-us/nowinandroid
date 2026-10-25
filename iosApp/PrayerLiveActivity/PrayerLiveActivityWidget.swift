import ActivityKit
import SwiftUI
import UIKit
import WidgetKit

private let prayerDeepLink = URL(string: "deenly://prayer-times")!
private enum PrayerPalette {
    static func tint(for brand: String) -> Color {
        let pair: (light: UInt32, dark: UInt32)
        switch brand.uppercased() {
        case "DEFAULT": pair = (0x8B418F, 0xFFA9FE)
        case "ANDROID": pair = (0x006D36, 0x0EE37C)
        case "ROYAL": pair = (0x2D5DA8, 0xA8C4EE)
        default: pair = (0x3E5B41, 0x9BB99E)
        }
        return Color(uiColor: UIColor { traits in
            uiColor(hex: traits.userInterfaceStyle == .dark ? pair.dark : pair.light)
        })
    }

    private static func uiColor(hex: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 0xff) / 255,
            green: CGFloat((hex >> 8) & 0xff) / 255,
            blue: CGFloat(hex & 0xff) / 255,
            alpha: 1
        )
    }
}

/// A factual presentation that stays valid when the app is suspended. System
/// timer/progress views advance themselves; phase changes must not depend on
/// a periodic TimelineView running in a Live Activity extension.
private struct PrayerPresentation {
    let state: PrayerActivityAttributes.ContentState
    let isStale: Bool
    let now: Date
    let themeBrand: String

    init(context: ActivityViewContext<PrayerActivityAttributes>) {
        if #available(iOS 16.2, *) {
            self.init(
                state: context.state,
                isStale: context.isStale,
                themeBrand: context.attributes.themeBrand
            )
        } else {
            // iOS 16.1 has no isStale signal. hasEnded still checks the
            // configured deadline whenever the system renders the content.
            self.init(state: context.state, themeBrand: context.attributes.themeBrand)
        }
    }

    init(
        state: PrayerActivityAttributes.ContentState,
        isStale: Bool = false,
        now: Date = Date(),
        themeBrand: String = "COASTAL"
    ) {
        self.state = state
        self.isStale = isStale
        self.now = now
        self.themeBrand = themeBrand
    }

    var hasEnded: Bool { now >= state.activeUntil }
    var isUpcoming: Bool { now < state.prayerDate }
    var canShowTimer: Bool {
        !isStale && !hasEnded && state.activeUntil > state.prayerDate
    }
    var status: LocalizedStringKey {
        hasEnded ? "Prayer window ended" : "Open Deenly for an update"
    }
    var activityLabel: LocalizedStringKey { isUpcoming ? "Next prayer" : "Prayer window" }
    var timerLabel: LocalizedStringKey { isUpcoming ? "until adhan" : "remaining" }
    var tint: Color { PrayerPalette.tint(for: themeBrand) }
    var countdownInterval: ClosedRange<Date> {
        let target = isUpcoming ? state.prayerDate : state.activeUntil
        return min(now, target)...max(now, target)
    }
    var progressInterval: ClosedRange<Date> {
        if isUpcoming, let previous = state.previousPrayerDate {
            return previous...max(state.prayerDate, previous)
        }
        return state.prayerDate...max(state.activeUntil, state.prayerDate)
    }
}

private struct PrayerBrandIcon: View {
    var size: CGFloat = 16

    var body: some View {
        // A Live Activity executes in the widget extension process, so resolve
        // the mark from that bundle explicitly. Original mode also protects the
        // transparent brand artwork from the surrounding keyline tint.
        Image("DeenlyLiveGlyph", bundle: .main)
            .resizable()
            .renderingMode(.original)
            .scaledToFit()
            .frame(width: size, height: size)
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
                    .accessibilityLabel(presentation.isUpcoming ? "Time until adhan" : "Prayer window remaining")
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
    let presentation: PrayerPresentation

    var body: some View {
        HStack(spacing: 8) {
            PrayerBrandIcon(size: 20)
            VStack(alignment: .leading, spacing: 1) {
                Text(presentation.activityLabel)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(presentation.state.prayerName)
                    .font(.headline.weight(.semibold))
                    .lineLimit(1)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Secondary information belongs below the camera, not in a tall side column.
private struct PrayerActivityDetails: View {
    let presentation: PrayerPresentation
    var locationName: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if presentation.canShowTimer {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("Adhan at \(presentation.state.prayerTime)")
                    Spacer(minLength: 0)
                    if let locationName, !locationName.isEmpty {
                        Text(locationName)
                            .lineLimit(1)
                    } else {
                        Text(presentation.timerLabel)
                    }
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
            ProgressView(timerInterval: presentation.progressInterval, countsDown: true)
                .labelsHidden()
                .tint(presentation.tint)
                .accessibilityLabel(presentation.timerLabel)
        }
    }
}

struct PrayerLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PrayerActivityAttributes.self) { context in
            LockScreenPrayerActivityView(
                presentation: PrayerPresentation(context: context),
                locationName: context.attributes.locationName
            )
                .activitySystemActionForegroundColor(.primary)
                .widgetURL(prayerDeepLink)
        } dynamicIsland: { context in
            let presentation = PrayerPresentation(context: context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    PrayerIdentity(presentation: presentation)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 1) {
                        PrayerCountdown(presentation: presentation)
                            .font(.headline.weight(.semibold))
                        Text(presentation.timerLabel)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .foregroundStyle(presentation.tint)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    PrayerActivityDetails(
                        presentation: presentation,
                        locationName: context.attributes.locationName
                    )
                }
            } compactLeading: {
                HStack(spacing: 3) {
                    PrayerBrandIcon()
                    Text(context.state.prayerName)
                        .font(.caption2.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                }
                .foregroundStyle(presentation.tint)
                .frame(width: 52, alignment: .trailing)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Deenly, \(context.state.prayerName) prayer")
            } compactTrailing: {
                if presentation.canShowTimer {
                    PrayerCountdown(presentation: presentation, width: 48, showsHours: true, textAlignment: .leading)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(presentation.tint)
                } else {
                    Image(systemName: "clock.badge.exclamationmark")
                        .foregroundStyle(presentation.tint)
                        .frame(width: 48, alignment: .leading)
                        .accessibilityLabel(presentation.status)
                }
            } minimal: {
                PrayerBrandIcon(size: 18)
                    .accessibilityElement()
                    .accessibilityLabel("Deenly, \(context.state.prayerName) prayer")
            }
            // One destination for every presentation, including both compact halves.
            .widgetURL(prayerDeepLink)
            .keylineTint(presentation.tint)
        }
    }
}

private struct LockScreenPrayerActivityView: View {
    let presentation: PrayerPresentation
    var locationName: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Same identity-leading / value-trailing anchors as the expanded
            // island, with the secondary content underneath both anchors.
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 16) {
                    PrayerIdentity(presentation: presentation)
                        .fixedSize(horizontal: true, vertical: false)
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 1) {
                        PrayerCountdown(presentation: presentation)
                            .font(.title2.weight(.semibold))
                        Text(presentation.timerLabel)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .foregroundStyle(presentation.tint)
                }
                VStack(alignment: .leading, spacing: 8) {
                    PrayerIdentity(presentation: presentation)
                    PrayerCountdown(presentation: presentation, textAlignment: .leading)
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(presentation.tint)
                }
            }
            PrayerActivityDetails(presentation: presentation, locationName: locationName)
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
