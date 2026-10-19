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
        // The Deenly GGUF inference service the shared suggestion flows call.
        DeenlyKnowledgeRuntime.shared.service = DeenlyKnowledgeServiceImpl.shared
        return IosComposeRootKt.PrayerTimesViewController(
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
        // The Deenly GGUF inference service the shared suggestion flows call.
        DeenlyKnowledgeRuntime.shared.service = DeenlyKnowledgeServiceImpl.shared
        return IosComposeRootKt.PrayerTimesTabViewController(
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

// The Now Nudge bot mirrors Android's VoiceAssistantButton: a circular voice
// button that long-press or pull-down turns into a thinking pill and then a
// suggestion card; a tap opens the typed-question composer. Turns are owned by
// the shared NowNudgeBus so the pacing, payloads, and quiz routing match
// Android's VoiceAssistantNudgeBus exactly.

/// Fields of the shared `DeenlyNudge` the bot surfaces render.
struct NudgePayload: Identifiable {
    enum Kind {
        case markPrayed
        case openQibla
        case playTravelDua
        case playQuiz
        case openRecommendation
    }

    let id: String
    let kind: Kind
    let label: String
    let supportingText: String?
    let sourceLabel: String?

    init?(from nudge: DeenlyNudge?) {
        guard let nudge else { return nil }
        id = nudge.id
        label = nudge.label
        supportingText = nudge.supportingText
        sourceLabel = nudge.sourceLabel
        let action = nudge.action
        if action == DeenlyNudgeAction.markPrayed {
            kind = .markPrayed
        } else if action == DeenlyNudgeAction.openQibla {
            kind = .openQibla
        } else if action == DeenlyNudgeAction.playTravelDua {
            kind = .playTravelDua
        } else if action == DeenlyNudgeAction.playQuiz {
            kind = .playQuiz
        } else {
            kind = .openRecommendation
        }
    }
}

/// Surfaces of the bot, in Android's VoiceAssistantSurface order. The quiz
/// lives in shared Compose, so it is absent here.
@available(iOS 26.0, *)
enum NowNudgeSurface {
    case button
    case composer
    case modelMissing
    case thinking
    case nudge
}

@MainActor
@available(iOS 26.0, *)
final class NowNudgeBotModel: ObservableObject {
    @Published var composerOpen = false
    @Published var busGenerating = false
    @Published var presented: NudgePayload?
    @Published var renderedPayload: NudgePayload?
    @Published var thinkingMessageIndex = 0
    @Published var thinkingVerbRotation: [String] = []
    @Published var composerSuggestions: [String] = []
    @Published var modelMissing = false

    // @Published: the reveal task writes these off the turn flow, and the
    // surfaces recompute from them — without the publish the thinking pill
    // never re-renders into the revealed card.
    @Published private var requestedNudgeId: String?
    @Published private var revealedNudgeId: String?
    private var revealTask: Task<Void, Never>?
    private var thinkingTask: Task<Void, Never>?
    private var renderedHoldTask: Task<Void, Never>?
    private var suggestionsLoaded = false
    private let selectionHaptic = UISelectionFeedbackGenerator()
    private let activationHaptic = UIImpactFeedbackGenerator(style: .heavy)

    var canPresentNudge: Bool {
        presented != nil && requestedNudgeId == presented?.id
    }

    var isPreparingNudge: Bool {
        canPresentNudge && revealedNudgeId != presented?.id
    }

    var isGeneratingNudge: Bool {
        busGenerating || isPreparingNudge
    }

    var showNudge: Bool {
        canPresentNudge && revealedNudgeId == presented?.id
    }

    var surface: NowNudgeSurface {
        if composerOpen {
            .composer
        } else if modelMissing {
            .modelMissing
        } else if isGeneratingNudge {
            .thinking
        } else if showNudge {
            .nudge
        } else {
            .button
        }
    }

    func start() {
        IosComposeRootKt.ObserveDeenlyNudge { nudge in
            DispatchQueue.main.async {
                self.receivePresented(NudgePayload(from: nudge))
            }
        }
        IosComposeRootKt.ObserveGeneratingSuggestion { generating in
            DispatchQueue.main.async {
                self.busGenerating = generating.boolValue
                if generating.boolValue { self.beginThinkingMessages() }
            }
        }
        IosComposeRootKt.ObserveSuggestionReady { nudgeId in
            DispatchQueue.main.async {
                self.receiveSuggestionReady(nudgeId)
            }
        }
    }

    func stop() {
        revealTask?.cancel()
        thinkingTask?.cancel()
        renderedHoldTask?.cancel()
        IosComposeRootKt.StopObservingDeenlyNudge()
    }

    func requestAmbientSuggestion() {
        guard !busGenerating else { return }
        guard IosComposeRootKt.IsDeenlyKnowledgeModelAvailable() else {
            composerOpen = false
            modelMissing = true
            return
        }
        composerOpen = false
        IosComposeRootKt.RequestDeenlyNudgeSuggestion(prompt: nil)
    }

    func openComposer() {
        guard surface == .button, !busGenerating else { return }
        guard IosComposeRootKt.IsDeenlyKnowledgeModelAvailable() else {
            modelMissing = true
            return
        }
        composerOpen = true
        if !suggestionsLoaded {
            suggestionsLoaded = true
            IosComposeRootKt.RequestComposerSuggestions { titles in
                DispatchQueue.main.async {
                    self.composerSuggestions = titles
                }
            }
        }
    }

    func closeComposer() {
        composerOpen = false
    }

    func closeModelMissing() {
        modelMissing = false
    }

    func startModelDownload() {
        modelMissing = false
        IosComposeRootKt.StartDeenlyModelDownload()
    }

    func submitComposerQuestion(_ question: String) {
        let trimmed = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        guard IosComposeRootKt.IsDeenlyKnowledgeModelAvailable() else {
            composerOpen = false
            modelMissing = true
            return
        }
        composerOpen = false
        IosComposeRootKt.RequestDeenlyNudgeSuggestion(prompt: trimmed)
    }

    func cancelSuggestion() {
        IosComposeRootKt.CancelDeenlyNudgeSuggestion()
    }

    func performNudgeAction() {
        guard showNudge else { return }
        IosComposeRootKt.RequestDeenlyNudgeAction()
    }

    func dismissNudge() {
        guard showNudge else { return }
        IosComposeRootKt.RequestDeenlyNudgeDismiss()
    }

    func pullHapticStep() {
        selectionHaptic.selectionChanged()
    }

    func pullActivationHaptic() {
        activationHaptic.impactOccurred()
    }

    func thinkingMessage(defaultActionKind: NudgePayload.Kind?) -> String {
        let informative = [
            "Understanding context",
            "Finding trusted source",
            "Verifying source",
            {
                switch defaultActionKind {
                case .playQuiz: "Composing question"
                case .openRecommendation: "Preparing insight"
                default: "Preparing suggestion"
                }
            }(),
        ]
        let index = thinkingMessageIndex
        if index < informative.count {
            return informative[index]
        }
        let verbs = thinkingVerbRotation
        if verbs.isEmpty { return informative.last ?? "Preparing suggestion" }
        return verbs[(index - informative.count) % verbs.count]
    }

    private func receivePresented(_ payload: NudgePayload?) {
        presented = payload
        if let payload {
            renderedPayload = payload
            renderedHoldTask?.cancel()
        } else {
            requestedNudgeId = nil
            revealedNudgeId = nil
            revealTask?.cancel()
            // Keep the outgoing card's content stable until its morph back to
            // the bot completes, matching Android's return-content hold.
            renderedHoldTask?.cancel()
            renderedHoldTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 460_000_000)
                if !Task.isCancelled {
                    renderedPayload = nil
                }
            }
        }
    }

    private func receiveSuggestionReady(_ nudgeId: String) {
        requestedNudgeId = nudgeId
        revealedNudgeId = nil
        revealTask?.cancel()
        revealTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 5_600_000_000)
            guard !Task.isCancelled else { return }
            revealedNudgeId = nudgeId
        }
    }

    private func beginThinkingMessages() {
        thinkingVerbRotation = NowNudgeThinkingVerbs.shuffled()
        thinkingMessageIndex = 0
        thinkingTask?.cancel()
        thinkingTask = Task { @MainActor in
            while isGeneratingNudge {
                try? await Task.sleep(nanoseconds: 1_400_000_000)
                guard !Task.isCancelled, isGeneratingNudge else { return }
                thinkingMessageIndex += 1
            }
        }
    }
}

