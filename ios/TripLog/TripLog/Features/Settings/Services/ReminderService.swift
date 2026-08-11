import Foundation
import UserNotifications

@MainActor
enum ReminderService {
    private static let identifier = "triplog.daily-reminder"

    static func setEnabled(_ enabled: Bool) async throws {
        let center = UNUserNotificationCenter.current()
        if enabled {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            guard granted else { throw ReminderError.permissionDenied }
            let content = UNMutableNotificationContent()
            content.title = "TripLog 여행 기록"
            content.body = "오늘의 여행 계획과 사진 기록을 확인해보세요."
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: DateComponents(hour: 9, minute: 0),
                repeats: true
            )
            try await center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
        } else {
            center.removePendingNotificationRequests(withIdentifiers: [identifier])
        }
    }
}

private enum ReminderError: LocalizedError {
    case permissionDenied
    var errorDescription: String? { "알림 권한이 꺼져 있습니다. iOS 설정에서 권한을 허용해주세요." }
}
