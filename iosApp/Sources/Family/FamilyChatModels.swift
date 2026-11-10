import Foundation

struct FamilyChatMessage: Identifiable, Equatable {
    let id: String
    let from: String
    let to: String
    let text: String
    let sequence: Int64
    let createdAt: Int64
    init?(_ o: [String: Any]) {
        guard let id = o["id"] as? String, let from = o["from"] as? String, let to = o["to"] as? String,
              let text = o["text"] as? String, let seq = o["sequence"] as? NSNumber, let time = o["createdAt"] as? NSNumber else { return nil }
        self.id = id; self.from = from; self.to = to; self.text = text; sequence = seq.int64Value; createdAt = time.int64Value
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
