import AVFoundation
import Foundation
import SherpaOnnx
import Shared

/// Native Sherpa runtime. Model downloads and path resolution intentionally live elsewhere.
final class SherpaSpeechService: NSObject, IosSherpaService {
    static let shared = SherpaSpeechService()

    private static let sampleRate = 16_000.0

    private let runtimeQueue = DispatchQueue(
        label: "com.starception.submission.sherpa-runtime",
        qos: .userInitiated
    )
    private let tokenLock = NSLock()

    private var recognitionToken = 0
    private var ttsToken = 0

    private var microphoneEngine: AVAudioEngine?
    private var microphoneTapInstalled = false
    private var keywordSpotter: SherpaOnnxKeywordSpotterWrapper?
    private var onlineRecognizer: SherpaOnnxRecognizer?
    private var recognitionSink: IosSherpaEventSink?
    private var lastPartialText = ""

    private var ttsRuntime: SherpaOnnxOfflineTtsWrapper?
    private var playbackEngine: AVAudioEngine?
    private var playbackNode: AVAudioPlayerNode?
    private var playbackBuffer: AVAudioPCMBuffer?
    private var ttsSink: IosSherpaEventSink?

    func startKeywordSpotting(
        paths: IosSherpaRecognitionPaths,
        eventSink: IosSherpaEventSink
    ) -> Bool {
        if let error = validateRecognitionPaths(paths, requiresKeywords: true) {
            emit(eventSink) { $0.onError(message: error) }
            return false
        }

        let token = nextRecognitionToken()
        _ = nextTtsToken()
        runtimeQueue.async { [weak self] in
            guard let self else { return }
            self.stopSpeakingOnQueue()
            self.stopRecognitionOnQueue()
            guard self.isRecognitionTokenCurrent(token) else { return }
            self.requestMicrophoneAccess(token: token, sink: eventSink) {
                self.startKeywordSpotter(paths: paths, sink: eventSink, token: token)
            }
        }
        return true
    }

    func startOnlineRecognition(
        paths: IosSherpaRecognitionPaths,
        eventSink: IosSherpaEventSink
    ) -> Bool {
        if let error = validateRecognitionPaths(paths, requiresKeywords: false) {
            emit(eventSink) { $0.onError(message: error) }
            return false
        }

        let token = nextRecognitionToken()
        _ = nextTtsToken()
        runtimeQueue.async { [weak self] in
            guard let self else { return }
            self.stopSpeakingOnQueue()
            self.stopRecognitionOnQueue()
            guard self.isRecognitionTokenCurrent(token) else { return }
            self.requestMicrophoneAccess(token: token, sink: eventSink) {
                self.startOnlineRecognizer(paths: paths, sink: eventSink, token: token)
            }
        }
        return true
    }

    func stopRecognition() {
        let token = nextRecognitionToken()
        runtimeQueue.async { [weak self] in
            self?.stopRecognitionOnQueue(finalizeOnlineResultWith: token)
        }
    }

    func speak(
        text: String,
        paths: IosSherpaTtsPaths,
        speakerId: Int32,
        speed: Float,
        eventSink: IosSherpaEventSink
    ) -> Bool {
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedText.isEmpty else {
            emit(eventSink) { $0.onError(message: "TTS text is empty") }
            return false
        }
        if let error = validateTtsPaths(paths) {
            emit(eventSink) { $0.onError(message: error) }
            return false
        }
        guard speed.isFinite, speed > 0 else {
            emit(eventSink) { $0.onError(message: "TTS speed must be greater than zero") }
            return false
        }

        _ = nextRecognitionToken()
        let token = nextTtsToken()
        runtimeQueue.async { [weak self] in
            guard let self else { return }
            self.stopRecognitionOnQueue()
            self.stopSpeakingOnQueue()
            guard self.isTtsTokenCurrent(token) else { return }
            self.generateAndPlay(
                text: trimmedText,
                paths: paths,
                speakerId: speakerId,
                speed: speed,
                sink: eventSink,
                token: token
            )
        }
        return true
    }

    func stopSpeaking() {
        _ = nextTtsToken()
        runtimeQueue.async { [weak self] in
            self?.stopSpeakingOnQueue()
        }
    }

    func shutdown() {
        _ = nextRecognitionToken()
        _ = nextTtsToken()
        runtimeQueue.async { [weak self] in
            self?.stopRecognitionOnQueue()
            self?.stopSpeakingOnQueue()
        }
    }

