import Foundation
import Combine
import XMPPFramework
import KissXML
import Shared
import UIKit

struct FamilyMemberRow: Identifiable {
    let jid: String
    let status: String
    var id: String { jid }
}
struct FamilyCallInfo: Identifiable {
    let id: String
    let caller: String
    let callee: String
    let kind: String
    let stage: String
    let version: Int64
    let expires: Int64
    let selected: Bool
    func peer(_ me: String) -> String { caller == me ? callee : caller }
    init?(_ object: [String: Any]) {
        guard let id = object["id"] as? String, let caller = object["caller"] as? String,
              let callee = object["callee"] as? String, let kind = object["kind"] as? String,
              let stage = object["stage"] as? String, let version = object["version"] as? NSNumber,
              let expires = object["expires"] as? NSNumber else { return nil }
        self.id = id; self.caller = caller; self.callee = callee; self.kind = kind
        self.stage = stage; self.version = version.int64Value; self.expires = expires.int64Value
        self.selected = object["selected"] as? Bool ?? false
    }
}
private enum FamilyError: Error { case disconnected, rejected(String), invalidResponse }

/** The socket and media session belong to the process, never a SwiftUI screen. */
@MainActor final class FamilyStore: NSObject, ObservableObject {
    static let shared = FamilyStore()
    static let domain = FamilyEndpoint().domain
    static let namespace = "urn:starception:family:1"
    @Published private(set) var connected = false
    @Published private(set) var busy = false
    @Published private(set) var serviceReady = false
    @Published private(set) var me = ""
    @Published private(set) var members: [FamilyMemberRow] = []
    @Published private(set) var call: FamilyCallInfo?
    @Published var message: String?
    @Published private(set) var muted = false
    @Published private(set) var cameraEnabled = true
    @Published private(set) var speakerEnabled = false
    @Published private(set) var mediaConnected = false
    @Published private(set) var connectedAt: Date?
    let media = FamilyWebRTC()
    private var stream: XMPPStream?
    private var password: String?
    private var pending: [String: CheckedContinuation<[String: Any], Error>] = [:]
    private var terminal: Set<String> = []
    private let callReducer = FamilyCallReducer()
    private var mediaCall: String?
    private var queuedSignals: [(String, [String: Any])] = []
    private var ringingTask: Task<Void, Never>?
    private var heartbeat: Task<Void, Never>?
    private var pendingCredential: (String, String)?

