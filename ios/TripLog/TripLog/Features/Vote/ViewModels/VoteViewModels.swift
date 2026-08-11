import Combine
import Foundation

@MainActor
final class VoteListViewModel: ObservableObject {
    @Published var groups: [VoteDayGroup] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isAnonymousVoteDefault = true
    @Published var isUpdatingAnonymous = false
    let trip: TripDetail
    let memberID: Int64?
    private let service = VoteService.shared
    private let timelineService = TimelineService.shared

    init(trip: TripDetail, memberID: Int64?) {
        self.trip = trip
        self.memberID = memberID
    }

    var isAdmin: Bool { memberID == trip.ownerId }

    func load() async {
        isLoading = true
        errorMessage = nil
        do {
            groups = try await service.votes(tripID: trip.id)
            if isAdmin, let settings = try? await timelineService.settings(tripID: trip.id) {
                isAnonymousVoteDefault = settings.isAnonymousVote
            }
        }
        catch {
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
        isLoading = false
    }

    func updateAnonymousVoteDefault(_ value: Bool) async {
        guard isAdmin, !isUpdatingAnonymous else { return }
        let previous = isAnonymousVoteDefault
        isAnonymousVoteDefault = value
        isUpdatingAnonymous = true
        defer { isUpdatingAnonymous = false }

        do {
            let settings = try await timelineService.updateAnonymousVote(tripID: trip.id, isAnonymous: value)
            isAnonymousVoteDefault = settings.isAnonymousVote
        } catch {
            isAnonymousVoteDefault = previous
            errorMessage = error.localizedDescription
        }
    }

    func createVote(for item: VoteTimeline) async {
        guard let timelineID = item.timelineId else { return }
        do {
            _ = try await service.create(tripID: trip.id, timelineID: timelineID)
            await load()
        } catch { errorMessage = error.localizedDescription }
    }
}

@MainActor
final class VoteDetailViewModel: ObservableObject {
    @Published var detail: VoteDetail?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var toastMessage: String?

    let tripID: Int64
    let voteID: Int64
    let isAdmin: Bool
    private let service = VoteService.shared

    init(tripID: Int64, voteID: Int64, isAdmin: Bool) {
        self.tripID = tripID
        self.voteID = voteID
        self.isAdmin = isAdmin
    }

    func load() async {
        isLoading = true
        errorMessage = nil
        do { detail = try await service.detail(tripID: tripID, voteID: voteID) }
        catch {
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
        isLoading = false
    }

    func vote(placeID: Int64) async {
        do {
            try await service.participate(tripID: tripID, voteID: voteID, placeID: placeID)
            toastMessage = "투표 반영"
            await load()
        } catch { errorMessage = error.localizedDescription }
    }

    func confirm() async {
        do {
            let response = try await service.confirm(tripID: tripID, voteID: voteID)
            toastMessage = response.isTie ? "동률 후보 중 무작위 확정" : "투표 결과 확정"
            await load()
        } catch { errorMessage = error.localizedDescription }
    }

    func setAnonymous(_ value: Bool) async {
        do {
            try await service.updateAnonymous(tripID: tripID, voteID: voteID, isAnonymous: value)
            await load()
        } catch { errorMessage = error.localizedDescription }
    }
}
