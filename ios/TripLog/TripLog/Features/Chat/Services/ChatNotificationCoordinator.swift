import Combine
import UIKit
import UserNotifications

extension Notification.Name {
    static let openTripChatFromNotification = Notification.Name("openTripChatFromNotification")
}

enum ChatNotificationNavigation {
    static let tripGroupIDKey = "tripGroupId"
}

@MainActor
final class ChatNotificationCoordinator: ObservableObject {
    private let tripService = TripService.shared
    private let systemNotificationCenter = UNUserNotificationCenter.current()
    private var clients: [Int64: STOMPChatClient] = [:]
    private var tripNames: [Int64: String] = [:]
    private var memberID: Int64?
    private var openChatTripID: Int64?
    private var isRefreshing = false

    func start(memberID: Int64?) async {
        guard let memberID else {
            stop()
            return
        }

        if self.memberID != memberID {
            stop()
            self.memberID = memberID
        }

        guard await requestNotificationAuthorization() else { return }
        await refreshRooms()
    }

    func refreshRooms() async {
        guard memberID != nil, !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }

        do {
            let trips = try await loadAllTrips()
            synchronizeClients(with: trips)
        } catch {
            // 채팅 팝업 연결 실패가 앱의 일반 사용을 방해하지 않도록 조용히 무시합니다.
        }
    }

    func setChatRoomOpen(_ tripID: Int64, isOpen: Bool) {
        if isOpen {
            openChatTripID = tripID
            clearNotifications(for: tripID)
        } else if openChatTripID == tripID {
            openChatTripID = nil
        }
    }

    func stop() {
        clients.values.forEach { $0.disconnect() }
        clients.removeAll()
        tripNames.removeAll()
        memberID = nil
        openChatTripID = nil
    }

    private func requestNotificationAuthorization() async -> Bool {
        let settings = await systemNotificationCenter.notificationSettings()

        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            return (try? await systemNotificationCenter.requestAuthorization(options: [.alert, .sound])) == true
        case .denied:
            return false
        @unknown default:
            return false
        }
    }

    private func loadAllTrips() async throws -> [TripSummary] {
        var trips: [TripSummary] = []
        var page = 0
        var hasNext = true

        while hasNext {
            let result = try await tripService.trips(page: page, size: 100)
            trips.append(contentsOf: result.items)
            hasNext = result.hasNext
            page += 1
        }

        return trips
    }

    private func synchronizeClients(with trips: [TripSummary]) {
        let roomIDs = Set(trips.map(\.id))
        tripNames = Dictionary(uniqueKeysWithValues: trips.map { ($0.id, $0.name) })

        let removedRoomIDs = clients.keys.filter { !roomIDs.contains($0) }
        for tripID in removedRoomIDs {
            clients.removeValue(forKey: tripID)?.disconnect()
        }

        for trip in trips where clients[trip.id] == nil {
            let client = STOMPChatClient()
            client.onMessage = { [weak self] message in
                self?.handle(message)
            }
            client.connect(tripID: trip.id)
            clients[trip.id] = client
        }
    }

    private func handle(_ message: ChatMessage) {
        guard UIApplication.shared.applicationState == .active,
              !message.isSystem,
              let senderID = message.senderId,
              senderID != memberID,
              openChatTripID != message.tripGroupId else { return }

        ChatUnreadStore.shared.recordIncomingMessage(tripID: message.tripGroupId)

        let content = UNMutableNotificationContent()
        content.title = tripNames[message.tripGroupId] ?? "TripLog 채팅"
        content.subtitle = message.senderName ?? "새 메시지"
        content.body = message.content
        content.sound = .default
        content.threadIdentifier = "triplog-chat-\(message.tripGroupId)"
        content.userInfo = [ChatNotificationNavigation.tripGroupIDKey: message.tripGroupId]

        let request = UNNotificationRequest(
            identifier: "triplog-chat-\(message.tripGroupId)-\(message.id)",
            content: content,
            trigger: nil
        )

        Task { [systemNotificationCenter] in
            try? await systemNotificationCenter.add(request)
        }
    }

    private func clearNotifications(for tripID: Int64) {
        ChatUnreadStore.shared.markRoomOpened(tripID: tripID)

        Task { [systemNotificationCenter] in
            let delivered = await systemNotificationCenter.deliveredNotifications()
            let deliveredIDs = delivered.compactMap { notification in
                notificationTripID(notification.request.content.userInfo) == tripID
                    ? notification.request.identifier
                    : nil
            }
            systemNotificationCenter.removeDeliveredNotifications(withIdentifiers: deliveredIDs)

            let pending = await systemNotificationCenter.pendingNotificationRequests()
            let pendingIDs = pending.compactMap { request in
                notificationTripID(request.content.userInfo) == tripID
                    ? request.identifier
                    : nil
            }
            systemNotificationCenter.removePendingNotificationRequests(withIdentifiers: pendingIDs)
        }
    }

    private func notificationTripID(_ userInfo: [AnyHashable: Any]) -> Int64? {
        let value = userInfo[ChatNotificationNavigation.tripGroupIDKey]

        if let number = value as? NSNumber {
            return number.int64Value
        }
        if let integer = value as? Int64 {
            return integer
        }
        if let integer = value as? Int {
            return Int64(integer)
        }
        if let text = value as? String {
            return Int64(text)
        }
        return nil
    }
}
