import Combine
import Foundation

struct PhotoDayPaginationState {
    var nextCursor: String? = nil
    var hasNext = false
    var isLoading = false
    var isLoaded = false
    var errorMessage: String?
}

@MainActor
final class PhotoTimelineViewModel: ObservableObject {
    @Published var groups: [PhotoDayGroup] = []
    @Published var errorMessage: String?
    @Published var dayStates: [Int: PhotoDayPaginationState] = [:]
    @Published var likeStatuses: [Int64: PhotoLikeStatus] = [:]

    let trip: TripDetail
    let memberID: Int64?
    private let service = PhotoService.shared

    init(trip: TripDetail, memberID: Int64?) {
        self.trip = trip
        self.memberID = memberID
    }

    func loadDayIfNeeded(_ dayNumber: Int) async {
        guard dayStates[dayNumber]?.isLoaded != true else { return }
        await loadDay(dayNumber, reset: true)
    }

    func loadNextPage(for dayNumber: Int) async {
        guard dayStates[dayNumber]?.hasNext == true else { return }
        await loadDay(dayNumber, reset: false)
    }

    func retryDay(_ dayNumber: Int) async {
        await loadDay(dayNumber, reset: dayStates[dayNumber]?.isLoaded != true)
    }

    func reloadLoadedDays() async {
        let loadedDays = dayStates
            .filter { $0.value.isLoaded }
            .map(\.key)
            .sorted()
        for dayNumber in loadedDays.isEmpty ? [1] : loadedDays {
            await loadDay(dayNumber, reset: true)
        }
    }

    func reloadAfterUpload(dayNumber: Int) async {
        await loadDay(dayNumber, reset: true)
    }

    private func loadDay(_ dayNumber: Int, reset: Bool) async {
        var state = dayStates[dayNumber] ?? PhotoDayPaginationState()
        guard !state.isLoading else { return }

        state.isLoading = true
        state.errorMessage = nil
        if reset {
            state.nextCursor = nil
            state.hasNext = false
        }
        dayStates[dayNumber] = state

        do {
            let response = try await service.posts(
                tripID: trip.id,
                dayNumber: dayNumber,
                cursor: reset ? nil : state.nextCursor,
                size: 5
            )
            if reset {
                replaceDay(dayNumber, with: response.groups)
            } else {
                merge(response.groups)
            }
            state.nextCursor = response.nextCursor
            state.hasNext = response.hasNext
            state.isLoaded = true
            await loadLikeStatuses(for: response.groups)
        } catch {
            if !error.isRequestCancellation {
                state.errorMessage = error.localizedDescription
            }
        }
        state.isLoading = false
        dayStates[dayNumber] = state
    }

    func toggleLike(_ post: PhotoPost) async {
        guard let postID = post.postId else { return }
        do {
            let current: PhotoLikeStatus?
            if let cached = likeStatuses[postID] {
                current = cached
            } else {
                current = try? await service.likeStatus(tripID: trip.id, postID: postID)
            }
            let result = try await service.setLiked(tripID: trip.id, postID: postID, liked: current?.liked ?? false)
            likeStatuses[postID] = result
            updatePost(postID: postID) { $0.likeCount = result.likeCount }
        } catch { errorMessage = error.localizedDescription }
    }

    func update(_ post: PhotoPost, content: String) async -> Bool {
        guard let postID = post.postId else { return false }
        let trimmed = String(content.trimmingCharacters(in: .whitespacesAndNewlines).prefix(20))
        do {
            try await service.update(tripID: trip.id, postID: postID, content: trimmed.isEmpty ? nil : trimmed)
            updatePost(postID: postID) { $0.content = trimmed }
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func delete(_ post: PhotoPost) async {
        guard let postID = post.postId else { return }
        do {
            try await service.delete(tripID: trip.id, postID: postID)
            for index in groups.indices { groups[index].posts.removeAll { $0.id == postID } }
            groups.removeAll { $0.posts.isEmpty }
        } catch { errorMessage = error.localizedDescription }
    }

    private func loadLikeStatuses(for groups: [PhotoDayGroup]) async {
        let postIDs = Set(
            groups
                .flatMap(\.posts)
                .compactMap(\.postId)
                .filter { likeStatuses[$0] == nil }
        )

        await withTaskGroup(of: PhotoLikeStatus?.self) { group in
            for postID in postIDs {
                group.addTask { @MainActor [service, tripID = trip.id] in
                    try? await service.likeStatus(tripID: tripID, postID: postID)
                }
            }

            for await status in group {
                guard let status else { continue }
                likeStatuses[status.postId] = status
            }
        }
    }

    private func merge(_ incoming: [PhotoDayGroup]) {
        for newGroup in incoming {
            if let index = groups.firstIndex(where: { $0.date == newGroup.date }) {
                let existingIDs = Set(groups[index].posts.map(\.id))
                groups[index].posts.append(contentsOf: newGroup.posts.filter { !existingIDs.contains($0.id) })
            } else {
                groups.append(newGroup)
            }
        }
        groups.sort { $0.date < $1.date }
    }

    private func replaceDay(_ dayNumber: Int, with incoming: [PhotoDayGroup]) {
        let date = TripDateFormatter.dayDate(
            startDate: trip.startDate,
            dayNumber: dayNumber
        )
        let dateText = TripDateFormatter.api.string(from: date)
        groups.removeAll { $0.date == dateText }
        merge(incoming)
    }

    private func updatePost(postID: Int64, mutation: (inout PhotoPost) -> Void) {
        for groupIndex in groups.indices {
            guard let postIndex = groups[groupIndex].posts.firstIndex(where: { $0.id == postID }) else { continue }
            mutation(&groups[groupIndex].posts[postIndex])
            return
        }
    }
}