    private func requestMicrophoneAccess(
        token: Int,
        sink: IosSherpaEventSink,
        onGranted: @escaping () -> Void
    ) {
        let session = AVAudioSession.sharedInstance()
        switch session.recordPermission {
        case .granted:
            onGranted()
        case .denied:
            emitRecognition(sink, token: token) {
                $0.onError(message: "Microphone permission was not granted")
            }
        case .undetermined:
            session.requestRecordPermission { [weak self] granted in
                guard let self else { return }
                self.runtimeQueue.async {
                    guard self.isRecognitionTokenCurrent(token) else { return }
                    if granted {
                        onGranted()
                    } else {
                        self.emitRecognition(sink, token: token) {
                            $0.onError(message: "Microphone permission was not granted")
                        }
                    }
                }
            }
        @unknown default:
            emitRecognition(sink, token: token) {
                $0.onError(message: "Microphone permission status is unavailable")
            }
        }
    }

    private func startKeywordSpotter(
        paths: IosSherpaRecognitionPaths,
        sink: IosSherpaEventSink,
        token: Int
    ) {
        let transducer = sherpaOnnxOnlineTransducerModelConfig(
            encoder: paths.encoderPath,
            decoder: paths.decoderPath,
            joiner: paths.joinerPath
        )
        let model = sherpaOnnxOnlineModelConfig(
            tokens: paths.tokensPath,
            transducer: transducer,
            numThreads: 2
        )
        let features = sherpaOnnxFeatureConfig(sampleRate: Int(Self.sampleRate), featureDim: 80)
        var config = sherpaOnnxKeywordSpotterConfig(
            featConfig: features,
            modelConfig: model,
            keywordsFile: paths.keywordsPath
        )
        let spotter = SherpaOnnxKeywordSpotterWrapper(config: &config)
        guard spotter.spotter != nil, spotter.stream != nil else {
            emitRecognition(sink, token: token) {
                $0.onError(message: "Sherpa could not initialize keyword spotting")
            }
            return
        }

        keywordSpotter = spotter
        recognitionSink = sink
        startMicrophone(sink: sink, token: token)
    }

    private func startOnlineRecognizer(
        paths: IosSherpaRecognitionPaths,
        sink: IosSherpaEventSink,
        token: Int
    ) {
        let transducer = sherpaOnnxOnlineTransducerModelConfig(
            encoder: paths.encoderPath,
            decoder: paths.decoderPath,
            joiner: paths.joinerPath
        )
        let model = sherpaOnnxOnlineModelConfig(
            tokens: paths.tokensPath,
            transducer: transducer,
            numThreads: 2
        )
        let features = sherpaOnnxFeatureConfig(sampleRate: Int(Self.sampleRate), featureDim: 80)
        var config = sherpaOnnxOnlineRecognizerConfig(
            featConfig: features,
            modelConfig: model,
            enableEndpoint: true
        )

        onlineRecognizer = SherpaOnnxRecognizer(config: &config)
        recognitionSink = sink
        startMicrophone(sink: sink, token: token)
    }

    private func startMicrophone(sink: IosSherpaEventSink, token: Int) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(
                .playAndRecord,
                mode: .measurement,
                options: [.defaultToSpeaker, .allowBluetoothHFP]
            )
            try session.setPreferredSampleRate(Self.sampleRate)
            try session.setPreferredIOBufferDuration(0.02)
            try session.setActive(true)

