import Combine
import Foundation

@MainActor
final class ChatViewModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var text = ""
    @Published var hasOlder = false
    @Published var isLoading = false
    @Published var isConnected = false
    @Published var errorMessage: String?
    @Published private(set) var pendingContent: String?
    @Published private(set) var sendFailed = false
    @Published private(set) var sendStatusMessage: String?
    @Published private(set) var readStatuses: [Int64: Int64] = [:]
    @Published private(set) var totalMemberCount = 0

    let trip: TripDetail
    let memberID: Int64?
    private let service = ChatService.shared
    private let socket = STOMPChatClient()
    private var sendFailureTask: Task<Void, Never>?

    init(trip: TripDetail, memberID: Int64?) {
        self.trip = trip
        self.memberID = memberID
        socket.onMessage = { [weak self] message in self?.append(message) }
        socket.onReadStatus = { [weak self] status in self?.updateReadStatus(status) }
        socket.onSendError = { [weak self] message in self?.markSendFailed(message) }
        socket.onConnectionChange = { [weak self] connected, message in
            self?.isConnected = connected
            if let message { self?.errorMessage = message }
            if connected {
                self?.errorMessage = nil
                Task { await self?.recoverMissedMessages() }
            }
        }
    }

    func start() async {
        await loadLatest()
        await loadReadStatuses()
        socket.connect(tripID: trip.id)
    }

    func stop() {
        sendFailureTask?.cancel()
        sendFailureTask = nil
        socket.disconnect()
    }

    func resumeConnection() {
        if isConnected {
            Task { await recoverMissedMessages() }
        } else {
            socket.reconnectImmediatelyIfNeeded()
        }
    }

    func loadLatest() async {
        isLoading = true
        do {
            let page = try await service.history(tripID: trip.id, cursor: nil)
            messages = page.messages.sorted { $0.id < $1.id }
            hasOlder = page.hasNext
            await markLatestRead()
        } catch { errorMessage = error.localizedDescription }
        isLoading = false
    }

    func loadOlder() async {
        guard hasOlder, let oldest = messages.first else { return }
        do {
            let page = try await service.history(tripID: trip.id, cursor: oldest.id)
            let known = Set(messages.map(\.id))
            messages.insert(contentsOf: page.messages.filter { !known.contains($0.id) }.sorted { $0.id < $1.id }, at: 0)
            hasOlder = page.hasNext
        } catch { errorMessage = error.localizedDescription }
    }

    func send() async {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, value.count <= 1000 else { return }
        pendingContent = value
        sendFailed = false
        sendStatusMessage = nil
        sendFailureTask?.cancel()
        do {
            try await socket.send(content: value)
            text = ""
            sendFailureTask = Task { [weak self] in
                try? await Task.sleep(for: .seconds(8))
                guard !Task.isCancelled, self?.pendingContent == value else { return }
                self?.markSendFailed("전송 실패 · 다시 시도해주세요.")
            }
        } catch {
            markSendFailed(error.localizedDescription)
        }
    }

    func unreadCount(for message: ChatMessage) -> Int {
        guard message.senderId == memberID,
              let memberID,
              totalMemberCount > 0 else { return 0 }

        let otherReaders = readStatuses.reduce(into: 0) { count, status in
            if status.key != memberID, status.value >= message.id {
                count += 1
            }
        }
        return max(totalMemberCount - 1 - otherReaders, 0)
    }

    private func append(_ message: ChatMessage) {
        guard !messages.contains(where: { $0.id == message.id }) else { return }
        messages.append(message)
        messages.sort { $0.id < $1.id }
        if message.senderId == memberID, message.content == pendingContent {
            sendFailureTask?.cancel()
            sendFailureTask = nil
            pendingContent = nil
            sendFailed = false
            sendStatusMessage = nil
        }
        Task { await service.markRead(tripID: trip.id, messageID: message.id) }
    }

    private func markSendFailed(_ message: String) {
        sendFailureTask?.cancel()
        sendFailureTask = nil
        sendFailed = true
        sendStatusMessage = message
    }

    private func recoverMissedMessages() async {
        guard let latest = messages.last else { return }
        do {
            let page = try await service.messagesAfter(tripID: trip.id, cursor: latest.id)
            page.messages.forEach(append)
        } catch { errorMessage = error.localizedDescription }
    }

    private func loadReadStatuses() async {
        do {
            let page = try await service.readStatuses(tripID: trip.id)
            totalMemberCount = page.totalMemberCount
            page.statuses.forEach(updateReadStatus)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func updateReadStatus(_ status: ChatReadStatus) {
        readStatuses[status.memberId] = max(
            readStatuses[status.memberId] ?? 0,
            status.lastReadMessageId
        )
    }

    private func markLatestRead() async {
        guard let latest = messages.last else { return }
        await service.markRead(tripID: trip.id, messageID: latest.id)
    }
}
