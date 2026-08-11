import Foundation
import Combine

@MainActor
final class ChatService {
    static let shared = ChatService()
    private let apiClient: APIClient
    private let encoder = JSONEncoder()
    private init() { apiClient = .shared }

    func history(tripID: Int64, cursor: Int64?, size: Int = 30) async throws -> ChatMessagePage {
        var path = "/api/v1/trips/\(tripID)/chat/messages?size=\(size)"
        if let cursor { path += "&cursor=\(cursor)" }
        let (response, _): (APIResponse<ChatMessagePage>, HTTPURLResponse) = try await apiClient.send(
            path: path,
            authenticated: true
        )
        return response.data
    }

    func messagesAfter(tripID: Int64, cursor: Int64) async throws -> ChatMessagePage {
        let (response, _): (APIResponse<ChatMessagePage>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/chat/messages/after?cursor=\(cursor)&size=100",
            authenticated: true
        )
        return response.data
    }

    func readStatuses(tripID: Int64) async throws -> ChatReadStatusPage {
        let (response, _): (APIResponse<ChatReadStatusPage>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/chat/read-statuses",
            authenticated: true
        )
        return response.data
    }

    func unreadCounts() async throws -> [Int64: Int] {
        let (response, _): (APIResponse<[String: Int]>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/chat/unread-counts",
            authenticated: true
        )
        return response.data.reduce(into: [:]) { result, item in
            guard let tripID = Int64(item.key) else { return }
            result[tripID] = item.value
        }
    }

    func unreadTalkCounts(
        memberID: Int64,
        serverCounts: [Int64: Int],
        cap: Int = 100
    ) async -> [Int64: Int] {
        var result: [Int64: Int] = [:]

        for (tripID, serverCount) in serverCounts where serverCount > 0 {
            do {
                result[tripID] = try await unreadTalkCount(
                    tripID: tripID,
                    memberID: memberID,
                    cap: cap
                )
            } catch {
                // 상세 조회에 실패한 경우 실제 일반 채팅을 누락시키지 않도록 서버 집계로 대체합니다.
                result[tripID] = serverCount
            }
        }

        return result
    }

    private func unreadTalkCount(
        tripID: Int64,
        memberID: Int64,
        cap: Int
    ) async throws -> Int {
        let statuses = try await readStatuses(tripID: tripID)
        var cursor = statuses.statuses
            .first(where: { $0.memberId == memberID })?
            .lastReadMessageId ?? 0
        var talkCount = 0
        var hasNext = true

        while hasNext, talkCount < cap {
            let page = try await messagesAfter(tripID: tripID, cursor: cursor)
            guard let latestID = page.messages.map(\.id).max() else { break }

            talkCount += page.messages.lazy.filter { !$0.isSystem }.count
            cursor = latestID
            hasNext = page.hasNext
        }

        return min(talkCount, cap)
    }

    func markRead(tripID: Int64, messageID: Int64) async {
        guard let body = try? encoder.encode(ChatReadRequest(lastReadMessageId: messageID)) else { return }
        _ = try? await apiClient.sendRaw(
            path: "/api/v1/trips/\(tripID)/chat/read",
            method: "PUT",
            body: body,
            contentType: "application/json",
            authenticated: true
        )
    }
}

@MainActor
final class ChatUnreadStore: ObservableObject {
    static let shared = ChatUnreadStore()

    @Published private(set) var counts: [Int64: Int] = [:]

    private let service = ChatService.shared
    private var isRefreshing = false
    private var memberID: Int64?

    private init() {}

    func count(for tripID: Int64) -> Int {
        counts[tripID] ?? 0
    }

    func configure(memberID: Int64?) {
        guard self.memberID != memberID else { return }
        self.memberID = memberID
        counts = [:]
    }

    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }

        guard let latestCounts = try? await service.unreadCounts() else { return }
        guard let memberID else {
            counts = latestCounts
            return
        }

        counts = await service.unreadTalkCounts(
            memberID: memberID,
            serverCounts: latestCounts
        )
    }

    func markRoomOpened(tripID: Int64) {
        counts[tripID] = 0
    }

    func recordIncomingMessage(tripID: Int64) {
        counts[tripID, default: 0] += 1
    }
}
