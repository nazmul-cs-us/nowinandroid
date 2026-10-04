import Shared
import SwiftUI
import UIKit

/// Hosts the shared Compose UI inside SwiftUI.
///
/// This is the entire iOS UI boundary. Swift owns the app lifecycle and the
/// window; everything drawn inside is Compose from `shared/commonMain`, the same
/// code that will render on Android. Adding a screen should mean writing a
/// composable in `shared/`, not another SwiftUI view here.
///
/// It passes nothing: location, date and calculation settings are all resolved
/// in Kotlin. Anything decided here would be a decision Android could not share.
struct ComposePrayerTimesView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosComposeRootKt.PrayerTimesViewController(
            sherpaService: SherpaSpeechService.shared,
            salahTfliteService: SalahTfliteServiceImpl(),
            salah3DService: SalahScene3DHost()
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Nothing to push down: the composable owns its own state.
    }
}

@available(iOS 26.0, *)
struct ComposePrayerTimesTabView: UIViewControllerRepresentable {
    let tabIndex: Int
    let onSelectTab: (Int) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        IosComposeRootKt.PrayerTimesTabViewController(
            sherpaService: SherpaSpeechService.shared,
            salahTfliteService: SalahTfliteServiceImpl(),
            salah3DService: SalahScene3DHost(),
            startBottomIndex: Int32(tabIndex),
            onSelectBottom: { index in
                onSelectTab(Int(index.int32Value))
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

@available(iOS 26.0, *)
struct NativePrayerTabsView: View {
    @State private var selectedTab = 0
    @State private var lastSelectedTab = 0
    @State private var activeVoiceTarget = 0
    @State private var morePath: [Int] = []
    @State private var pendingNudgeLabel: String?
    @State private var visibleNudgeLabel: String?
    @State private var isPreparingNudge = false
    @State private var isGeneratingNudge = false
    @State private var nudgeGenerationTask: Task<Void, Never>?

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Home", systemImage: "house", value: 0) {
                tabContent(0)
            }
            Tab("For you", systemImage: "shippingbox", value: 1) {
                tabContent(1)
            }
            Tab("Saved", systemImage: "bookmark", value: 2) {
                tabContent(2)
            }
            Tab("More", systemImage: "ellipsis", value: 5) {
                MorePrayerTabsView(
                    path: $morePath,
                    activeVoiceTarget: $activeVoiceTarget,
                    onSelectTab: selectContentTab
                )
            }
            Tab(value: 6, role: .search) {
                Color.clear
            } label: {
                Label {
                    Text(isPreparingNudge ? "Thinking" : "Voice")
                } icon: {
                    Image(systemName: isGeneratingNudge ? "sparkles" : "waveform")
                }
            }
        }
        .tabBarMinimizeBehavior(.automatic)
        .tint(Color.accentColor)
        .overlay(alignment: .bottomTrailing) {
            if let visibleNudgeLabel {
                Button {
                    IosComposeRootKt.RequestDeenlyNudgeAction()
                } label: {
                    Text(visibleNudgeLabel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.primary)
                        .lineLimit(1)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(
                            LinearGradient(
                                colors: [
                                    Color(red: 0.93, green: 0.92, blue: 1.0),
                                    Color(red: 0.91, green: 0.97, blue: 1.0),
                                    Color(red: 0.90, green: 0.98, blue: 0.95),
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            ),
                            in: Capsule()
                        )
                        .shadow(color: .black.opacity(0.16), radius: 4, y: 2)
                }
                .buttonStyle(.plain)
                .padding(.trailing, 12)
                .padding(.bottom, 82)
                .transition(
                    .scale(scale: 0.16, anchor: .bottomTrailing)
                        .combined(with: .opacity)
                )
            }
        }
        .animation(.spring(response: 0.42, dampingFraction: 0.78), value: visibleNudgeLabel)
        .onAppear {
            IosComposeRootKt.ObserveDeenlyNudge { label in
                DispatchQueue.main.async {
                    receiveNudge(label)
                }
            }
        }
        .onDisappear {
            nudgeGenerationTask?.cancel()
            IosComposeRootKt.StopObservingDeenlyNudge()
        }
        .onChange(of: selectedTab) { _, newValue in
            if newValue == 6 {
                if visibleNudgeLabel != nil {
                    IosComposeRootKt.RequestDeenlyNudgeAction()
                } else if !isPreparingNudge {
                    IosComposeRootKt.RequestVoiceSearch(tabIndex: Int32(activeVoiceTarget))
                }
                DispatchQueue.main.async {
                    selectedTab = lastSelectedTab
                }
            } else {
                lastSelectedTab = newValue
                if newValue < 3 {
                    activeVoiceTarget = newValue
                }
            }
        }
    }

    private func tabContent(_ index: Int) -> some View {
        ComposePrayerTimesTabView(tabIndex: index, onSelectTab: selectContentTab)
            .ignoresSafeArea(.all)
    }

    private func selectContentTab(_ index: Int) {
        activeVoiceTarget = index
        if index < 3 {
            selectedTab = index
        } else {
            morePath = [index]
            selectedTab = 5
        }
    }

    private func receiveNudge(_ label: String?) {
        nudgeGenerationTask?.cancel()
        pendingNudgeLabel = label
        visibleNudgeLabel = nil

        guard let label else {
            isPreparingNudge = false
            isGeneratingNudge = false
            return
        }

        isPreparingNudge = true
        isGeneratingNudge = true
        nudgeGenerationTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            guard !Task.isCancelled, pendingNudgeLabel == label else { return }
            isGeneratingNudge = false
            try? await Task.sleep(nanoseconds: 800_000_000)
            guard !Task.isCancelled, pendingNudgeLabel == label else { return }
            isPreparingNudge = false
            visibleNudgeLabel = label
        }
    }
}

