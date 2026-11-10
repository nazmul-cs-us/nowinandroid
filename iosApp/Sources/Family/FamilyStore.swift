import Foundation
import OSLog
import Combine
import XMPPFramework
import KissXML
import Shared
import UIKit

struct FamilyWebPairing: Identifiable {
    let code: String
    let url: URL
    let expires: Date
    var id: String { code }
}
struct FamilyMemberRow: Identifiable, Equatable {
    let jid: String
    let status: String
    var presence: String = "unknown"
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
    @Published private(set) var webPairing: FamilyWebPairing?
    @Published private(set) var me = ""
    @Published private(set) var members: [FamilyMemberRow] = []
    @Published private(set) var call: FamilyCallInfo?
    @Published var message: String?
    @Published private(set) var callMessage: String?
    @Published private(set) var pushRegistrationMessage: String?
    @Published private(set) var muted = false
    @Published private(set) var cameraEnabled = true
    @Published private(set) var speakerEnabled = false
    @Published private(set) var mediaConnected = false
    @Published private(set) var connectionRoute = "unknown"
    @Published private(set) var connectedAt: Date?
    @Published private(set) var conversations: [FamilyConversation] = []
    @Published private(set) var notifications: [FamilyNotice] = []
    @Published private(set) var unreadCount = 0
    @Published private(set) var chatPeer: String?
    @Published private(set) var messages: [FamilyChatMessage] = []
    @Published private(set) var chatLoading = false
    @Published private(set) var chatSending = false
    @Published private(set) var nextBefore: Int64 = 0
    @Published var chatDraft = ""
    @Published private(set) var chatError: String?
    private var retryMessage: (id: String, text: String)?
    private var chatGeneration = 0
    private var inboxVersion: Int64 = 0
    let media = FamilyWebRTC()
    private let logger = Logger(subsystem: "com.starception.submission", category: "FamilyCalls")
    private var stream: XMPPStream?
    private var password: String?
    private var registering = false
    private var accountJustCreated = false
    private var pending: [String: CheckedContinuation<[String: Any], Error>] = [:]
    private var terminal: Set<String> = []
    private let callReducer = FamilyCallReducer()
    private let presenceCache = FamilyPresenceCache()
    private let restorePolicy = FamilySessionRestorePolicy()
    private var foreground = false
    private var activityVersion: Int64 = 0
    private var mediaCall: String?
    private var queuedSignals: [(String, [String: Any])] = []
    private var ringingTask: Task<Void, Never>?
    private var heartbeat: Task<Void, Never>?
    private var pendingCredential: (String, String)?