/// The bot layer: the transient bot surfaces (composer, thinking pill,
/// nudge card) anchored bottom-trailing above the native tab bar. The idle
/// affordance is the tab bar's trailing Voice item — the slot Android's
/// app-shell bar gives its voice button.
@available(iOS 26.0, *)
private struct NowNudgeBotLayer: View {
    @ObservedObject var model: NowNudgeBotModel
    @Environment(\.colorScheme) private var colorScheme
    @Namespace private var containerNamespace
    @State private var glowPulse = false

    private let buttonSize: CGFloat = 60

    private var nudgeGradient: LinearGradient {
        if colorScheme == .dark {
            LinearGradient(
                colors: [
                    Color(red: 0.188, green: 0.188, blue: 0.267),
                    Color(red: 0.145, green: 0.220, blue: 0.243),
                    Color(red: 0.153, green: 0.239, blue: 0.220),
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
        } else {
            LinearGradient(
                colors: [
                    Color(red: 0.929, green: 0.918, blue: 1.0),
                    Color(red: 0.910, green: 0.969, blue: 1.0),
                    Color(red: 0.902, green: 0.984, blue: 0.949),
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
        }
    }

    private var glowColor: Color {
        Color.accentColor
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            switch model.surface {
            case .button:
                EmptyView()
            case .composer:
                composerCard
                    .offset(y: -(buttonSize + 8))
                    .transition(.opacity)
            case .modelMissing:
                missingModelCard
                    .offset(y: -(buttonSize + 8))
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
            case .thinking:
                thinkingPill
                    .offset(y: -(buttonSize + 8))
                    .transition(.opacity)
            case .nudge:
                nudgeCard
                    .offset(y: -(buttonSize + 8))
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.42), value: model.surface)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.7).repeatForever(autoreverses: true)) {
                glowPulse = true
            }
        }
    }

