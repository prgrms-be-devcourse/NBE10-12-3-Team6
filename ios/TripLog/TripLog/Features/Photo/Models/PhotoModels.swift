import Foundation

struct PhotoCursorResponse: Codable {
    let groups: [PhotoDayGroup]
    let nextCursor: String?
    let hasNext: Bool
}

struct PhotoDayGroup: Codable, Identifiable {
    let date: String
    var posts: [PhotoPost]
    var id: String { date }
}

struct PhotoPost: Codable, Identifiable, Hashable {
    let postId: Int64?
    let originalFilename: String?
    let contentUrl: String?
    let normalContentUrl: String?
    let dataSaverContentUrl: String?
    let dominantColor: String?
    let timelineId: Int64?
    let startTime: String?
    let endTime: String?
    let confirmedPlaceName: String?
    let createdAt: String?
    var likeCount: Int
    let authorMemberId: Int64?
    var content: String?

    var id: Int64 { postId ?? 0 }
    var timeRange: String {
        guard let startTime, let endTime else { return "자유로운 한 컷" }
        return "\(LocalDateTimeFormatter.time(from: startTime)) ~ \(LocalDateTimeFormatter.time(from: endTime))"
    }

    func previewURL(preference: String) -> URL? {
        let original = contentUrl ?? normalContentUrl ?? dataSaverContentUrl
        let normal = normalContentUrl ?? original
        let saver = dataSaverContentUrl ?? normal
        return PhotoURLResolver.url(from: preference == "quality" ? normal : saver)
    }

    func detailURL(preference: String) -> URL? {
        let original = contentUrl ?? normalContentUrl ?? dataSaverContentUrl
        let normal = normalContentUrl ?? original
        let saver = dataSaverContentUrl ?? normal
        return PhotoURLResolver.url(from: preference == "saver" ? saver : original)
    }
}

struct CurrentPhotoSlot: Codable {
    let startTime: String
    let endTime: String
    let timelineId: Int64?
    let confirmedPlaceName: String?
    let isTaken: Bool
}

struct PhotoLikeStatus: Codable {
    let postId: Int64
    let liked: Bool
    let likeCount: Int
}

struct PhotoCreatedResponse: Codable {
    let id: Int64?
    let timelineId: Int64?
    let type: String?
    let originalFilename: String?
    let contentUrl: String?
    let normalContentUrl: String?
    let dataSaverContentUrl: String?
    let dominantColor: String?
    let content: String?
    let likeCount: Int
}

struct PhotoContentUpdateRequest: Encodable { let content: String? }

enum PhotoURLResolver {
    static func url(from value: String?) -> URL? {
        guard let value, !value.isEmpty else { return nil }
        if value.hasPrefix("http") { return URL(string: value) }
        return URL(string: value, relativeTo: AppConfiguration.apiBaseURL)?.absoluteURL
    }
}