    override private init() {
        super.init()
        logger.notice("Diagnostics v2 loaded")
        foreground = UIApplication.shared.applicationState == .active
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
        media.onRoute = { [weak self] route in self?.connectionRoute = route }
        media.onFailure = { [weak self] message in Task { await self?.failCall(message) } }
    }
    func activityChanged(_ active: Bool) {
        if active { restoreSavedSession() }
        guard foreground != active else { return }
        foreground = active; activityVersion += 1
        guard connected else { return }
        Task { _ = try? await request("presence") }
    }
    func restoreSavedSession() {
        guard restorePolicy.canRestore(connected: connected, busy: busy), let saved = FamilyCredentials.load() else { return }
        login(saved.0, password: saved.1, restoring: true)
    }
    func requestWebHelper() {
        message = nil
        perform {
            let reply = try await self.request("pair_web_helper", ["deviceId": FamilyCredentials.deviceID])
            guard let value = reply["webPairing"] as? [String: Any], let code = value["code"] as? String,
                  let text = value["url"] as? String, let url = URL(string: text), url.scheme == "https",
                  url.host == Self.domain, url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == "family-helper",
                  let expires = value["expires"] as? NSNumber else { throw FamilyError.invalidResponse }
            self.webPairing = FamilyWebPairing(code: code, url: url,
                expires: Date(timeIntervalSince1970: expires.doubleValue / 1000))
        }
    }
    func dismissWebHelper() { webPairing = nil }
    func signUp(_ input: String, password: String, confirmation: String) {
        guard !busy, !connected else { return }
        let policy = FamilyRegistrationPolicy()
        if let code = policy.validate(input: input, password: password, confirmation: confirmation) {
            message = policy.message(code: code); return
        }
        guard let username = policy.username(input: input) else { return }
        login(username, password: password, createAccount: true)
    }
    func login(_ input: String, password: String, createAccount: Bool = false, restoring: Bool = false) {
        guard !busy else { return }
        if !restoring { restorePolicy.manualSignIn() }
        guard let jid = FamilyEndpoint().account(input: input),
              jid.hasSuffix("@" + Self.domain), !password.isEmpty else {
            message = "Enter your family account and password."; return
        }
        disconnect(clearSaved: false)
        me = jid; busy = true; self.password = password; registering = createAccount; message = nil
        pendingCredential = (jid, password)
        let socket = XMPPStream()
        socket.myJID = XMPPJID(string: jid + "/ios-" + FamilyCredentials.deviceID + "-" + String(UUID().uuidString.prefix(8)))
        socket.hostName = Self.domain; socket.hostPort = UInt16(FamilyEndpoint().port)
        socket.startTLSPolicy = .required
        socket.addDelegate(self, delegateQueue: .main)
        stream = socket
        do { try socket.connect(withTimeout: 15) }
        catch { lost("Could not reach the family server. Check that this device’s network is allowed through the server firewall on TCP 5222.") }
    }
    func disconnect(clearSaved: Bool = true) {
        logger.info("Session reset; clearSaved=\(clearSaved); hadCall=\(self.call != nil)")
        if clearSaved { restorePolicy.explicitSignOut(); unregisterPushToken(); FamilyCredentials.clear() }
        inboxVersion = 0; conversations = []; notifications = []; unreadCount = 0; closeChat()
        presenceCache.clear(); webPairing = nil
        callMessage = nil; pushRegistrationMessage = nil
        if let id = call?.id { FamilySystemCalls.shared.end(id) }
        let socket = stream
        if let current = call, let socket, socket.isAuthenticated,
           let data = try? JSONSerialization.data(withJSONObject: ["v": 1, "action": "end_call", "callId": current.id]) {
            let child = DDXMLElement(name: "family", xmlns: Self.namespace)
            child.stringValue = String(decoding: data, as: UTF8.self)
            socket.send(XMPPIQ(type: "set", to: XMPPJID(string: Self.domain), elementID: UUID().uuidString, child: child))
        }
        stream = nil; socket?.removeDelegate(self); socket?.disconnectAfterSending()
        password = nil; registering = false; accountJustCreated = false; pendingCredential = nil; connected = false; serviceReady = false; busy = false; me = ""; members = []; connectedAt = nil
        media.close(); mediaCall = nil; call = nil; terminal.removeAll(); queuedSignals.removeAll(); callReducer.clear()
        ringingTask?.cancel(); heartbeat?.cancel(); heartbeat = nil
        rejectPending()
    }
    private func lost(_ text: String) {
        logger.warning("Session lost; hadCall=\(self.call != nil)")
        presenceCache.clear(); webPairing = nil
        if let id = call?.id { FamilySystemCalls.shared.end(id, reason: .failed) }
        pendingCredential = nil
        callMessage = nil
        connectedAt = nil
        password = nil; connected = false; serviceReady = false; busy = false; media.close(); mediaCall = nil
        call = nil; queuedSignals.removeAll(); ringingTask?.cancel(); heartbeat?.cancel()
        rejectPending(); message = text
        let failed = stream; stream = nil; failed?.removeDelegate(self); failed?.disconnect()
    }
    private func rejectPending() {
        let waiting = Array(pending.values); pending.removeAll()
        waiting.forEach { $0.resume(throwing: FamilyError.disconnected) }
    }
    func openChat(_ peer: String) {
        guard members.contains(where: { $0.jid == peer && $0.status == "accepted" }) else { return }
        closeChat(); chatPeer = peer; loadMessages()
    }
    func closeChat() { chatGeneration += 1; chatPeer = nil; messages = []; chatDraft = ""; chatError = nil; chatLoading = false; chatSending = false; nextBefore = 0; retryMessage = nil }
    func loadMessages(older: Bool = false) {
        guard let peer = chatPeer, !chatLoading else { return }
        let before: Int64 = older ? nextBefore : 9007199254740991
        guard before > 0 else { return }
        let chatToken = chatGeneration
        chatLoading = true; chatError = nil
        Task {
            defer { if chatPeer == peer && chatGeneration == chatToken { chatLoading = false } }
            do {
                let reply = try await request("chat_history", ["peer": peer, "before": before])
                guard chatPeer == peer, chatGeneration == chatToken else { return }
                mergeMessages((reply["messages"] as? [[String: Any]] ?? []).compactMap(FamilyChatMessage.init))
                nextBefore = (reply["nextBefore"] as? NSNumber)?.int64Value ?? 0
            } catch { if chatPeer == peer && chatGeneration == chatToken { chatError = chatErrorText(error) } }
        }
    }
    func sendMessage() {
        guard let peer = chatPeer, !chatSending else { return }
        guard FamilyChatPolicy().valid(text: chatDraft) else { chatError = FamilyChatPolicy().error(); return }
        let chatToken = chatGeneration
        let text = chatDraft
        let id = retryMessage?.text == text ? retryMessage!.id : UUID().uuidString.lowercased()
        retryMessage = (id, text); chatSending = true; chatError = nil
        Task {
            defer { if chatPeer == peer && chatGeneration == chatToken { chatSending = false } }
            do {
                let reply = try await request("send_message", ["peer": peer, "id": id, "text": text])
                guard chatPeer == peer, chatGeneration == chatToken, let row = reply["message"] as? [String: Any], let message = FamilyChatMessage(row) else { return }
                mergeMessages([message]); if chatDraft == text { chatDraft = "" }; retryMessage = nil
            } catch { if chatPeer == peer && chatGeneration == chatToken { chatError = chatErrorText(error) } }
        }
    }
    private func mergeMessages(_ rows: [FamilyChatMessage]) {
        var byID = Dictionary(uniqueKeysWithValues: messages.map { ($0.id, $0) })
        rows.forEach { byID[$0.id] = $0 }; messages = byID.values.sorted { $0.sequence < $1.sequence }
    }
    func markChatRead() {
        guard foreground, let peer = chatPeer, let last = messages.last else { return }
        Task { if let reply = try? await request("read_messages", ["peer": peer, "through": last.sequence]) { await apply(reply) } }
    }
    func acknowledgeNotifications() { perform { try await self.apply(self.request("ack_notifications")) } }
    private func chatErrorText(_ error: Error) -> String {
        if case FamilyError.rejected(let code) = error {
            switch code {
            case "not_family": return "This conversation is no longer available. Refresh your family list."
            case "message_rate_limited": return "You’re sending messages too quickly. Please wait a moment."
            case "chat_storage_full": return "Chat storage is full. Contact the server administrator."
            default: break
            }
        }
        return "Could not sync this conversation. Your draft is still here. Please retry."
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
        message = nil; callMessage = nil
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
    func acceptIncomingCall(_ id: String) async -> Bool {
        guard await resumeForPush(id), let incoming = call else { return false }
        guard await FamilyWebRTC.permissions(video: incoming.kind == "video") else {
            callMessage = "Allow microphone and camera access in Settings before answering."; return false
        }
        do {
            try await apply(request("accept_call", ["callId": incoming.id]))
            return call?.id == incoming.id && call?.stage == "active"
        } catch {
            if call?.id == incoming.id { callMessage = "Could not answer this call. Please retry." }
            return false
        }
    }
    func systemCallUnavailable(_ id: String) {
        guard call?.id.lowercased() == id.lowercased(), media.ready else { return }
        do { try media.activateInAppAudio() }
        catch { Task {
            guard call?.id.lowercased() == id.lowercased() else { return }
            await failCall("Could not activate call audio.")
        } }
    }
    func hangup() { if let current = call { endCall(current) } }
    private func endCall(_ current: FamilyCallInfo) {
        finish(current.id, reason: "local-hangup")
        Task { _ = try? await request("end_call", ["callId": current.id]) }
    }
    func previewCamera() async {
        guard let current = call, current.stage == "ringing", current.kind == "video", current.caller == me,
              UIApplication.shared.applicationState == .active else { return }
        if !cameraEnabled { media.close(); return }
        do { try await media.preview(); callMessage = nil }
        catch { if call?.id == current.id && call?.stage == "ringing" { media.close(); callMessage = "Camera preview could not start. Cancel and retry the video call." } }
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
        let activeStream = stream
        Task {
            do {
                _ = try await self.request("register_device", ["deviceId": FamilyCredentials.deviceID,
                    "platform": "ios", "token": token, "environment": environment])
                if activeStream === stream { pushRegistrationMessage = nil }
            } catch {
                if activeStream === stream { pushRegistrationMessage = "Background call registration is unavailable. Calls can still work while both apps are open." }
            }
        }
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
        if action == "snapshot" || action == "presence" {
            object["active"] = foreground; object["activityVersion"] = activityVersion
        }
        let data = try JSONSerialization.data(withJSONObject: object)
        let child = DDXMLElement(name: "family", xmlns: Self.namespace)
        child.stringValue = String(decoding: data, as: UTF8.self)
        let id = UUID().uuidString
        let iq = XMPPIQ(type: "set", to: XMPPJID(string: Self.domain), elementID: id, child: child)
        let callRevision = callReducer.snapshotRevision
        var response: [String: Any] = try await withCheckedThrowingContinuation { continuation in
            pending[id] = continuation
            socket.send(iq)
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                self?.pending.removeValue(forKey: id)?.resume(throwing: FamilyError.disconnected)
            }
        }
        response["_localCallRevision"] = callRevision
        return response
    }
    private func apply(_ body: [String: Any]) async {
        if let revision = body["inboxVersion"] as? NSNumber, revision.int64Value > inboxVersion {
            inboxVersion = revision.int64Value
            if let rows = body["conversations"] as? [[String: Any]] {
                conversations = rows.compactMap { o in
                    guard let peer = o["peer"] as? String else { return nil }
                    return FamilyConversation(peer: peer, unread: o["unread"] as? Int ?? 0,
                        lastMessage: (o["lastMessage"] as? [String: Any]).flatMap(FamilyChatMessage.init))
                }
            }
            if let rows = body["notifications"] as? [[String: Any]] {
                notifications = rows.compactMap { o in
                    guard let id = o["id"] as? String, let kind = o["kind"] as? String, let peer = o["peer"] as? String else { return nil }
                    return FamilyNotice(id: id, kind: kind, peer: peer, count: o["count"] as? Int ?? 0,
                        unread: o["unread"] as? Bool ?? false, createdAt: (o["createdAt"] as? NSNumber)?.int64Value ?? 0)
                }.sorted { $0.createdAt > $1.createdAt }
            }
            unreadCount = body["unreadCount"] as? Int ?? 0
        }
        if body["event"] as? String == "chat", let row = body["message"] as? [String: Any],
           let message = FamilyChatMessage(row), let peer = chatPeer,
           Set([message.from, message.to]) == Set([peer, me]) { mergeMessages([message]) }

        presenceCache.beginEpoch(next: body["presenceEpoch"] as? String ?? "")
        if let rows = body["members"] as? [[String: Any]] {
            serviceReady = true
            let next = rows.compactMap { row -> FamilyMemberRow? in
                guard let jid = row["jid"] as? String, let status = row["status"] as? String else { return nil }
                if status == "accepted" {
                    _ = presenceCache.apply(jid: jid, status: row["presence"] as? String ?? "unknown",
                        revision: (row["presenceVersion"] as? NSNumber)?.int64Value ?? 0)
                }
                return FamilyMemberRow(jid: jid, status: status)
            }
            presenceCache.retain(accepted: next.filter { $0.status == "accepted" }.map(\.jid))
            publishMembers(next)
        } else if body["event"] as? String == "presence" {
            if let jid = body["jid"] as? String, members.contains(where: { $0.jid == jid && $0.status == "accepted" }) {
                _ = presenceCache.apply(jid: jid, status: body["presence"] as? String ?? "unknown",
                    revision: (body["presenceVersion"] as? NSNumber)?.int64Value ?? 0)
            }
            publishMembers(members)
        }
        if body["members"] != nil, let peer = chatPeer, !members.contains(where: { $0.jid == peer && $0.status == "accepted" }) { closeChat() }
        if let current = body["call"] as? [String: Any] { await updateCall(current) }
        if let calls = body["calls"] as? [[String: Any]] {
            if let first = calls.first { await updateCall(first) }
            else if let revision = body["_localCallRevision"] as? Int64,
                    callReducer.canClearFromSnapshot(requestRevision: revision), let current = call { finish(current.id, reason: "current-empty-snapshot") }
        }
    }
    private func publishMembers(_ rows: [FamilyMemberRow]) {
        let next = rows.map { row -> FamilyMemberRow in
            var member = row; member.presence = presenceCache.status(jid: row.jid); return member
        }
        if next != members { members = next }
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
        let rawReason = object["reason"] as? String ?? ""
        let reason = ["hangup", "expired", "family_removed", ""].contains(rawReason) ? rawReason : "other"
        logger.info("Call update: stage=\(next.stage, privacy: .public); version=\(next.version); selected=\(next.selected); remainingMs=\(next.expires - now); serverReason=\(reason, privacy: .public)")
        if callReducer.stage == "ended" || (next.stage == "active" && !next.selected) { finish(next.id, reason: "terminal-expired-or-other-device"); return }
        if next.stage == "ringing" && next.expires <= now { finish(next.id, reason: "terminal-expired-or-other-device"); return }
        if call?.id != next.id { callMessage = nil }
        call = next
        FamilySystemCalls.shared.update(next, me: me)
        if next.stage == "active" && mediaCall != next.id { speakerEnabled = next.kind == "video" }
        if next.stage == "active" && next.kind == "video" && UIApplication.shared.applicationState != .active { cameraEnabled = false }
        if next.stage == "ringing" {
            ringingTask?.cancel()
            ringingTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(max(0, next.expires - now)) * 1_000_000)
                guard !Task.isCancelled, self?.call?.id == next.id, self?.call?.stage == "ringing" else { return }
                self?.finish(next.id, reason: "ring-timeout")
            }
        }
        if next.stage == "active", mediaCall != next.id {
            ringingTask?.cancel(); mediaCall = next.id
            do {
                let response = try await request("turn", ["callId": next.id])
                guard call?.id == next.id, let servers = response["iceServers"] as? [[String: Any]] else { return }
                try await media.start(servers, video: next.kind == "video", caller: next.caller == me,
                                      systemAudio: FamilySystemCalls.shared.usesSystemAudio(next.id))
                let queued = queuedSignals.filter { $0.0 == next.id }; queuedSignals.removeAll()
                for (_, payload) in queued { await media.receive(payload) }
            } catch { if call?.id == next.id { await failCall("Could not start media. Check permissions and relay configuration.") } }
        }
    }
    private func finish(_ id: String, reason: String = "media-failure") {
        callReducer.finishCall(id: id)
        terminal.insert(id)
        // The server expires/prunes calls; retain all terminal IDs for this signed-in session.
        guard let current = call, current.id == id else { return }
        logger.info("Ending local call presentation: \(reason, privacy: .public)")
        FamilySystemCalls.shared.end(id)
        call = nil; ringingTask?.cancel(); media.close(); mediaCall = nil; queuedSignals.removeAll()
        muted = false; cameraEnabled = true; speakerEnabled = false; mediaConnected = false; connectedAt = nil; callMessage = nil
    }
    private func failCall(_ text: String) async {
        logger.warning("Media/signalling failure; hadCall=\(self.call != nil)")
        if let current = call { finish(current.id); _ = try? await request("end_call", ["callId": current.id]) }
        message = text
    }
    private static func friendly(_ code: String) -> String {
        switch code {
        case "not_family": return "Only accepted family members can call each other."
        case "busy": return "One of you is already in a call."
        case "web_helper_unavailable": return "Temporary call alerts are not enabled on the server yet."
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
            do {
                guard sender.isSecure else { throw FamilyError.disconnected }
                if registering {
                    guard sender.supportsInBandRegistration else {
                        lost(FamilyRegistrationPolicy().message(code: "not-allowed")); return
                    }
                    try sender.register(withPassword: secret)
                }
                else { try sender.authenticate(withPassword: secret) }
            } catch { lost("Could not connect securely to create or sign in to your Family account.") }
        }
    }
    nonisolated func xmppStreamDidRegister(_ sender: XMPPStream) {
        Task { @MainActor in
            guard sender === stream, registering, let secret = password else { return }
            registering = false; accountJustCreated = true
            do { try sender.authenticate(withPassword: secret) }
            catch { lost("Account created. Sign in with your new username and password.") }
        }
    }
    nonisolated func xmppStream(_ sender: XMPPStream, didNotRegister error: DDXMLElement) {
        Task { @MainActor in
            guard sender === stream, registering else { return }
            let container = error.elements(forName: "error").first
            let conditions = ["conflict", "resource-constraint", "not-allowed", "forbidden", "not-acceptable", "bad-request", "service-unavailable"]
            let code = conditions.first { !(container?.elements(forLocalName: $0, uri: "urn:ietf:params:xml:ns:xmpp-stanzas").isEmpty ?? true) } ?? "unknown"
            registering = false
            lost(FamilyRegistrationPolicy().message(code: code))
            stream = nil; sender.removeDelegate(self); sender.disconnect()
        }
    }
    nonisolated func xmppStreamDidAuthenticate(_ sender: XMPPStream) {
        Task { @MainActor in
            guard sender === stream else { return }
            password = nil; registering = false; accountJustCreated = false; connected = true; busy = false
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
        Task { @MainActor in if sender === stream { restorePolicy.authenticationRejected(); lost(accountJustCreated ? "Account created. Sign in with your new username and password." : "Could not sign in. Check your family account and password.") } }
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
