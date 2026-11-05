import AVFoundation
import CallKit
import Foundation
import PushKit
import UIKit
import WebRTC

/** All VoIP pushes are reported to CallKit before network validation, as iOS requires. */
@MainActor final class FamilySystemCalls: NSObject, CXProviderDelegate, PKPushRegistryDelegate {
    static let shared = FamilySystemCalls()
    private let provider: CXProvider
    private let controller = CXCallController()
    private var registry: PKPushRegistry?
    private var calls: Set<UUID> = []
    private var expiryTasks: [UUID: Task<Void, Never>] = [:]
    var token: String?
    var enabled: Bool {
        let value = Bundle.main.object(forInfoDictionaryKey: "FamilyBackgroundCalls")
        return (value as? Bool) == true || ["YES", "true", "1"].contains(value as? String ?? "")
    }
    override private init() {
        let config = CXProviderConfiguration()
        config.supportsVideo = true
        config.maximumCallsPerCallGroup = 1; config.maximumCallGroups = 1
        config.supportedHandleTypes = [.generic]
        config.includesCallsInRecents = false
        provider = CXProvider(configuration: config)
        super.init()
        provider.setDelegate(self, queue: .main)
        RTCAudioSession.sharedInstance().useManualAudio = true
        RTCAudioSession.sharedInstance().isAudioEnabled = false
    }
    func start() {
        guard enabled, registry == nil else { return }
        let value = PKPushRegistry(queue: .main)
        value.delegate = self; value.desiredPushTypes = [.voIP]; registry = value
    }
    func update(_ call: FamilyCallInfo, me: String) {
        guard let uuid = UUID(uuidString: call.id) else { return }
        if calls.contains(uuid) {
            if call.stage == "active" { expiryTasks.removeValue(forKey: uuid)?.cancel() }
            return
        }
        if call.callee == me && call.stage == "ringing" {
            report(uuid, caller: call.caller, video: call.kind == "video", expires: call.expires) { _ in }
        } else if call.caller == me {
            calls.insert(uuid)
            let action = CXStartCallAction(call: uuid, handle: CXHandle(type: .generic, value: call.callee))
            action.isVideo = call.kind == "video"
            controller.request(CXTransaction(action: action)) { [weak self] error in
                Task { @MainActor in
                    if error != nil {
                        self?.calls.remove(uuid)
                        if FamilyStore.shared.call?.id.lowercased() == uuid.uuidString.lowercased() { FamilyStore.shared.hangup() }
                    }
                }
            }
        }
    }
    func connected(_ id: String) {
        guard let uuid = UUID(uuidString: id), calls.contains(uuid) else { return }
        expiryTasks.removeValue(forKey: uuid)?.cancel()
        if FamilyStore.shared.call?.caller == FamilyStore.shared.me {
            provider.reportOutgoingCall(with: uuid, connectedAt: Date())
        }
    }
    func answer(_ id: String) {
        guard let uuid = UUID(uuidString: id), calls.contains(uuid) else { return }
        controller.request(CXTransaction(action: CXAnswerCallAction(call: uuid))) { _ in }
    }
    func end(_ id: String, reason: CXCallEndedReason = .remoteEnded) {
        guard let uuid = UUID(uuidString: id), calls.remove(uuid) != nil else { return }
        expiryTasks.removeValue(forKey: uuid)?.cancel()
        provider.reportCall(with: uuid, endedAt: Date(), reason: reason)
    }
    private func report(_ id: UUID, caller: String, video: Bool, expires: Int64, completion: @escaping (Error?) -> Void) {
        if calls.contains(id) { completion(nil); return }
        calls.insert(id)
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: caller)
        update.localizedCallerName = caller.components(separatedBy: "@")[0].capitalized
        update.hasVideo = video; update.supportsHolding = false; update.supportsGrouping = false; update.supportsUngrouping = false
        provider.reportNewIncomingCall(with: id, update: update) { error in
            Task { @MainActor in
                if error != nil {
                    self.calls.remove(id)
                    if FamilyStore.shared.call?.id.lowercased() == id.uuidString.lowercased() { FamilyStore.shared.hangup() }
                }
                completion(error)
            }
        }
        expiryTasks[id] = Task { [weak self] in
            let remaining = max(0, expires - Int64(Date().timeIntervalSince1970 * 1000))
            try? await Task.sleep(nanoseconds: UInt64(min(remaining, 45000)) * 1_000_000)
            guard !Task.isCancelled else { return }
            self?.end(id.uuidString, reason: .unanswered)
        }
    }
    nonisolated func pushRegistry(_ registry: PKPushRegistry, didUpdate credentials: PKPushCredentials, for type: PKPushType) {
        let value = credentials.token.map { String(format: "%02x", $0) }.joined()
        Task { @MainActor in
            token = value
            if !FamilyStore.shared.connected && !FamilyStore.shared.busy, let saved = FamilyCredentials.load() {
                FamilyStore.shared.login(saved.0, password: saved.1)
            } else { FamilyStore.shared.registerPushToken() }
        }
    }
    nonisolated func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        Task { @MainActor in token = nil; FamilyStore.shared.unregisterPushToken() }
    }
    nonisolated func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload,
                                 for type: PKPushType, completion: @escaping () -> Void) {
        let data = payload.dictionaryPayload
        Task { @MainActor in
            // Even malformed/expired VoIP pushes must be reported, then ended.
            let uuid = (data["callId"] as? String).flatMap(UUID.init(uuidString:)) ?? UUID()
            let expires = (data["expires"] as? NSNumber)?.int64Value ?? 0
            let caller = data["caller"] as? String ?? "Family member"
            report(uuid, caller: caller, video: data["kind"] as? String == "video", expires: expires) { error in
                completion()
                guard error == nil else { return }
                Task {
                    guard data["v"] as? Int == 1, expires > Int64(Date().timeIntervalSince1970 * 1000),
                          let callee = data["callee"] as? String,
                          FamilyCredentials.load()?.0 == callee,
                          await FamilyStore.shared.resumeForPush(uuid.uuidString) else {
                        self.end(uuid.uuidString, reason: .failed); return
                    }
                }
            }
        }
    }
    nonisolated func providerDidReset(_ provider: CXProvider) {
        Task { @MainActor in
            calls.removeAll(); expiryTasks.values.forEach { $0.cancel() }; expiryTasks.removeAll()
            FamilyStore.shared.hangup()
        }
    }
    nonisolated func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        Task { @MainActor in
            provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date()); action.fulfill()
        }
    }
    nonisolated func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        Task { @MainActor in
            if await FamilyStore.shared.answerFromSystem(action.callUUID.uuidString) { action.fulfill() }
            else { action.fail(); end(action.callUUID.uuidString, reason: .failed) }
        }
    }
    nonisolated func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        Task { @MainActor in
            if FamilyStore.shared.call?.id.lowercased() == action.callUUID.uuidString.lowercased() { FamilyStore.shared.hangup() }
            calls.remove(action.callUUID); action.fulfill()
        }
    }
    nonisolated func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        Task { @MainActor in
            guard FamilyStore.shared.call?.id.lowercased() == action.callUUID.uuidString.lowercased() else { action.fail(); return }
            if FamilyStore.shared.muted != action.isMuted { FamilyStore.shared.mute() }
            action.fulfill()
        }
    }
    nonisolated func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        RTCAudioSession.sharedInstance().audioSessionDidActivate(audioSession)
        RTCAudioSession.sharedInstance().isAudioEnabled = true
    }
    nonisolated func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        RTCAudioSession.sharedInstance().isAudioEnabled = false
        RTCAudioSession.sharedInstance().audioSessionDidDeactivate(audioSession)
    }
}
