import AVFoundation
import Combine
import Foundation
import WebRTC
import UIKit
import Shared

@MainActor final class FamilyWebRTC: NSObject, ObservableObject {
    @Published private(set) var localVideo: RTCVideoTrack?
    @Published private(set) var remoteVideo: RTCVideoTrack?
    var onSignal: (([String: Any]) -> Void)?
    var onConnected: ((Bool) -> Void)?
    var onFailure: ((String) -> Void)?
    private static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        return RTCPeerConnectionFactory(encoderFactory: RTCDefaultVideoEncoderFactory(), decoderFactory: RTCDefaultVideoDecoderFactory())
    }()
    private var peer: RTCPeerConnection?
    private var microphone: RTCAudioTrack?
    private var capturer: RTCCameraVideoCapturer?
    private var cameraDevice: AVCaptureDevice?
    private var cameraStarted = false
    private var pendingCandidates: [RTCIceCandidate] = []
    private var hasRemoteDescription = false
    private var epoch = 0
    private var ownsAudio = false
    private var previousCategory: AVAudioSession.Category?
    private var previousMode: AVAudioSession.Mode?
    private var previousOptions: AVAudioSession.CategoryOptions = []
    var ready: Bool { peer != nil }

    static func permissions(video: Bool) async -> Bool {
        let audio: Bool = await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { continuation.resume(returning: $0) }
        }
        guard audio else { return false }
        if video { return await AVCaptureDevice.requestAccess(for: .video) }
        return true
    }
    func start(_ servers: [[String: Any]], video: Bool, caller: Bool) async throws {
        close(preservePreview: video && caller && peer == nil && localVideo != nil)
        let token = epoch
        FamilyAudioSession.shared.setActive(active: true)
        QuranPlaybackBus.shared.stop()
        let session = AVAudioSession.sharedInstance()
        previousCategory = session.category; previousMode = session.mode; previousOptions = session.categoryOptions
        // Other app audio must remain paused until explicitly resumed after the call.
        NotificationCenter.default.post(name: .familyCallWillStart, object: nil)
        SherpaSpeechService.shared.stopRecognition()
        SherpaSpeechService.shared.stopSpeaking()
        try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth])
        // CallKit activates/deactivates the audio session through its provider.
        ownsAudio = true
        speaker(video)
        let configuration = RTCConfiguration()
        configuration.sdpSemantics = .unifiedPlan
        configuration.continualGatheringPolicy = .gatherContinually
        configuration.iceServers = try servers.map { server in
            guard let urls = server["urls"] as? [String], let username = server["username"] as? String,
                  let password = server["credential"] as? String else { throw MediaError.negotiation }
            return RTCIceServer(urlStrings: urls, username: username, credential: password)
        }
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        guard let connection = Self.factory.peerConnection(with: configuration, constraints: constraints, delegate: self) else {
            throw MediaError.negotiation
        }
        peer = connection
        let audioSource = Self.factory.audioSource(with: constraints)
        microphone = Self.factory.audioTrack(with: audioSource, trackId: "family-audio")
        if let track = microphone { connection.add(track, streamIds: ["family"]) }
        if video {
            if UIApplication.shared.applicationState == .active { try await preview() }
            else if localVideo == nil { makeCamera(); localVideo?.isEnabled = false }
            if let track = localVideo { connection.add(track, streamIds: ["family"]) }
        }
        if caller {
            let offer = try await create(offer: true, connection: connection)
            guard epoch == token else { return }
            try await set(offer, local: true, connection: connection)
            guard epoch == token else { return }
            onSignal?(["type": "offer", "sdp": offer.sdp])
        }
    }
    private func makeCamera() {
        let source = Self.factory.videoSource()
        capturer = RTCCameraVideoCapturer(delegate: source)
        localVideo = Self.factory.videoTrack(with: source, trackId: "family-video")
    }
    /// Local video only: no peer connection, microphone or audio activation.
    func preview() async throws {
        guard localVideo == nil else { return }
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else { throw MediaError.camera }
        makeCamera()
        do { try await startCamera(front: true) }
        catch {
            if peer == nil { close() } else { onFailure?("Could not start camera.") }
            throw error
        }
    }
    func receive(_ payload: [String: Any]) async {
        guard let connection = peer, let type = payload["type"] as? String else { return }
        let token = epoch
        do {
            if type == "ice" {
                guard let value = payload["candidate"] as? String, !value.isEmpty, value.utf8.count <= 4096,
                      let line = (payload["sdpMLineIndex"] as? NSNumber)?.int32Value else { return }
                let candidate = RTCIceCandidate(sdp: value, sdpMLineIndex: line, sdpMid: payload["sdpMid"] as? String)
                if hasRemoteDescription { connection.add(candidate) { _ in } }
                else if pendingCandidates.count < 256 { pendingCandidates.append(candidate) }
            } else if type == "offer" || type == "answer" {
                guard let text = payload["sdp"] as? String, !text.isEmpty, text.utf8.count < 35000 else { return }
                let description = RTCSessionDescription(type: type == "offer" ? .offer : .answer, sdp: text)
                try await set(description, local: false, connection: connection)
                guard token == epoch else { return }
                hasRemoteDescription = true
                for candidate in pendingCandidates { connection.add(candidate) { _ in } }
                pendingCandidates.removeAll()
                if type == "offer" {
                    let answer = try await create(offer: false, connection: connection)
                    guard token == epoch else { return }
                    try await set(answer, local: true, connection: connection)
                    guard token == epoch else { return }
                    onSignal?(["type": "answer", "sdp": answer.sdp])
                }
            }
        } catch { if token == epoch { onFailure?("Could not negotiate this call’s media.") } }
    }
    private func create(offer: Bool, connection: RTCPeerConnection) async throws -> RTCSessionDescription {
        try await withCheckedThrowingContinuation { continuation in
            let complete: (RTCSessionDescription?, Error?) -> Void = { description, error in
                if let description { continuation.resume(returning: description) }
                else { continuation.resume(throwing: error ?? MediaError.negotiation) }
            }
            let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
            if offer { connection.offer(for: constraints, completionHandler: complete) }
            else { connection.answer(for: constraints, completionHandler: complete) }
        }
    }
    private func set(_ sdp: RTCSessionDescription, local: Bool, connection: RTCPeerConnection) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let complete: (Error?) -> Void = { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            }
            if local { connection.setLocalDescription(sdp, completionHandler: complete) }
            else { connection.setRemoteDescription(sdp, completionHandler: complete) }
        }
    }
    private func startCamera(front: Bool) async throws {
        guard let capture = capturer else { throw MediaError.camera }
        let devices = RTCCameraVideoCapturer.captureDevices()
        guard let device = devices.first(where: { $0.position == (front ? .front : .back) }) ?? devices.first else { throw MediaError.camera }
        let formats = RTCCameraVideoCapturer.supportedFormats(for: device)
        guard let format = formats.min(by: {
            abs(CMVideoFormatDescriptionGetDimensions($0.formatDescription).width - 640) <
            abs(CMVideoFormatDescriptionGetDimensions($1.formatDescription).width - 640)
        }) else { throw MediaError.camera }
        let maximum = format.videoSupportedFrameRateRanges.map(\.maxFrameRate).max() ?? 24
        cameraDevice = device
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            capture.startCapture(with: device, format: format, fps: max(1, min(24, Int(maximum)))) { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            }
        }
        cameraStarted = true
    }
    func switchCamera() {
        let front = cameraDevice?.position != .front
        Task { do { try await startCamera(front: front) } catch { onFailure?("Could not switch camera.") } }
    }
    func mute(_ muted: Bool) { microphone?.isEnabled = !muted }
    func camera(_ enabled: Bool) {
        localVideo?.isEnabled = enabled
        if enabled && !cameraStarted && UIApplication.shared.applicationState == .active {
            Task { do { try await startCamera(front: true) } catch { onFailure?("Could not start camera.") } }
        }
    }
    func speaker(_ enabled: Bool) { try? AVAudioSession.sharedInstance().overrideOutputAudioPort(enabled ? .speaker : .none) }
    func close(preservePreview: Bool = false) {
        epoch += 1
        if !preservePreview { capturer?.stopCapture(); capturer = nil; cameraDevice = nil; cameraStarted = false; localVideo = nil }
        peer?.close(); peer = nil; microphone = nil; remoteVideo = nil
        pendingCandidates.removeAll(); hasRemoteDescription = false
        if ownsAudio {
            let session = AVAudioSession.sharedInstance()
            try? session.overrideOutputAudioPort(.none)
            // CallKit releases activation after the call is reported ended.
            if let category = previousCategory, let mode = previousMode {
                try? session.setCategory(category, mode: mode, options: previousOptions)
            }
            ownsAudio = false
            NotificationCenter.default.post(name: .familyCallDidEnd, object: nil)
        }
        FamilyAudioSession.shared.setActive(active: false)
        onConnected?(false)
    }
    private enum MediaError: Error { case negotiation, camera }
}

extension Notification.Name {
    static let familyCallWillStart = Notification.Name("familyCallWillStart")
    static let familyCallDidEnd = Notification.Name("familyCallDidEnd")
}

extension FamilyWebRTC: RTCPeerConnectionDelegate {
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    nonisolated func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        Task { @MainActor in
            guard peerConnection === peer else { return }
            onConnected?(newState == .connected || newState == .completed)
            if newState == .failed { onFailure?("Could not connect media. Check your network and relay configuration.") }
        }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        Task { @MainActor in
            guard peerConnection === peer else { return }
            var payload: [String: Any] = ["type": "ice", "candidate": candidate.sdp, "sdpMLineIndex": candidate.sdpMLineIndex]
            if let mid = candidate.sdpMid { payload["sdpMid"] = mid }
            onSignal?(payload)
        }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams: [RTCMediaStream]) {
        Task { @MainActor in if peerConnection === peer { remoteVideo = rtpReceiver.track as? RTCVideoTrack } }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didStartReceivingOn transceiver: RTCRtpTransceiver) {
        Task { @MainActor in if peerConnection === peer { remoteVideo = transceiver.receiver.track as? RTCVideoTrack } }
    }
}