            let engine = AVAudioEngine()
            let input = engine.inputNode
            let inputFormat = input.outputFormat(forBus: 0)
            guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
                throw serviceError("The microphone has no usable audio format")
            }
            guard let targetFormat = AVAudioFormat(
                commonFormat: .pcmFormatFloat32,
                sampleRate: Self.sampleRate,
                channels: 1,
                interleaved: false
            ), let converter = AVAudioConverter(from: inputFormat, to: targetFormat) else {
                throw serviceError("The microphone audio converter could not be created")
            }

            input.installTap(onBus: 0, bufferSize: 1_024, format: inputFormat) { [weak self, converter] buffer, _ in
                guard let self, self.isRecognitionTokenCurrent(token) else { return }
                do {
                    let samples = try Self.convertTo16kMono(buffer, using: converter)
                    guard !samples.isEmpty else { return }
                    self.runtimeQueue.async {
                        guard self.isRecognitionTokenCurrent(token) else { return }
                        self.processRecognitionSamples(samples, sink: sink, token: token)
                    }
                } catch {
                    self.runtimeQueue.async {
                        guard self.isRecognitionTokenCurrent(token) else { return }
                        self.emitRecognition(sink, token: token) {
                            $0.onError(message: "Audio conversion failed: \(error.localizedDescription)")
                        }
                        _ = self.nextRecognitionToken()
                        self.stopRecognitionOnQueue()
                    }
                }
            }
            microphoneEngine = engine
            microphoneTapInstalled = true
            engine.prepare()
            try engine.start()
            emitRecognition(sink, token: token) { $0.onRecognitionStarted() }
        } catch {
            stopRecognitionOnQueue()
            emitRecognition(sink, token: token) {
                $0.onError(message: "Microphone startup failed: \(error.localizedDescription)")
            }
        }
    }

    private static func convertTo16kMono(
        _ input: AVAudioPCMBuffer,
        using converter: AVAudioConverter
    ) throws -> [Float] {
        let ratio = sampleRate / input.format.sampleRate
        let frameCapacity = AVAudioFrameCount(
            max(1, ceil(Double(input.frameLength) * ratio) + 32)
        )
        guard let output = AVAudioPCMBuffer(
            pcmFormat: converter.outputFormat,
            frameCapacity: frameCapacity
        ) else {
            throw serviceError("The converted audio buffer could not be allocated")
        }

        var suppliedInput = false
        var conversionError: NSError?
        let status = converter.convert(to: output, error: &conversionError) { _, inputStatus in
            if suppliedInput {
                inputStatus.pointee = .noDataNow
                return nil
            }
            suppliedInput = true
            inputStatus.pointee = .haveData
            return input
        }
        if status == .error {
            throw conversionError ?? serviceError("AVAudioConverter returned an error")
        }
        guard let channel = output.floatChannelData?.pointee else { return [] }
        return Array(UnsafeBufferPointer(start: channel, count: Int(output.frameLength)))
    }

    private func processRecognitionSamples(
        _ samples: [Float],
        sink: IosSherpaEventSink,
        token: Int
    ) {
        if let spotter = keywordSpotter {
            spotter.acceptWaveform(samples: samples, sampleRate: Int(Self.sampleRate))
            while spotter.isReady() {
                spotter.decode()
                let keyword = spotter.getResult().keyword.trimmingCharacters(in: .whitespacesAndNewlines)
                if !keyword.isEmpty {
                    spotter.reset()
                    emitRecognition(sink, token: token) { $0.onKeyword(keyword: keyword) }
                }
            }
            return
        }

        guard let recognizer = onlineRecognizer else { return }
        recognizer.acceptWaveform(samples: samples, sampleRate: Int(Self.sampleRate))
        while recognizer.isReady() {
            recognizer.decode()
        }
        let text = recognizer.getResult().text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !text.isEmpty, text != lastPartialText {
            lastPartialText = text
            emitRecognition(sink, token: token) { $0.onPartialResult(text: text) }
        }
        if recognizer.isEndpoint() {
            if !text.isEmpty {
                emitRecognition(sink, token: token) { $0.onFinalResult(text: text) }
            }
            lastPartialText = ""
            recognizer.reset()
        }
    }

    private func stopRecognitionOnQueue(finalizeOnlineResultWith token: Int? = nil) {
        if microphoneTapInstalled {
            microphoneEngine?.inputNode.removeTap(onBus: 0)
            microphoneTapInstalled = false
        }
        microphoneEngine?.stop()
        microphoneEngine = nil

        if let token, let recognizer = onlineRecognizer, let sink = recognitionSink {
            recognizer.acceptWaveform(samples: [Float](repeating: 0, count: 3_200))
            recognizer.inputFinished()
            while recognizer.isReady() {
                recognizer.decode()
            }
            let text = recognizer.getResult().text.trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty {
                emitRecognition(sink, token: token) { $0.onFinalResult(text: text) }
            }
        }

        keywordSpotter = nil
        onlineRecognizer = nil
        recognitionSink = nil
        lastPartialText = ""
        deactivateAudioSession()
    }

    private func generateAndPlay(
        text: String,
        paths: IosSherpaTtsPaths,
        speakerId: Int32,
        speed: Float,
        sink: IosSherpaEventSink,
        token: Int
    ) {
        let runtime: SherpaOnnxOfflineTtsWrapper
        if paths.voicesPath.isEmpty {
            let vits = sherpaOnnxOfflineTtsVitsModelConfig(
                model: paths.modelPath,
                lexicon: paths.lexiconPath,
                tokens: paths.tokensPath,
                dataDir: paths.dataDirPath,
                dictDir: paths.dictDirPath
            )
            let model = sherpaOnnxOfflineTtsModelConfig(vits: vits, numThreads: 2)
            var config = sherpaOnnxOfflineTtsConfig(model: model)
            runtime = SherpaOnnxOfflineTtsWrapper(config: &config)
        } else {
            let kokoro = sherpaOnnxOfflineTtsKokoroModelConfig(
                model: paths.modelPath,
                voices: paths.voicesPath,
                tokens: paths.tokensPath,
                dataDir: paths.dataDirPath,
                dictDir: paths.dictDirPath,
                lexicon: paths.lexiconPath,
                lang: paths.language
            )
            let model = sherpaOnnxOfflineTtsModelConfig(kokoro: kokoro, numThreads: 2)
            var config = sherpaOnnxOfflineTtsConfig(model: model)
            runtime = SherpaOnnxOfflineTtsWrapper(config: &config)
        }
        guard runtime.tts != nil else {
            emitTts(sink, token: token) { $0.onError(message: "Sherpa could not initialize TTS") }
            return
        }
        ttsRuntime = runtime
        ttsSink = sink

        let context = TtsGenerationContext(service: self, token: token)
        var generation = SherpaOnnxGenerationConfigSwift()
        generation.sid = Int(speakerId)
        generation.speed = speed
        let audio = runtime.generateWithConfig(
            text: text,
            config: generation,
            callback: ttsProgressCallback,
            arg: Unmanaged.passUnretained(context).toOpaque()
        )
        guard isTtsTokenCurrent(token) else { return }
        guard audio.audio != nil else {
            stopSpeakingOnQueue()
            emitTts(sink, token: token) { $0.onError(message: "Sherpa TTS generation failed") }
            return
        }

        let samples = audio.samples
        let generatedSampleRate = Int(audio.sampleRate)
        guard !samples.isEmpty, generatedSampleRate > 0 else {
            stopSpeakingOnQueue()
            emitTts(sink, token: token) { $0.onError(message: "Sherpa generated no audio") }
            return
        }
        playGeneratedAudio(
            samples: samples,
            sampleRate: generatedSampleRate,
            sink: sink,
            token: token
        )
    }

    private func playGeneratedAudio(
        samples: [Float],
        sampleRate: Int,
        sink: IosSherpaEventSink,
        token: Int
    ) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
            try session.setActive(true)

            guard samples.count <= Int(UInt32.max), let format = AVAudioFormat(
                commonFormat: .pcmFormatFloat32,
                sampleRate: Double(sampleRate),
                channels: 1,
                interleaved: false
            ), let buffer = AVAudioPCMBuffer(
                pcmFormat: format,
                frameCapacity: AVAudioFrameCount(samples.count)
            ), let channel = buffer.floatChannelData?.pointee else {
                throw serviceError("The generated audio buffer could not be created")
            }
            buffer.frameLength = AVAudioFrameCount(samples.count)
            samples.withUnsafeBufferPointer { source in
                if let baseAddress = source.baseAddress {
                    channel.update(from: baseAddress, count: samples.count)
                }
            }

            let engine = AVAudioEngine()
            let node = AVAudioPlayerNode()
            engine.attach(node)
            engine.connect(node, to: engine.mainMixerNode, format: format)
            playbackEngine = engine
            playbackNode = node
            playbackBuffer = buffer
            engine.prepare()
            try engine.start()
            node.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { [weak self] _ in
                guard let self else { return }
                self.runtimeQueue.async {
                    guard self.isTtsTokenCurrent(token) else { return }
                    let completedSink = self.ttsSink
                    self.stopSpeakingOnQueue()
                    if let completedSink {
                        self.emitTts(completedSink, token: token) { $0.onTtsFinished() }
                    }
                }
            }
            node.play()
            emitTts(sink, token: token) { $0.onTtsStarted(sampleRate: Int32(sampleRate)) }
        } catch {
            stopSpeakingOnQueue()
            emitTts(sink, token: token) {
                $0.onError(message: "TTS playback failed: \(error.localizedDescription)")
            }
        }
    }

    private func stopSpeakingOnQueue() {
        playbackNode?.stop()
        playbackEngine?.stop()
        playbackBuffer = nil
        playbackNode = nil
        playbackEngine = nil
        ttsRuntime = nil
        ttsSink = nil
        deactivateAudioSession()
    }

    private func validateRecognitionPaths(
        _ paths: IosSherpaRecognitionPaths,
        requiresKeywords: Bool
    ) -> String? {
        let requiredFiles = [
            ("encoder", paths.encoderPath),
            ("decoder", paths.decoderPath),
            ("joiner", paths.joinerPath),
            ("tokens", paths.tokensPath),
        ] + (requiresKeywords ? [("keywords", paths.keywordsPath)] : [])
        return firstMissingFile(in: requiredFiles)
    }

    private func validateTtsPaths(_ paths: IosSherpaTtsPaths) -> String? {
        var files = [("model", paths.modelPath), ("tokens", paths.tokensPath)]
        if !paths.voicesPath.isEmpty {
            files.append(("voices", paths.voicesPath))
        }
        if !paths.lexiconPath.isEmpty {
            files.append(("lexicon", paths.lexiconPath))
        }
        if let error = firstMissingFile(in: files) {
            return error
        }
        if !paths.dictDirPath.isEmpty, !isDirectory(paths.dictDirPath) {
            return "Sherpa dictionary directory does not exist: \(paths.dictDirPath)"
        }
        if !paths.dataDirPath.isEmpty, !isDirectory(paths.dataDirPath) {
            return "Sherpa data directory does not exist: \(paths.dataDirPath)"
        }
        return nil
    }

    private func firstMissingFile(in paths: [(String, String)]) -> String? {
        for (label, path) in paths where path.isEmpty || !FileManager.default.isReadableFile(atPath: path) {
            return "Sherpa \(label) file does not exist or is unreadable: \(path)"
        }
        return nil
    }

    private func isDirectory(_ path: String) -> Bool {
        var isDirectory: ObjCBool = false
        return !path.isEmpty
            && FileManager.default.fileExists(atPath: path, isDirectory: &isDirectory)
            && isDirectory.boolValue
    }

    private func nextRecognitionToken() -> Int {
        tokenLock.lock()
        defer { tokenLock.unlock() }
        recognitionToken &+= 1
        return recognitionToken
    }

    private func nextTtsToken() -> Int {
        tokenLock.lock()
        defer { tokenLock.unlock() }
        ttsToken &+= 1
        return ttsToken
    }

    fileprivate func isTtsTokenCurrent(_ token: Int) -> Bool {
        tokenLock.lock()
        defer { tokenLock.unlock() }
        return ttsToken == token
    }

    private func isRecognitionTokenCurrent(_ token: Int) -> Bool {
        tokenLock.lock()
        defer { tokenLock.unlock() }
        return recognitionToken == token
    }

    private func emit(_ sink: IosSherpaEventSink, event: @escaping (IosSherpaEventSink) -> Void) {
        DispatchQueue.main.async { event(sink) }
    }

    private func emitRecognition(
        _ sink: IosSherpaEventSink,
        token: Int,
        event: @escaping (IosSherpaEventSink) -> Void
    ) {
        DispatchQueue.main.async { [weak self] in
            guard self?.isRecognitionTokenCurrent(token) == true else { return }
            event(sink)
        }
    }

    private func emitTts(
        _ sink: IosSherpaEventSink,
        token: Int,
        event: @escaping (IosSherpaEventSink) -> Void
    ) {
        DispatchQueue.main.async { [weak self] in
            guard self?.isTtsTokenCurrent(token) == true else { return }
            event(sink)
        }
    }

    private func deactivateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: .notifyOthersOnDeactivation
        )
    }
}

private final class TtsGenerationContext {
    weak var service: SherpaSpeechService?
    let token: Int

    init(service: SherpaSpeechService, token: Int) {
        self.service = service
        self.token = token
    }
}

private let ttsProgressCallback: TtsProgressCallbackWithArg = { _, _, _, opaqueContext in
    guard let opaqueContext else { return 0 }
    let context = Unmanaged<TtsGenerationContext>
        .fromOpaque(opaqueContext)
        .takeUnretainedValue()
    return context.service?.isTtsTokenCurrent(context.token) == true ? 1 : 0
}

private func serviceError(_ message: String) -> NSError {
    NSError(
        domain: "com.starception.submission.SherpaSpeechService",
        code: 1,
        userInfo: [NSLocalizedDescriptionKey: message]
    )
}
