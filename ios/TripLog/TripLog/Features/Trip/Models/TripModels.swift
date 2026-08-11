import Foundation

struct TripSummary: Codable, Identifiable, Hashable {
    let id: Int64
    let name: String
    let ownerId: Int64
    let region: String
    let joinCode: String
    let nights: Int
    let startDate: String
    let endDate: String
}

struct TripMember: Codable, Identifiable, Hashable {
    let memberId: Int64
    let name: String
    let admin: Bool

    var id: Int64 { memberId }
}

struct TripDetail: Codable, Identifiable, Hashable {
    let id: Int64
    let name: String
    let ownerId: Int64
    let region: String
    let joinCode: String
    let nights: Int
    let startDate: String
    let endDate: String
    let members: [TripMember]

    var dayCount: Int { nights + 1 }
}

struct CreateTripRequest: Encodable {
    let name: String
    let region: String
    let startDate: String
    let nights: Int
}

struct UpdateTripRequest: Encodable {
    let name: String
    let region: String
    let startDate: String
    let nights: Int
}

struct PastMate: Codable, Identifiable, Hashable {
    let id: Int64
    let name: String
    let travelCount: Int
    let latestTravelDate: String
    let latestGroupName: String?
}

struct PastMatesPage: Codable {
    let items: [PastMate]
    let hasNext: Bool
    let page: Int
    let size: Int
}

struct TripSummaryPage: Codable {
    let items: [TripSummary]
    let hasNext: Bool
    let page: Int
    let size: Int
}

struct InviteMembersRequest: Encodable { let memberIds: [Int64] }

struct BulkDeleteTripsRequest: Encodable {
    let ids: [Int64]
}

struct BulkDeleteTripsResponse: Decodable {
    let deletedCount: Int
}

enum TripStatus {
    case before
    case during
    case after

    var title: String {
        switch self {
        case .before: "여행 전"
        case .during: "여행 중"
        case .after: "여행 완료"
        }
    }
}

enum TripDateFormatter {
    static let api: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    static let display: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "M월 d일"
        return formatter
    }()

    static let filterDisplay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy년 M월 d일"
        return formatter
    }()

    static func date(from string: String) -> Date? {
        api.date(from: string)
    }

    static func status(startDate: String, nights: Int) -> TripStatus {
        guard let start = date(from: startDate) else { return .before }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let normalizedStart = calendar.startOfDay(for: start)
        let end = calendar.date(byAdding: .day, value: nights, to: normalizedStart) ?? normalizedStart

        if today < normalizedStart { return .before }
        if today <= end { return .during }
        return .after
    }

    static func dayDate(startDate: String, dayNumber: Int) -> Date {
        let start = date(from: startDate) ?? Date()
        return Calendar.current.date(byAdding: .day, value: dayNumber - 1, to: start) ?? start
    }
}
