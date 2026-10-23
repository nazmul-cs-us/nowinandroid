import ActivityKit
import SwiftUI
import WidgetKit

private let prayerDeepLink = URL(string: "deenly://prayer-times")!

private struct PrayerBrandIcon: View {
    var size: CGFloat = 20

    var body: some View {
        Image("AppIcon-1024")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.22))
            .accessibilityHidden(true)
    }
}

// MARK: - Phase presentation (Android parity, Apple HIG presentation)
//
// The schedule defines five presentation states. Everything the lock screen
// and Dynamic Island render derives from one presentation so the two stay
// in sync, per the HIG guidance that expanded is an enlarged version of the
// compact presentation.

private enum PrayerPhase: Equatable {
    /// Countdown to the prayer time: "Next prayer".
    case nextPrayer
    /// 0-20% of the active window: "Go to Mosque".
    case goToMosque
    /// 20-60% of the active window: "Best Time to Pray".
    case bestTimeToPray
    /// 60-100% of the active window: "Make Time for Prayer".
    case makeTime
    /// The window passed while no fresher schedule arrived (app not running).
    case awaitingUpdate

    var title: String {
        switch self {
        case .nextPrayer: "Next prayer"
        case .goToMosque: "Go to Mosque"
        case .bestTimeToPray: "Best Time to Pray"
        case .makeTime: "Make Time for Prayer"
        case .awaitingUpdate: "Prayer Time"
        }
    }

    var description: String? {
        switch self {
        case .nextPrayer: nil
        case .goToMosque: "Time to prepare and go to the mosque"
        case .bestTimeToPray: "Optimal time for prayer"
        case .makeTime: "Ensure you make time for prayer"
        case .awaitingUpdate: "Awaiting the next schedule update"
        }
    }

    var color: Color {
        switch self {
        case .nextPrayer, .awaitingUpdate: .teal
        case .goToMosque: .blue
        case .bestTimeToPray: .green
        case .makeTime: .orange
        }
    }
}

private struct PhasePresentation {
    let phase: PrayerPhase
    /// The date the live timer counts down to; nil hides the timer.
    let timerTarget: Date?
    let timerLabel: String?
}

private func phasePresentation(
    for state: PrayerActivityAttributes.ContentState,
    now: Date = Date()
) -> PhasePresentation {
    if now < state.prayerDate {
        return PhasePresentation(
            phase: .nextPrayer,
            timerTarget: state.prayerDate,
            timerLabel: "until adhan"
        )
    }
    guard now < state.activeUntil else {
        return PhasePresentation(phase: .awaitingUpdate, timerTarget: nil, timerLabel: nil)
    }
    let total = state.activeUntil.timeIntervalSince(state.prayerDate)
    guard total > 0 else {
        return PhasePresentation(phase: .awaitingUpdate, timerTarget: nil, timerLabel: nil)
    }
    let fraction = now.timeIntervalSince(state.prayerDate) / total
    let phase: PrayerPhase = switch fraction {
    case ..<0.2: .goToMosque
    case ..<0.6: .bestTimeToPray
    default: .makeTime
    }
    return PhasePresentation(
        phase: phase,
        timerTarget: state.activeUntil,
        timerLabel: "window closing"
    )
}

private struct PhaseTimeline<Content: View>: View {
    let state: PrayerActivityAttributes.ContentState
    let content: (PhasePresentation) -> Content

    init(
        state: PrayerActivityAttributes.ContentState,
        @ViewBuilder content: @escaping (PhasePresentation) -> Content
    ) {
        self.state = state
        self.content = content
    }

    var body: some View {
        // Re-evaluate the phase while the app is suspended. Text(timer:) keeps
        // counting by itself, but titles, colors, and progress branches don't.
        TimelineView(.periodic(from: .now, by: 30)) { timeline in
            content(phasePresentation(for: state, now: timeline.date))
        }
    }
}

/// The live countdown text shared by every presentation. A nil target
/// renders an em dash so layouts keep a stable footprint.
@ViewBuilder
private func phaseTimer(_ presentation: PhasePresentation, weight: Font.Weight) -> some View {
    if let target = presentation.timerTarget {
        Text(target, style: .timer)
            .monospacedDigit()
    } else {
        Text("–")
    }
}

