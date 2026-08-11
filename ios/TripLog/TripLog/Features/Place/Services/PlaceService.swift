import Foundation

@MainActor
final class PlaceService {
    static let shared = PlaceService()
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    private init() { apiClient = .shared }

    func search(query: String) async throws -> [KakaoPlaceDocument] {
        var components = URLComponents()
        components.path = "/api/places"
        components.queryItems = [URLQueryItem(name: "query", value: query)]

        guard let path = components.string else {
            throw APIError.invalidResponse
        }

        let (response, _): (KakaoPlaceSearchResponse, HTTPURLResponse) = try await apiClient.send(
            path: path,
            authenticated: false
        )
        return response.documents
    }

    func places(tripID: Int64) async throws -> [WishPlace] {
        let (response, _): (APIResponse<[WishPlace]>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/wish-places",
            authenticated: true
        )
        return response.data
    }

    func create(tripID: Int64, request: WishPlaceCreateRequest) async throws {
        let body = try encoder.encode(request)
        let result: (APIResponse<WishPlaceCreateResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/wish-places",
            method: "POST",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func delete(tripID: Int64, placeID: Int64) async throws {
        _ = try await apiClient.sendRaw(
            path: "/api/v1/trips/\(tripID)/wish-places/\(placeID)",
            method: "DELETE",
            authenticated: true
        )
    }
}
