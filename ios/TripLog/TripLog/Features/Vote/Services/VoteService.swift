import Foundation

@MainActor
final class VoteService {
    static let shared = VoteService()
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    private init() { apiClient = .shared }

    func votes(tripID: Int64) async throws -> [VoteDayGroup] {
        let (response, _): (APIResponse<[VoteDayGroup]>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/votes",
            authenticated: true
        )
        return response.data
    }

    func create(tripID: Int64, timelineID: Int64) async throws -> Int64? {
        let body = try encoder.encode(VoteCreateRequest(timelineId: timelineID))
        let (response, _): (APIResponse<VoteCreateResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/votes",
            method: "POST",
            body: body,
            authenticated: true
        )
        return response.data.voteId
    }

    func detail(tripID: Int64, voteID: Int64) async throws -> VoteDetail {
        let (response, _): (APIResponse<VoteDetail>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/votes/\(voteID)/count",
            authenticated: true
        )
        return response.data
    }

    func participate(tripID: Int64, voteID: Int64, placeID: Int64) async throws {
        let body = try encoder.encode(VoteParticipateRequest(tripPlaceId: placeID))
        let result: (APIResponse<VoteParticipateResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/votes/\(voteID)",
            method: "POST",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func confirm(tripID: Int64, voteID: Int64) async throws -> VoteConfirmResponse {
        let (response, _): (APIResponse<VoteConfirmResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/votes/\(voteID)/confirm",
            method: "PATCH",
            authenticated: true
        )
        return response.data
    }

    func updateAnonymous(tripID: Int64, voteID: Int64, isAnonymous: Bool) async throws {
        let body = try encoder.encode(VoteAnonymousRequest(isAnonymous: isAnonymous))
        let result: (APIResponse<Bool>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/trips/\(tripID)/votes/\(voteID)/anonymous",
            method: "PATCH",
            body: body,
            authenticated: true
        )
        _ = result
    }
}
