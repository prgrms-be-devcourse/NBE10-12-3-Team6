import Foundation

@MainActor
final class TimelineService {
    static let shared = TimelineService()

    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    private init() { apiClient = .shared }

    func timelines(tripID: Int64, dayNumber: Int) async throws -> [TimelineItem] {
        let (response, _): (APIResponse<[TimelineItem]>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/timelines?dayNumber=\(dayNumber)",
            authenticated: true
        )
        return response.data
    }

    func create(tripID: Int64, request: TimelineCreateRequest) async throws {
        let body = try encoder.encode(request)
        let result: (APIResponse<TimelineMutationResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/timelines",
            method: "POST",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func createAll(tripID: Int64, request: TimelineAllCreateRequest) async throws {
        let body = try encoder.encode(request)
        let result: (APIResponse<[TimelineMutationResponse]>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/timelines/batch",
            method: "POST",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func update(tripID: Int64, timelineID: Int64, request: TimelineUpdateRequest) async throws {
        let body = try encoder.encode(request)
        let result: (APIResponse<TimelineMutationResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/timelines/\(timelineID)",
            method: "PATCH",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func delete(tripID: Int64, timelineID: Int64) async throws {
        let result: (APIResponse<EmptyPayload?>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/timelines/\(timelineID)",
            method: "DELETE",
            authenticated: true
        )
        _ = result
    }

    func settings(tripID: Int64) async throws -> TripGroupSettings {
        let (response, _): (APIResponse<TripGroupSettings>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/settings",
            authenticated: true
        )
        return response.data
    }

    func updateFreeTime(tripID: Int64, dayNumber: Int, minutes: Int) async throws -> TripGroupSettings {
        let body = try encoder.encode(FreeTimeUpdateRequest(freeTimeMinutes: minutes))
        let (response, _): (APIResponse<TripGroupSettings>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/settings/free-time/\(dayNumber)",
            method: "PATCH",
            body: body,
            authenticated: true
        )
        return response.data
    }

    func updateAllFreeTime(tripID: Int64, minutes: Int) async throws -> TripGroupSettings {
        let body = try encoder.encode(FreeTimeUpdateRequest(freeTimeMinutes: minutes))
        let (response, _): (APIResponse<TripGroupSettings>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/settings/free-time",
            method: "PATCH",
            body: body,
            authenticated: true
        )
        return response.data
    }

    func updateAnonymousVote(tripID: Int64, isAnonymous: Bool) async throws -> TripGroupSettings {
        let body = try encoder.encode(AnonymousVoteUpdateRequest(isAnonymousVote: isAnonymous))
        let (response, _): (APIResponse<TripGroupSettings>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/settings",
            method: "PATCH",
            body: body,
            authenticated: true
        )
        return response.data
    }
}