    override private init() {
        super.init()
        media.onSignal = { [weak self] payload in
            guard let self, let id = self.call?.id else { return }
            Task { do { _ = try await self.request("signal", ["callId": id, "payload": payload]) }
                catch { await self.failCall("Call signalling was interrupted.") } }
        }
        media.onConnected = { [weak self] connected in
            self?.mediaConnected = connected
            if connected && self?.connectedAt == nil { self?.connectedAt = Date() }
            if connected, let id = self?.call?.id { FamilySystemCalls.shared.connected(id) }
        }
        media.onFailure = { [weak self] message in Task { await self?.failCall(message) } }
    }
    func login(_ input: String, password: String) {
        guard !busy else { return }
        guard let jid = FamilyEndpoint().account(input: input),
              jid.hasSuffix("@" + Self.domain), !password.isEmpty else {
            message = "Enter your family account and password."; return
        }
        disconnect(clearSaved: false)
        me = jid; busy = true; self.password = password
        pendingCredential = (jid, password)
        let socket = XMPPStream()
        socket.myJID = XMPPJID(string: jid + "/ios-" + FamilyCredentials.deviceID)
        socket.hostName = Self.domain; socket.hostPort = UInt16(FamilyEndpoint().port)
        socket.startTLSPolicy = .required
        socket.addDelegate(self, delegateQueue: .main)
        stream = socket
        do { try socket.connect(withTimeout: 15) }
        catch { lost("Could not reach the family server. Check that this device’s network is allowed through the server firewall on TCP 5222.") }
    }
    func disconnect(clearSaved: Bool = true) {
        if clearSaved { unregisterPushToken(); FamilyCredentials.clear() }
        if let id = call?.id { FamilySystemCalls.shared.end(id) }
        let socket = stream
        if let current = call, let socket, socket.isAuthenticated,
           let data = try? JSONSerialization.data(withJSONObject: ["v": 1, "action": "end_call", "callId": current.id]) {
            let child = DDXMLElement(name: "family", xmlns: Self.namespace)
            child.stringValue = String(decoding: data, as: UTF8.self)
            socket.send(XMPPIQ(type: "set", to: XMPPJID(string: Self.domain), elementID: UUID().uuidString, child: child))
        }
        stream = nil; socket?.removeDelegate(self); socket?.disconnectAfterSending()
        password = nil; pendingCredential = nil; connected = false; serviceReady = false; busy = false; me = ""; members = []; connectedAt = nil
        media.close(); mediaCall = nil; call = nil; terminal.removeAll(); queuedSignals.removeAll(); callReducer.clear()
        ringingTask?.cancel(); heartbeat?.cancel(); heartbeat = nil
        rejectPending()
    }
    private func lost(_ text: String) {
        if let id = call?.id { FamilySystemCalls.shared.end(id, reason: .failed) }
        pendingCredential = nil
        connectedAt = nil
        password = nil; connected = false; serviceReady = false; busy = false; media.close(); mediaCall = nil
        call = nil; queuedSignals.removeAll(); ringingTask?.cancel(); heartbeat?.cancel()
        rejectPending(); message = text
    }
    private func rejectPending() {
        let waiting = Array(pending.values); pending.removeAll()
        waiting.forEach { $0.resume(throwing: FamilyError.disconnected) }
    }
    func refresh() {
        let activeStream = stream
        Task {
            do { try await self.apply(self.request("snapshot")) }
            catch {
                if connected && activeStream === stream {
                    serviceReady = false
                    message = "Signed in, but the Family service did not respond. Tap Refresh to retry."
                }
            }
        }
    }
    func invite(_ input: String) {
        guard let peer = FamilyEndpoint().account(input: input) else { message = "Enter a valid family account."; return }
        perform { try await self.apply(self.request("invite", ["peer": peer])) }
    }
    func acceptMember(_ peer: String) { perform { try await self.apply(self.request("accept_member", ["peer": peer])) } }
    func removeMember(_ peer: String) { perform { try await self.apply(self.request("remove_member", ["peer": peer])) } }
    func startCall(_ peer: String, video: Bool) {
        guard call == nil, members.contains(where: { $0.jid == peer && FamilyPolicy().canCall(relationship: $0.status) }) else { return }
        perform {
            guard await FamilyWebRTC.permissions(video: video) else {
                self.message = "Allow microphone access, and camera access for video calls, in Settings."; return
            }
            try await self.apply(self.request("invite_call", ["peer": peer, "kind": video ? "video" : "voice", "callId": UUID().uuidString]))
        }
    }
    func answer() {
        if let id = call?.id { FamilySystemCalls.shared.answer(id) }
    }
    func answerFromSystem(_ id: String) async -> Bool {
        guard await resumeForPush(id), let incoming = call else { return false }
        guard await FamilyWebRTC.permissions(video: incoming.kind == "video") else {
            message = "Allow microphone and camera access in Settings before answering."; return false
        }
        do {
            try await apply(request("accept_call", ["callId": incoming.id]))
            return call?.id == incoming.id && call?.stage == "active"
        } catch { return false }
    }
    func hangup() { if let current = call { endCall(current) } }
    private func endCall(_ current: FamilyCallInfo) {
        finish(current.id)
        Task { _ = try? await request("end_call", ["callId": current.id]) }
    }
    func previewCamera() async {
        guard let current = call, current.stage == "ringing", current.kind == "video", current.caller == me,
              UIApplication.shared.applicationState == .active else { return }
        if !cameraEnabled { media.close(); return }
        do { try await media.preview() }
        catch { if call?.id == current.id && call?.stage == "ringing" { media.close(); message = "Camera preview unavailable. You can still continue the call." } }
    }
    func mute() { muted.toggle(); media.mute(muted) }
    func camera() { cameraEnabled.toggle(); media.camera(cameraEnabled) }
    func switchCamera() { media.switchCamera() }
    func speaker() { speakerEnabled.toggle(); media.speaker(speakerEnabled) }
    func backgrounded() {
        // iOS suspends camera capture in the background; voice stays owned by CallKit.
        if call?.stage == "ringing" { media.close() }
        else if call?.kind == "video", cameraEnabled { camera() }
    }
    func registerPushToken() {
        guard connected, let token = FamilySystemCalls.shared.token else { return }
        let environment = Bundle.main.object(forInfoDictionaryKey: "FamilyAPNSEnvironment") as? String == "development" ? "sandbox" : "production"
        perform { _ = try await self.request("register_device", ["deviceId": FamilyCredentials.deviceID,
            "platform": "ios", "token": token, "environment": environment]) }
    }
    func unregisterPushToken() {
        guard let socket = stream, socket.isAuthenticated,
              let data = try? JSONSerialization.data(withJSONObject: ["v": 1, "action": "unregister_device", "deviceId": FamilyCredentials.deviceID]) else { return }
        let child = DDXMLElement(name: "family", xmlns: Self.namespace)
        child.stringValue = String(decoding: data, as: UTF8.self)
        socket.send(XMPPIQ(type: "set", to: XMPPJID(string: Self.domain), elementID: UUID().uuidString, child: child))
    }
    func resumeForPush(_ id: String) async -> Bool {
        if !connected {
            guard let saved = FamilyCredentials.load() else { return false }
            if !busy { login(saved.0, password: saved.1) }
            for _ in 0..<150 {
                if connected || !busy { break }
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
        }
        guard connected else { return false }
        do {
            try await apply(request("snapshot"))
            return call?.id.lowercased() == id.lowercased() && call?.callee == me && call?.stage == "ringing"
        } catch { return false }
    }

    private func perform(_ action: @escaping @MainActor () async throws -> Void) {
        Task { do { try await action() }
            catch FamilyError.rejected(let code) { message = Self.friendly(code) }
            catch { message = "Could not complete this action. Refresh your family list and try again." } }
    }
    private func request(_ action: String, _ fields: [String: Any] = [:]) async throws -> [String: Any] {
        guard let socket = stream, socket.isAuthenticated else { throw FamilyError.disconnected }
        var object = fields; object["v"] = 1; object["action"] = action
        let data = try JSONSerialization.data(withJSONObject: object)
        let child = DDXMLElement(name: "family", xmlns: Self.namespace)
        child.stringValue = String(decoding: data, as: UTF8.self)
        let id = UUID().uuidString
        let iq = XMPPIQ(type: "set", to: XMPPJID(string: Self.domain), elementID: id, child: child)
        return try await withCheckedThrowingContinuation { continuation in
            pending[id] = continuation
            socket.send(iq)
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                self?.pending.removeValue(forKey: id)?.resume(throwing: FamilyError.disconnected)
            }
        }
    }
    private func apply(_ body: [String: Any]) async {
        if let rows = body["members"] as? [[String: String]] {
            serviceReady = true
            members = rows.compactMap { row in
                guard let jid = row["jid"], let status = row["status"] else { return nil }
                return FamilyMemberRow(jid: jid, status: status)
            }
        }
        if let current = body["call"] as? [String: Any] { await updateCall(current) }
        if let calls = body["calls"] as? [[String: Any]] {
            if let first = calls.first { await updateCall(first) }
            else if let current = call { finish(current.id) }
        }
    }
    private func event(_ body: [String: Any]) async {
        guard connected, body["v"] as? Int == 1 else { return }
        if body["event"] as? String == "signal" {
            guard let id = body["callId"] as? String, call?.id == id, !terminal.contains(id),
                  let payload = body["payload"] as? [String: Any] else { return }
            if mediaCall == id && media.ready { await media.receive(payload) }
            else if queuedSignals.count < 64 { queuedSignals.append((id, payload)) }
        } else { await apply(body) }
    }
    private func updateCall(_ object: [String: Any]) async {
        guard let next = FamilyCallInfo(object), !terminal.contains(next.id) else { return }
        if let current = call, current.id != next.id || current.version >= next.version { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        guard callReducer.apply(id: next.id, revision: next.version, next: next.stage, expiresAt: next.expires, now: now) else { return }
        if callReducer.stage == "ended" || (next.stage == "active" && !next.selected) { finish(next.id); return }
        if next.stage == "ringing" && next.expires <= now { finish(next.id); return }
        call = next
        FamilySystemCalls.shared.update(next, me: me)
        if next.stage == "active" && mediaCall != next.id { speakerEnabled = next.kind == "video" }
        if next.stage == "active" && next.kind == "video" && UIApplication.shared.applicationState != .active { cameraEnabled = false }
        if next.stage == "ringing" {
            ringingTask?.cancel()
            ringingTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(max(0, next.expires - now)) * 1_000_000)
                guard !Task.isCancelled, self?.call?.id == next.id, self?.call?.stage == "ringing" else { return }
                self?.finish(next.id)
            }
        }
        if next.stage == "active", mediaCall != next.id {
            ringingTask?.cancel(); mediaCall = next.id
            do {
                let response = try await request("turn", ["callId": next.id])
                guard call?.id == next.id, let servers = response["iceServers"] as? [[String: Any]] else { return }
                try await media.start(servers, video: next.kind == "video", caller: next.caller == me)
                let queued = queuedSignals.filter { $0.0 == next.id }; queuedSignals.removeAll()
                for (_, payload) in queued { await media.receive(payload) }
            } catch { if call?.id == next.id { await failCall("Could not start media. Check permissions and relay configuration.") } }
        }
    }
    private func finish(_ id: String) {
        callReducer.finishCall(id: id)
        terminal.insert(id)
        // The server expires/prunes calls; retain all terminal IDs for this signed-in session.
        guard let current = call, current.id == id else { return }
        FamilySystemCalls.shared.end(id)
        call = nil; ringingTask?.cancel(); media.close(); mediaCall = nil; queuedSignals.removeAll()
        muted = false; cameraEnabled = true; speakerEnabled = false; mediaConnected = false; connectedAt = nil
    }
    private func failCall(_ text: String) async {
        if let current = call { finish(current.id); _ = try? await request("end_call", ["callId": current.id]) }
        message = text
    }
    private static func friendly(_ code: String) -> String {
        switch code {
        case "not_family": return "Only accepted family members can call each other."
        case "busy": return "One of you is already in a call."
        case "invitation_not_found", "accept_invitation_first": return "Accept the pending invitation first."
        case "invalid_member", "member_not_found": return "Enter a valid family account on this server."
        case "call_ended", "answered_elsewhere": return "This call ended or was answered on another device."
        default: return "Could not complete this action. Refresh your family list and try again."
        }
    }
}