    // MARK: Composer

    private var composerCard: some View {
        ComposerSurface(
            model: model,
            gradient: nudgeGradient,
            glowColor: glowColor,
            glowPulse: glowPulse
        )
        .matchedGeometryEffect(id: "voiceContainer", in: containerNamespace)
    }

    private var missingModelCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Download Now Nudge model")
                        .font(.system(size: 14, weight: .semibold))
                    Text("On-device AI needs a one-time model download.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    model.closeModelMissing()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.secondary)
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            Button {
                model.startModelDownload()
            } label: {
                Label("Download model", systemImage: "arrow.down.circle.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(14)
        .frame(width: 300)
        .background(nudgeGradient)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: glowColor.opacity(0.28), radius: 12, y: 4)
        .matchedGeometryEffect(id: "voiceContainer", in: containerNamespace)
    }

    // MARK: Thinking

    private var thinkingPill: some View {
        let message = model.thinkingMessage(defaultActionKind: model.presented?.kind)
        let pillWidth = textWidth(message, font: thinkingFont) + 66
        return HStack(spacing: 3) {
            VoiceNudgeSparkleGlyph(color: glowColor)
                .frame(width: 30, height: 30)
            Text(message)
                .font(thinkingFont)
                .lineLimit(1)
                .truncationMode(.tail)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .frame(minHeight: 44)
        .background(nudgeGradient)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .frame(width: pillWidth)
        .shadow(color: glowColor.opacity(glowPulse ? 0.44 : 0.34), radius: 11)
        .gesture(
            DragGesture(minimumDistance: 12)
                .onChanged { value in
                    guard model.surface == .thinking, value.translation.height > 0 else { return }
                    if value.translation.height >= 56 {
                        model.pullActivationHaptic()
                        model.cancelSuggestion()
                    }
                }
                .onEnded { _ in }
        )
        .accessibilityLabel(message)
        .matchedGeometryEffect(id: "voiceContainer", in: containerNamespace)
    }

    private var thinkingFont: Font {
        .system(size: 14, weight: .semibold)
    }

    // MARK: Nudge card

    private var nudgeCard: some View {
        NudgeCardSurface(
            payload: model.renderedPayload,
            gradient: nudgeGradient,
            glowColor: glowColor,
            glowPulse: glowPulse,
            onAction: { model.performNudgeAction() },
            onDismiss: { model.dismissNudge() }
        )
        .matchedGeometryEffect(id: "voiceContainer", in: containerNamespace)
    }

    // MARK: Helpers

    private func textWidth(_ text: String, font: Font) -> CGFloat {
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 14, weight: .semibold),
        ]
        return (text as NSString).size(withAttributes: attributes).width
    }
}

