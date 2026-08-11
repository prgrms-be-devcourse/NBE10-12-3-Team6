import Foundation

@MainActor
final class PhotoService {
    static let shared = PhotoService()
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    private init() { apiClient = .shared }

    func posts(
        tripID: Int64,
        dayNumber: Int,
        cursor: String?,
        size: Int = 5
    ) async throws -> PhotoCursorResponse {
        var components = URLComponents()
        components.path = "/api/v1/trips/\(tripID)/posts"
        var items = [
            URLQueryItem(name: "dayNumber", value: String(dayNumber)),
            URLQueryItem(name: "size", value: String(size))
        ]
        if let cursor { items.append(URLQueryItem(name: "cursor", value: cursor)) }
        components.queryItems = items
        let (response, _): (PhotoCursorResponse, HTTPURLResponse) = try await apiClient.sendDirect(
            path: components.string
                ?? "/api/v1/trips/\(tripID)/posts?dayNumber=\(dayNumber)&size=5",
            authenticated: true
        )
        return response
    }

    func currentSlot(tripID: Int64, dayNumber: Int) async throws -> CurrentPhotoSlot {
        let (response, _): (APIResponse<CurrentPhotoSlot>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/posts/is-taken?dayNumber=\(dayNumber)",
            authenticated: true
        )
        return response.data
    }

    func upload(
        tripID: Int64,
        timelineID: Int64?,
        content: String?,
        imageData: Data,
        filename: String,
        mimeType: String,
        onProgress: @escaping @MainActor @Sendable (Double) -> Void
    ) async throws {
        let boundary = "TripLogBoundary-\(UUID().uuidString)"
        var body = Data()
        let requestData = try encoder.encode(PhotoUploadMetadata(timelineId: timelineID, content: content))

        body.appendMultipart("--\(boundary)\r\n")
        body.appendMultipart("Content-Disposition: form-data; name=\"request\"\r\n")
        body.appendMultipart("Content-Type: application/json\r\n\r\n")
        body.append(requestData)
        body.appendMultipart("\r\n")
        body.appendMultipart("--\(boundary)\r\n")
        body.appendMultipart("Content-Disposition: form-data; name=\"image\"; filename=\"\(filename)\"\r\n")
        body.appendMultipart("Content-Type: \(mimeType)\r\n\r\n")
        body.append(imageData)
        body.appendMultipart("\r\n--\(boundary)--\r\n")

        let result: (PhotoCreatedResponse, HTTPURLResponse) = try await apiClient.sendDirectWithUploadProgress(
            path: "/api/v1/trips/\(tripID)/posts",
            method: "POST",
            body: body,
            contentType: "multipart/form-data; boundary=\(boundary)",
            authenticated: true,
            onProgress: onProgress
        )
        _ = result
    }

    func update(tripID: Int64, postID: Int64, content: String?) async throws {
        let body = try encoder.encode(PhotoContentUpdateRequest(content: content))
        _ = try await apiClient.sendRaw(
            path: "/api/v1/trips/\(tripID)/posts/\(postID)",
            method: "PUT",
            body: body,
            contentType: "application/json",
            authenticated: true
        )
    }

    func delete(tripID: Int64, postID: Int64) async throws {
        _ = try await apiClient.sendRaw(
            path: "/api/v1/trips/\(tripID)/posts/\(postID)",
            method: "DELETE",
            authenticated: true
        )
    }

    func likeStatus(tripID: Int64, postID: Int64) async throws -> PhotoLikeStatus {
        let (response, _): (APIResponse<PhotoLikeStatus>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/posts/\(postID)/likes",
            authenticated: true
        )
        return response.data
    }

    func setLiked(tripID: Int64, postID: Int64, liked: Bool) async throws -> PhotoLikeStatus {
        let (response, _): (APIResponse<PhotoLikeStatus>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/posts/\(postID)/likes",
            method: liked ? "DELETE" : "POST",
            authenticated: true
        )
        return response.data
    }
}

private struct PhotoUploadMetadata: Encodable {
    let timelineId: Int64?
    let content: String?
}

private extension Data {
    mutating func appendMultipart(_ value: String) {
        if let data = value.data(using: .utf8) { append(data) }
    }
}