extension FamilyStore: XMPPStreamDelegate {
    nonisolated func xmppStreamDidConnect(_ sender: XMPPStream) {
        Task { @MainActor in
            guard sender === stream, let secret = password else { return }
            do { try sender.authenticate(withPassword: secret) }
            catch { lost("Could not authenticate your family account.") }
        }
    }
    nonisolated func xmppStreamDidAuthenticate(_ sender: XMPPStream) {
        Task { @MainActor in
            guard sender === stream else { return }
            password = nil; connected = true; busy = false
            if let saved = pendingCredential { FamilyCredentials.save(jid: saved.0, password: saved.1) }
            pendingCredential = nil
            registerPushToken()
            sender.send(XMPPPresence())
            refresh()
            heartbeat?.cancel()
            heartbeat = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 20_000_000_000)
                    guard !Task.isCancelled, let self, self.connected else { return }
                    self.refresh()
                }
            }
        }
    }
    nonisolated func xmppStream(_ sender: XMPPStream, didNotAuthenticate error: DDXMLElement) {
        Task { @MainActor in if sender === stream { lost("Could not sign in. Check your family account and password.") } }
    }
    nonisolated func xmppStreamDidDisconnect(_ sender: XMPPStream, withError error: Error?) {
        Task { @MainActor in if sender === stream { lost(connected ? "Family connection closed. Sign in again before calling." : "Could not connect securely to the family server. Check the device clock, certificate and firewall access on TCP 5222.") } }
    }
    nonisolated func xmppStream(_ sender: XMPPStream, didReceive iq: XMPPIQ) -> Bool {
        guard iq.type == "result" || iq.type == "error" else { return false }
        Task { @MainActor in
            guard sender === stream, iq.from == nil || iq.from?.full == Self.domain,
                  let id = iq.elementID, let continuation = pending.removeValue(forKey: id) else { return }
            guard iq.type == "result", let text = iq.elements(forLocalName: "family", uri: Self.namespace).first?.stringValue,
                  let data = text.data(using: .utf8),
                  let result = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                continuation.resume(throwing: FamilyError.invalidResponse); return
            }
            if result["ok"] as? Bool == true { continuation.resume(returning: result) }
            else { continuation.resume(throwing: FamilyError.rejected(result["error"] as? String ?? "unavailable")) }
        }
        return true
    }
    nonisolated func xmppStream(_ sender: XMPPStream, didReceive message: XMPPMessage) {
        Task { @MainActor in
            guard sender === stream, message.from?.full == Self.domain,
                  let text = message.body, let data = text.data(using: .utf8),
                  let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
            await event(object)
        }
    }
}
