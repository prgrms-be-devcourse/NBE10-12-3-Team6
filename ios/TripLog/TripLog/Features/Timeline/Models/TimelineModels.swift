import Foundation

struct TimelineItem: Codable, Identifiable, Hashable {
    let timelineId: Int64
    let dayNumber: Int
    let startTime: String
    let endTime: String
    let confirmedPlaceName: String?
    let category: String?
    let voteId: Int64?
    let isFreeTime: Bool

    var id: Int64 { timelineId }
    var startLabel: String { LocalDateTimeFormatter.time(from: startTime) }
    var endLabel: String { LocalDateTimeFormatter.time(from: endTime) }
}

struct TimelineMutationResponse: Codable {
    let timelineId: Int64
    let dayNumber: Int
    let startTime: String
    let endTime: String
    let isFreeTime: Bool
}

struct TimelineCreateRequest: Encodable {
    let dayNumber: Int
    let startTime: String
    let endTime: String
}

struct TimelineAllCreateRequest: Encodable {
    let dayNumber: Int
    let timelines: [TimelineCreateRequest]
}

struct TimelineUpdateRequest: Encodable {
    let startTime: String
    let endTime: String
}

struct TripGroupSettings: Codable {
    let tripGroupId: Int64
    let isAnonymousVote: Bool
    let days: [TripDayFreeTimeSetting]
    let editable: Bool
}

struct TripDayFreeTimeSetting: Codable, Identifiable {
    let dayNumber: Int
    let freeTimeMinutes: Int
    var id: Int { dayNumber }
}

struct FreeTimeUpdateRequest: Encodable {
    let freeTimeMinutes: Int
}

struct AnonymousVoteUpdateRequest: Encodable { let isAnonymousVote: Bool }

enum LocalDateTimeFormatter {
    private static let output: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return formatter
    }()

    static func string(date: Date, time: Date) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        let day = calendar.dateComponents([.year, .month, .day], from: date)
        let clock = calendar.dateComponents([.hour, .minute], from: time)
        let components = DateComponents(
            year: day.year,
            month: day.month,
            day: day.day,
            hour: clock.hour,
            minute: clock.minute,
            second: 0
        )
        return output.string(from: calendar.date(from: components) ?? date)
    }

    static func time(from value: String) -> String {
        let time = value.split(separator: "T").last.map(String.init) ?? value
        return String(time.prefix(5))
    }

    static func date(from value: String) -> Date? {
        let clean = String(value.prefix(19))
        return output.date(from: clean)
    }
}