/// "Ask Now Nudge" — the typed-question composer with Fortress chapter chips.
@available(iOS 26.0, *)
private struct ComposerSurface: View {
    @ObservedObject var model: NowNudgeBotModel
    let gradient: LinearGradient
    let glowColor: Color
    let glowPulse: Bool

    @State private var typedQuestion = ""
    @FocusState private var fieldFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Ask Now Nudge")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.primary)
                    Text("Answers use your downloaded Quran, Hadith, and dua sources")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    model.closeComposer()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.secondary)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }

            HStack(spacing: 8) {
                TextField("For example: fasting dua", text: $typedQuestion, axis: .vertical)
                    .font(.subheadline)
                    .lineLimit(3)
                    .focused($fieldFocused)
                    .submitLabel(.send)
                    .onSubmit(submit)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color.primary.opacity(0.06))
                    )

                Button(action: submit) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 26))
                        .foregroundStyle(Color.accentColor)
                }
                .buttonStyle(.plain)
                .disabled(typedQuestion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityLabel("Send question")
            }

            if typedQuestion.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        // The ambient-nudge summon the retired floating circle's
                        // long-press used to provide.
                        Button {
                            model.requestAmbientSuggestion()
                        } label: {
                            Label("Surprise me", systemImage: "sparkles")
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundStyle(Color.accentColor)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule().fill(Color.accentColor.opacity(0.12))
                                )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("surpriseMeChip")
                        ForEach(model.composerSuggestions, id: \.self) { suggestion in
                            Button {
                                typedQuestion = suggestion
                            } label: {
                                Text(suggestion)
                                    .font(.caption)
                                    .fontWeight(.medium)
                                    .foregroundStyle(.secondary)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(
                                        Capsule().fill(Color.primary.opacity(0.08))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(14)
        .frame(width: min(360, UIScreen.main.bounds.width - 24), alignment: .leading)
        .background(gradient)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: glowColor.opacity(glowPulse ? 0.44 : 0.34), radius: 11)
        .task {
            try? await Task.sleep(nanoseconds: 420_000_000)
            fieldFocused = true
        }
    }

    private func submit() {
        let question = typedQuestion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !question.isEmpty else { return }
        typedQuestion = ""
        fieldFocused = false
        model.submitComposerQuestion(question)
    }
}

