import Foundation
import Shared

struct FamilyChatMessage: Identifiable, Equatable {
    let id: String
    let from: String
    let to: String
    let text: String
    let sequence: Int64
    let createdAt: Int64
    let call: FamilyChatCall?
    var deliveredAt: Int64
    var seenAt: Int64
    init?(_ o: [String: Any]) {
        guard let id = o["id"] as? String, let from = o["from"] as? String, let to = o["to"] as? String,
              let text = o["text"] as? String, let seq = o["sequence"] as? NSNumber, let time = o["createdAt"] as? NSNumber else { return nil }
        self.id = id; self.from = from; self.to = to; self.text = text; sequence = seq.int64Value; createdAt = time.int64Value
        deliveredAt = max(0, (o["deliveredAt"] as? NSNumber)?.int64Value ?? 0)
        seenAt = max(0, (o["seenAt"] as? NSNumber)?.int64Value ?? 0)
        if o["type"] as? String == "call", let value = o["call"] as? [String: Any],
           let entry = FamilyChatCall(value), id == "call:\(entry.id)" { call = entry } else { call = nil }
    }
    func preview(me: String) -> String {
        guard let call else { return text }
        let policy = FamilyCallHistoryPolicy()
        return "\(policy.title(kind: call.kind)) · \(policy.status(outcome: call.outcome, outgoing: from == me))"
    }
}
struct FamilyChatCall: Equatable {
    let id: String
    let kind: String
    let outcome: String
    let durationSeconds: Int64?
    init?(_ o: [String: Any]) {
        guard let id = o["id"] as? String, !id.isEmpty, let kind = o["kind"] as? String,
              let outcome = o["outcome"] as? String, FamilyCallHistoryPolicy().valid(kind: kind, outcome: outcome) else { return nil }
        let duration = (o["durationSeconds"] as? NSNumber)?.int64Value
        guard duration == nil || (duration! >= 0 && duration! <= 86400) else { return nil }
        self.id = id; self.kind = kind; self.outcome = outcome; durationSeconds = duration
    }
}
struct FamilyConversation: Identifiable {
    let peer: String
    let unread: Int
    let lastMessage: FamilyChatMessage?
    var id: String { peer }
}
struct FamilyNotice: Identifiable {
    let id: String
    let kind: String
    let peer: String
    let count: Int
    let unread: Bool
    let createdAt: Int64
}