@available(iOS 26.0, *)
private struct VoiceNudgeBarsGlyph: View {
    let generating: Bool

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
            let cycle = timeline.date.timeIntervalSinceReferenceDate
                .truncatingRemainder(dividingBy: 2.6) / 2.6
            ZStack {
                VoiceNudgeBarsShape()
                    .stroke(
                        Color.accentColor,
                        style: StrokeStyle(lineWidth: 2.8, lineCap: .round)
                    )
                    .opacity(generating ? 0 : 1)
                    .scaleEffect(generating ? 0.90 : 1)
                VoiceNudgeSparklesShape(
                    morph: generating ? 1 : 0,
                    phase: CGFloat(cycle * .pi * 2)
                )
                    .fill(Color.accentColor)
            }
            .animation(.easeInOut(duration: 0.70), value: generating)
        }
        .frame(width: 30, height: 30)
    }
}

@available(iOS 26.0, *)
private struct VoiceNudgeBarsShape: Shape {
    func path(in rect: CGRect) -> Path {
        let rest: [CGFloat] = [0.40, 0.62, 1.0, 0.62, 0.40]
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let spacing = rect.width * 0.15
        var path = Path()

        for (index, heightFraction) in rest.enumerated() {
            let idleX = center.x + CGFloat(index - 2) * spacing
            let idleHalfLength = max((rect.height * 0.68 * heightFraction - 2.8) / 2, 0)
            path.move(to: CGPoint(x: idleX, y: center.y - idleHalfLength))
            path.addLine(to: CGPoint(x: idleX, y: center.y + idleHalfLength))
        }
        return path
    }
}

@available(iOS 26.0, *)
private struct VoiceNudgeSparklesShape: Shape {
    var morph: CGFloat
    let phase: CGFloat

    var animatableData: CGFloat {
        get { morph }
        set { morph = newValue }
    }

    func path(in rect: CGRect) -> Path {
        guard morph > 0 else { return Path() }
        let sizes: [CGFloat] = [0.27, 0.19, 0.15]
        let rest: [CGFloat] = [0.40, 0.62, 1.0, 0.62, 0.40]
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let orbitRadius = min(rect.width, rect.height) * 0.13
        let spacing = rect.width * 0.15
        let barHalfWidth: CGFloat = 1.4
        var path = Path()

        for (index, sizeFraction) in sizes.enumerated() {
            let angle = phase + CGFloat(index) * .pi * 2 / CGFloat(sizes.count)
            let pulse = 0.72 + 0.28 * (sin(angle * 2) + 1) / 2
            let radius = min(rect.width, rect.height) * sizeFraction * pulse
            let orbitCenter = CGPoint(
                x: center.x + cos(angle) * orbitRadius,
                y: center.y + sin(angle) * orbitRadius
            )
            let targetBarIndex = index * 2
            let barCenter = CGPoint(
                x: center.x + CGFloat(targetBarIndex - 2) * spacing,
                y: center.y
            )
            let sparkleCenter = CGPoint(
                x: barCenter.x + (orbitCenter.x - barCenter.x) * morph,
                y: barCenter.y + (orbitCenter.y - barCenter.y) * morph
            )
            let barHalfLength = rect.height * 0.68 * rest[targetBarIndex] / 2
            let verticalRadius = barHalfLength + (radius - barHalfLength) * morph
            let horizontalRadius = barHalfWidth + (radius * 0.72 - barHalfWidth) * morph
            let innerRadiusX = barHalfWidth + (radius * 0.20 - barHalfWidth) * morph
            let barInnerRadiusY = max(barHalfLength - barHalfWidth, 0)
            let innerRadiusY = barInnerRadiusY + (verticalRadius * 0.20 - barInnerRadiusY) * morph
            path.move(to: CGPoint(x: sparkleCenter.x, y: sparkleCenter.y - verticalRadius))
            path.addLine(to: CGPoint(x: sparkleCenter.x + innerRadiusX, y: sparkleCenter.y - innerRadiusY))
            path.addLine(to: CGPoint(x: sparkleCenter.x + horizontalRadius, y: sparkleCenter.y))
            path.addLine(to: CGPoint(x: sparkleCenter.x + innerRadiusX, y: sparkleCenter.y + innerRadiusY))
            path.addLine(to: CGPoint(x: sparkleCenter.x, y: sparkleCenter.y + verticalRadius))
            path.addLine(to: CGPoint(x: sparkleCenter.x - innerRadiusX, y: sparkleCenter.y + innerRadiusY))
            path.addLine(to: CGPoint(x: sparkleCenter.x - horizontalRadius, y: sparkleCenter.y))
            path.addLine(to: CGPoint(x: sparkleCenter.x - innerRadiusX, y: sparkleCenter.y - innerRadiusY))
            path.closeSubpath()
        }
        return path
    }
}

@available(iOS 26.0, *)
private struct MorePrayerTabsView: View {
    @Binding var path: [Int]
    @Binding var activeVoiceTarget: Int
    let onSelectTab: (Int) -> Void

    var body: some View {
        NavigationStack(path: $path) {
            List {
                NavigationLink(value: 3) {
                    Label("Course", systemImage: "play.rectangle")
                }
                NavigationLink(value: 4) {
                    Label("Interests", systemImage: "square.grid.2x2")
                }
            }
            .navigationTitle("More")
            .navigationDestination(for: Int.self) { index in
                ComposePrayerTimesTabView(tabIndex: index, onSelectTab: onSelectTab)
                    .ignoresSafeArea(.container, edges: .bottom)
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
        .onChange(of: path) { _, newPath in
            activeVoiceTarget = newPath.last ?? 3
        }
    }
}
