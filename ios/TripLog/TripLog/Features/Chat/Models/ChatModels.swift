import Foundation

struct ChatMessage: Codable, Identifiable, Hashable {
    let id: Int64
    let tripGroupId: Int64
    let senderId: Int64?
    let senderName: String?
    let messageType: String
    let content: String
    let createdAt: String

    var isSystem: Bool { messageType == "SYSTEM" }
    var timeLabel: String { LocalDateTimeFormatter.time(from: createdAt) }
}

struct ChatMessagePage: Codable {
    let messages: [ChatMessage]
    let hasNext: Bool
}

struct ChatReadStatus: Codable, Hashable {
    let memberId: Int64
    let lastReadMessageId: Int64
}

struct ChatReadStatusPage: Codable {
    let totalMemberCount: Int
    let statuses: [ChatReadStatus]
}

struct ChatReadRequest: Encodable { let lastReadMessageId: Int64 }