struct PrayerLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PrayerActivityAttributes.self) { context in
            // HIG / StandBy: the lock-screen view is reused full-screen and
            // the custom background would stretch with it, so use the system
            // default material — it adapts to both appearances, blends with
            // the StandBy bezel, and survives Night Mode's red tint.
            LockScreenPrayerActivityView(context: context)
                .activitySystemActionForegroundColor(.primary)
                .widgetURL(prayerDeepLink)
        } dynamicIsland: { context in
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    PhaseTimeline(state: context.state) { presentation in
                        VStack(alignment: .leading, spacing: 2) {
                            Label {
                                Text(presentation.phase.title)
                            } icon: {
                                PrayerBrandIcon(size: 16)
                            }
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(presentation.phase.color)
                            HStack(spacing: 4) {
                                Text(context.state.prayerName)
                                    .font(.subheadline.weight(.semibold))
                                if !context.state.prayerArabicName.isEmpty {
                                    Text(context.state.prayerArabicName)
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                    .widgetURL(prayerDeepLink)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    PhaseTimeline(state: context.state) { presentation in
                        VStack(alignment: .trailing, spacing: 2) {
                            phaseTimer(presentation, weight: .semibold)
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(presentation.phase.color)
                            if let label = presentation.timerLabel {
                                Text(label)
                                    .font(.caption2.weight(.medium))
                                    .foregroundStyle(.secondary)
                            } else {
                                Text(context.state.prayerTime)
                                    .font(.caption2.weight(.medium))
                                    .monospacedDigit()
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .widgetURL(prayerDeepLink)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    // Wrap content tightly under the camera: one description
                    // line, then the live progress bar in the phase color.
                    PhaseTimeline(state: context.state) { presentation in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(
                                    presentation.phase.description
                                        ?? context.attributes.locationName
                                )
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                Spacer()
                                Text(
                                    presentation.phase.description == nil
                                        ? context.attributes.locationName
                                        : context.state.prayerTime
                                )
                                .font(.footnote)
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                            }
                            if let target = presentation.timerTarget {
                                ProgressView(
                                    timerInterval: context.state.prayerDate...max(
                                        target,
                                        context.state.prayerDate.addingTimeInterval(1)
                                    ),
                                    countsDown: false
                                )
                                .tint(presentation.phase.color)
                            }
                        }
                    }
                    .widgetURL(prayerDeepLink)
                }
            } compactLeading: {
                PrayerBrandIcon()
                    .foregroundStyle(.teal)
                    .widgetURL(prayerDeepLink)
            } compactTrailing: {
                // HIG: compact shows dynamic, essential info — the live
                // countdown, recolored by phase.
                PhaseTimeline(state: context.state) { presentation in
                    ViewThatFits(in: .horizontal) {
                        phaseTimer(presentation, weight: .semibold)
                            .font(.caption2.weight(.semibold))
                            .monospacedDigit()
                            .minimumScaleFactor(0.7)
                        Image(systemName: "clock.fill")
                            .font(.caption2.weight(.semibold))
                    }
                    .foregroundStyle(presentation.phase.color)
                }
                .widgetURL(prayerDeepLink)
            } minimal: {
                // HIG: minimal displays updated information, not just a
                // logo — the remaining time itself, like the Timer app.
                PhaseTimeline(state: context.state) { presentation in
                    ViewThatFits(in: .horizontal) {
                        phaseTimer(presentation, weight: .semibold)
                            .font(.caption2.weight(.semibold))
                            .monospacedDigit()
                            .minimumScaleFactor(0.7)
                        PrayerBrandIcon(size: 16)
                            .font(.caption2.weight(.semibold))
                    }
                    .foregroundStyle(presentation.phase.color)
                }
                .widgetURL(prayerDeepLink)
            }
            // HIG: tint the keyline around the Dynamic Island to match the
            // phase color so the island reads as one piece in dark mode.
            .keylineTint(.teal)
        }
    }
}

/// Lock-screen presentation: one glanceable identity row with the live
/// timer, one progress line, and a three-prayer summary — within the
/// system's 84-160pt height budget with the standard 14pt layout margin.
private struct LockScreenPrayerActivityView: View {
    let context: ActivityViewContext<PrayerActivityAttributes>
    private var state: PrayerActivityAttributes.ContentState { context.state }

    private var summaryTimes: [PrayerActivityAttributes.PrayerTime] {
        guard state.times.count > 3,
              let nextIndex = state.times.firstIndex(where: \.isNext) else {
            return state.times
        }
        let start = min(max(nextIndex - 1, 0), state.times.count - 3)
        return Array(state.times[start..<(start + 3)])
    }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { timeline in
            let presentation = phasePresentation(for: state, now: timeline.date)
            VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 10) {
                PrayerBrandIcon(size: 30)
                    .font(.title3.weight(.medium))
                    .foregroundStyle(presentation.phase.color)
                VStack(alignment: .leading, spacing: 2) {
                    Text(presentation.phase.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(presentation.phase.color)
                    HStack(spacing: 5) {
                        Text(state.prayerName)
                            .font(.headline.weight(.semibold))
                        if !state.prayerArabicName.isEmpty {
                            Text(state.prayerArabicName)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.secondary)
                        }
                        Text(state.prayerTime)
                            .font(.subheadline.weight(.semibold))
                            .monospacedDigit()
                    }
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    phaseTimer(presentation, weight: .bold)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(presentation.phase.color)
                    Text(presentation.timerLabel ?? state.prayerTime)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.secondary)
                }
            }
            // Approach bar: fills the segment from the previous prayer to
            // the next before adhan, then the active window in phase color.
            if presentation.phase == .nextPrayer,
               let previous = state.previousPrayerDate,
               previous < state.prayerDate {
                ProgressView(
                    timerInterval: previous...state.prayerDate,
                    countsDown: false
                )
                .tint(presentation.phase.color)
            } else if let target = presentation.timerTarget {
                ProgressView(
                    timerInterval: context.state.prayerDate...max(
                        target,
                        context.state.prayerDate.addingTimeInterval(1)
                    ),
                    countsDown: false
                )
                .tint(presentation.phase.color)
            }
            // Today's schedule strip: the glanceable summary of the day,
            // with the next prayer highlighted in the phase tint.
            if !summaryTimes.isEmpty {
                HStack(spacing: 4) {
                    ForEach(summaryTimes, id: \.self) { time in
                        VStack(spacing: 1) {
                            Text(time.name)
                                .font(.footnote.weight(time.isNext ? .bold : .medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                            Text(time.time)
                                .font(.caption.weight(.semibold))
                                .monospacedDigit()
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        .foregroundStyle(time.isNext ? Color.primary : Color.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 3)
                        .background(
                            time.isNext
                                ? presentation.phase.color.opacity(0.18)
                                : Color.secondary.opacity(0.10),
                            in: RoundedRectangle(cornerRadius: 6)
                        )
                    }
                }
            }
            }
            .padding(14)
        }
    }
}