/// The suggestion card: action thumbnail, NOW NUDGE header, label, verified
/// quote, and the source chip that performs the action.
@available(iOS 26.0, *)
private struct NudgeCardSurface: View {
    let payload: NudgePayload?
    let gradient: LinearGradient
    let glowColor: Color
    let glowPulse: Bool
    let onAction: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                actionThumbnail
                    .frame(width: 36, height: 36)
                Spacer().frame(width: 10)
                Text("NOW NUDGE")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundStyle(Color.accentColor)
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.secondary)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Dismiss suggestion")
            }

            Text(payload?.label ?? "")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.primary)

            supportingBody

            if let sourceLabel = payload?.sourceLabel, !sourceLabel.isBlank {
                Button(action: onAction) {
                    HStack(spacing: 4) {
                        Text(sourceLabel)
                            .font(.system(size: 14, weight: .semibold))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundStyle(Color.accentColor)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color.accentColor.opacity(0.10))
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Open source")
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(width: min(360, UIScreen.main.bounds.width - 24), alignment: .leading)
        .frame(minHeight: 44, alignment: .leading)
        .background(gradient)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: glowColor.opacity(glowPulse ? 0.44 : 0.34), radius: 11)
        .contentShape(Rectangle())
        .onTapGesture(perform: onAction)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("nowNudgeCard")
        .accessibilityLabel(
            [payload?.label, payload?.supportingText, payload?.sourceLabel]
                .compactMap(\.self)
                .joined(separator: ". ")
        )
    }

    private var actionThumbnail: some View {
        RoundedRectangle(cornerRadius: 9, style: .continuous)
            .fill(Color.accentColor.opacity(0.15))
            .overlay(
                Image(systemName: actionIcon)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
            )
    }

    private var actionIcon: String {
        switch payload?.kind {
        case .markPrayed: "checkmark"
        case .playTravelDua: "airplane"
        case .playQuiz: "book.fill"
        case .openQibla, .openRecommendation, nil: "bolt.fill"
        }
    }

    @ViewBuilder
    private var supportingBody: some View {
        if let body = payload?.supportingText, !body.isBlank {
            let answerQuote = body.substringBefore("\n\n")
            let sourceBody = body.substringAfter("\n\n")
            let isAnswerCard = !sourceBody.isBlank &&
                answerQuote.count <= 140 &&
                answerQuote.hasPrefix("\u{201C}")
            if isAnswerCard {
                VStack(alignment: .leading, spacing: 4) {
                    Text(answerQuote)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.accentColor)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color.accentColor.opacity(0.08))
                        )
                    if !sourceBody.isBlank {
                        Text(sourceBody)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            } else {
                Text(body)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

extension String {
    var isBlank: Bool {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func substringBefore(_ separator: String) -> String {
        guard let range = range(of: separator) else { return self }
        return String(self[..<range.lowerBound])
    }

    func substringAfter(_ separator: String) -> String {
        guard let range = range(of: separator) else { return "" }
        return String(self[range.upperBound...])
    }
}

@available(iOS 26.0, *)
struct NativePrayerTabsView: View {
    @State private var selectedTab = 0
    @State private var morePath: [Int] = []
    @State private var lastSelectedTab = 0
    @StateObject private var bot = NowNudgeBotModel()

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Home", systemImage: "house", value: 0) {
                tabContent(0)
            }
            Tab("Saved", systemImage: "bookmark", value: 2) {
                tabContent(2)
            }
            Tab("Interests", systemImage: "square.grid.2x2", value: 4) {
                tabContent(4)
            }
            Tab("More", systemImage: "ellipsis", value: 5) {
                MorePrayerTabsView(path: $morePath, onSelectTab: selectContentTab)
            }
            Tab(value: 6, role: .search) {
                Color.clear
            } label: {
                Label("Voice", systemImage: bot.isGeneratingNudge ? "sparkles" : "waveform")
            }
        }
        .tabBarMinimizeBehavior(.automatic)
        .tint(Color.accentColor)
        .overlay(alignment: .bottomTrailing) {
            NowNudgeBotLayer(model: bot)
                .padding(.trailing, 16)
                .padding(.bottom, 0)
        }
        .onAppear {
            bot.start()
        }
        .onDisappear {
            bot.stop()
        }
        .onReceive(NotificationCenter.default.publisher(for: .openPrayerLiveActivity)) { _ in
            morePath = []
            selectedTab = 0
            lastSelectedTab = 0
        }
        .onChange(of: selectedTab) { _, newValue in
            if newValue == 6 {
                if bot.showNudge {
                    bot.performNudgeAction()
                } else if !bot.isGeneratingNudge {
                    bot.openComposer()
                }
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    selectedTab = lastSelectedTab
                }
            } else {
                lastSelectedTab = newValue
            }
        }
    }

    private func tabContent(_ index: Int) -> some View {
        ComposePrayerTimesTabView(tabIndex: index, onSelectTab: selectContentTab)
            .ignoresSafeArea(.all)
    }

    private func selectContentTab(_ index: Int) {
        switch index {
        case 0:
            selectedTab = 0
        case 2:
            selectedTab = 2
        case 4:
            selectedTab = 4
        default:
            morePath = [index]
            selectedTab = 5
        }
    }
}

@available(iOS 26.0, *)
private struct VoiceNudgeBarsGlyph: View {
    let generating: Bool
    var color: Color = .accentColor

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
            let cycle = timeline.date.timeIntervalSinceReferenceDate
                .truncatingRemainder(dividingBy: 2.6) / 2.6
            ZStack {
                VoiceNudgeBarsShape()
                    .stroke(
                        color,
                        style: StrokeStyle(lineWidth: 2.8, lineCap: .round)
                    )
                    .opacity(generating ? 0 : 1)
                    .scaleEffect(generating ? 0.90 : 1)
                VoiceNudgeSparklesShape(
                    morph: generating ? 1 : 0,
                    phase: CGFloat(cycle * .pi * 2)
                )
                    .fill(color)
            }
            .animation(.easeInOut(duration: 0.70), value: generating)
        }
        .frame(width: 30, height: 30)
    }
}

/// Full-sparkle glyph used by the thinking pill; mirrors Android's
/// `VoiceNudgeSparkles(morph = 1f)`.
@available(iOS 26.0, *)
private struct VoiceNudgeSparkleGlyph: View {
    let color: Color

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
            let cycle = timeline.date.timeIntervalSinceReferenceDate
                .truncatingRemainder(dividingBy: 2.6) / 2.6
            VoiceNudgeSparklesShape(
                morph: 1,
                phase: CGFloat(cycle * .pi * 2)
            )
            .fill(color)
        }
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

