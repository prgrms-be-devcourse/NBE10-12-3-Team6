import Foundation

@MainActor
final class TripService {
    static let shared = TripService()

    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    private init() {
        self.apiClient = .shared
    }

    func trips(
        keyword: String = "",
        startDate: String = "",
        page: Int = 0,
        size: Int = 10
    ) async throws -> TripSummaryPage {
        var components = URLComponents()
        components.path = "/api/v1/trips"
        var items: [URLQueryItem] = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "size", value: String(size))
        ]
        if !keyword.isEmpty { items.append(URLQueryItem(name: "keyword", value: keyword)) }
        if !startDate.isEmpty { items.append(URLQueryItem(name: "startDate", value: startDate)) }
        components.queryItems = items

        let (response, _): (APIResponse<TripSummaryPage>, HTTPURLResponse) = try await apiClient.send(
            path: components.string ?? "/api/v1/trips",
            authenticated: true
        )
        return response.data
    }

    func createTrip(name: String, region: String, startDate: Date, nights: Int) async throws -> TripSummary {
        let request = CreateTripRequest(
            name: name,
            region: region,
            startDate: TripDateFormatter.api.string(from: startDate),
            nights: nights
        )
        let body = try encoder.encode(request)
        let (response, _): (APIResponse<TripSummary>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips",
            method: "POST",
            body: body,
            authenticated: true
        )
        return response.data
    }

    func detail(tripID: Int64) async throws -> TripDetail {
        let (response, _): (APIResponse<TripDetail>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)",
            authenticated: true
        )
        return response.data
    }

    func join(code: String) async throws {
        let result: (APIResponse<EmptyPayload?>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/member/\(code)",
            method: "POST",
            authenticated: true
        )
        _ = result
    }

    func updateTrip(tripID: Int64, name: String, region: String, startDate: Date, nights: Int) async throws {
        let request = UpdateTripRequest(
            name: name,
            region: region,
            startDate: TripDateFormatter.api.string(from: startDate),
            nights: nights
        )
        let body = try encoder.encode(request)
        let result: (APIResponse<TripSummary>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)",
            method: "PATCH",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func deleteTrips(ids: [Int64]) async throws -> Int {
        let body = try encoder.encode(BulkDeleteTripsRequest(ids: ids))
        let (response, _): (APIResponse<BulkDeleteTripsResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/bulk-delete",
            method: "POST",
            body: body,
            authenticated: true
        )
        return response.data.deletedCount
    }

    func pastMates(search: String = "", page: Int = 0) async throws -> PastMatesPage {
        var components = URLComponents()
        components.path = "/api/v1/trips/past-members"
        components.queryItems = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "size", value: "15"),
            URLQueryItem(name: "search", value: search.isEmpty ? nil : search)
        ]
        let (response, _): (APIResponse<PastMatesPage>, HTTPURLResponse) = try await apiClient.send(
            path: components.string ?? "/api/v1/trips/past-members",
            authenticated: true
        )
        return response.data
    }

    func invite(tripID: Int64, memberIDs: [Int64]) async throws {
        let body = try encoder.encode(InviteMembersRequest(memberIds: memberIDs))
        let result: (APIResponse<[TripMember]>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/members/invite",
            method: "POST",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func onlineStatus(memberIDs: [Int64]) async throws -> [Int64: Bool] {
        guard !memberIDs.isEmpty else { return [:] }
        var components = URLComponents()
        components.path = "/api/v1/presence/status"
        components.queryItems = [
            URLQueryItem(name: "userIds", value: memberIDs.map(String.init).joined(separator: ","))
        ]
        let (response, _): (APIResponse<[String: Bool]>, HTTPURLResponse) = try await apiClient.send(
            path: components.string ?? "/api/v1/presence/status",
            authenticated: true
        )
        return response.data.reduce(into: [:]) { result, entry in
            if let memberID = Int64(entry.key) {
                result[memberID] = entry.value
            }
        }
    }
}
