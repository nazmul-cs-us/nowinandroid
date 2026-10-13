import ActivityKit
import SwiftUI
import WidgetKit

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
        } dynamicIsland: { context in
            let presentation = phasePresentation(for: context.state)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        Label(
                            presentation.phase.title,
                            systemImage: "moon.stars.fill"
                        )
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
                DynamicIslandExpandedRegion(.trailing) {
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
                DynamicIslandExpandedRegion(.bottom) {
                    // Wrap content tightly under the camera: one description
                    // line, then the live progress bar in the phase color.
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
            } compactLeading: {
                Image(systemName: "moon.stars.fill")
                    .foregroundStyle(presentation.phase.color)
            } compactTrailing: {
                // HIG: compact shows dynamic, essential info — the live
                // countdown, recolored by phase.
                phaseTimer(presentation, weight: .semibold)
                    .font(.caption2.weight(.semibold))
                    .monospacedDigit()
                    .minimumScaleFactor(0.6)
                    .foregroundStyle(presentation.phase.color)
            } minimal: {
                // HIG: minimal displays updated information, not just a
                // logo — the remaining time itself, like the Timer app.
                phaseTimer(presentation, weight: .semibold)
                    .font(.caption2.weight(.semibold))
                    .monospacedDigit()
                    .minimumScaleFactor(0.5)
                    .foregroundStyle(presentation.phase.color)
            }
            // HIG: tint the keyline around the Dynamic Island to match the
            // phase color so the island reads as one piece in dark mode.
            .keylineTint(presentation.phase.color)
        }
    }
}

/// Lock-screen presentation: one glanceable identity row with the live
/// timer, one progress line, and the day's schedule strip — within the
/// system's 84-160pt height budget with the standard 14pt layout margin.
private struct LockScreenPrayerActivityView: View {
    let context: ActivityViewContext<PrayerActivityAttributes>
    private var state: PrayerActivityAttributes.ContentState { context.state }

    var body: some View {
        let presentation = phasePresentation(for: state)
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 10) {
                Image(systemName: "moon.stars.fill")
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
            if !state.times.isEmpty {
                HStack(spacing: 4) {
                    ForEach(state.times, id: \.self) { time in
                        VStack(spacing: 1) {
                            Text(time.name)
                                .font(.caption2.weight(time.isNext ? .bold : .medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                            Text(time.time)
                                .font(.caption2.weight(.semibold))
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