/// After the informative prefix, the thinking pill cycles through the playful
/// spinner verbs, the same list as Android, shuffled per generation.
private let NowNudgeThinkingVerbs = [
    "Accomplishing", "Actioning", "Actualizing", "Architecting", "Baking", "Beaming",
    "Beboppin'", "Befuddling", "Billowing", "Blanching", "Bloviating", "Boogieing",
    "Boondoggling", "Booping", "Bootstrapping", "Brewing", "Bunning", "Burrowing",
    "Calculating", "Canoodling", "Caramelizing", "Cascading", "Catapulting", "Cerebrating",
    "Channeling", "Choreographing", "Churning", "Clauding", "Coalescing", "Cogitating",
    "Combobulating", "Composing", "Computing", "Concocting", "Considering", "Contemplating",
    "Cooking", "Crafting", "Creating", "Crunching", "Crystallizing", "Cultivating",
    "Deciphering", "Deliberating", "Determining", "Dilly-dallying", "Discombobulating",
    "Doing", "Doodling", "Drizzling", "Ebbing", "Effecting", "Elucidating", "Embellishing",
    "Enchanting", "Envisioning", "Evaporating", "Fermenting", "Fiddle-faddling", "Finagling",
    "Flambéing", "Flibbertigibbeting", "Flowing", "Flummoxing", "Fluttering", "Forging",
    "Forming", "Frolicking", "Frosting", "Gallivanting", "Galloping", "Garnishing",
    "Generating", "Gesticulating", "Germinating", "Gitifying", "Grooving", "Gusting",
    "Harmonizing", "Hashing", "Hatching", "Herding", "Honking", "Hullaballooing",
    "Hyperspacing", "Ideating", "Imagining", "Improvising", "Incubating", "Inferring",
    "Infusing", "Ionizing", "Jitterbugging", "Julienning", "Kneading", "Leavening",
    "Levitating", "Lollygagging", "Manifesting", "Marinating", "Meandering",
    "Metamorphosing", "Misting", "Moonwalking", "Moseying", "Mulling", "Mustering",
    "Musing", "Nebulizing", "Nesting", "Newspapering", "Noodling", "Nucleating",
    "Orbiting", "Orchestrating", "Osmosing", "Perambulating", "Percolating", "Perusing",
    "Philosophising", "Photosynthesizing", "Pollinating", "Pondering", "Pontificating",
    "Pouncing", "Precipitating", "Prestidigitating", "Processing", "Proofing",
    "Propagating", "Puttering", "Puzzling", "Quantumizing", "Razzle-dazzling",
    "Razzmatazzing", "Recombobulating", "Reticulating", "Roosting", "Ruminating",
    "Sautéing", "Scampering", "Schlepping", "Scurrying", "Seasoning", "Shenaniganing",
    "Shimmying", "Simmering", "Skedaddling", "Sketching", "Slithering", "Smooshing",
    "Sock-hopping", "Spelunking", "Spinning", "Sprouting", "Stewing", "Sublimating",
    "Swirling", "Swooping", "Symbioting", "Synthesizing", "Tempering", "Thinking",
    "Thundering", "Tinkering", "Tomfoolering", "Topsy-turvying", "Transfiguring",
    "Transmuting", "Twisting", "Undulating", "Unfurling", "Unravelling", "Vibing",
    "Waddling", "Wandering", "Warping", "Whatchamacalliting", "Whirlpooling", "Whirring",
    "Whisking", "Wibbling", "Working", "Wrangling", "Zesting", "Zigzagging",
]

@available(iOS 26.0, *)
private struct MorePrayerTabsView: View {
    @Binding var path: [Int]
    let onSelectTab: (Int) -> Void

    var body: some View {
        NavigationStack(path: $path) {
            List {
                NavigationLink(value: 1) {
                    Label("For you", systemImage: "shippingbox")
                }
                NavigationLink(value: 3) {
                    Label("Course", systemImage: "play.rectangle")
                }
            }
            .navigationTitle("More")
            .navigationDestination(for: Int.self) { index in
                ComposePrayerTimesTabView(tabIndex: index, onSelectTab: onSelectTab)
                    .ignoresSafeArea(.container, edges: .bottom)
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
    }
}
