import Combine
import Foundation
import SwiftUI

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var trips: [TripSummary] = []
    @Published var keyword = ""
    @Published var startDate: Date?
    @Published var isLoading = false
    @Published var isLoadingMore = false
    @Published private(set) var resultAnimationVersion = 0
    @Published var errorMessage: String?
    @Published var isCreateTripPresented = false
    @Published var isJoinTripPresented = false

    private let tripService = TripService.shared
    private let pageSize = 10
    private var currentPage = 0
    private var hasNext = false

    func load() async {
        guard !isLoading, !isLoadingMore else { return }
        isLoading = true
        errorMessage = nil

        do {
            let result = try await tripService.trips(
                keyword: keyword.trimmingCharacters(in: .whitespacesAndNewlines),
                startDate: startDate.map(TripDateFormatter.api.string) ?? "",
                page: 0,
                size: pageSize
            )
            withAnimation(.easeOut(duration: 0.24)) {
                trips = result.items
                currentPage = result.page
                hasNext = result.hasNext
                isLoading = false
                resultAnimationVersion += 1
            }
        } catch {
            isLoading = false
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
    }

    func loadMore() async {
        guard hasNext,
              !isLoading,
              !isLoadingMore else { return }

        isLoadingMore = true
        defer { isLoadingMore = false }

        do {
            let result = try await tripService.trips(
                keyword: keyword.trimmingCharacters(in: .whitespacesAndNewlines),
                startDate: startDate.map(TripDateFormatter.api.string) ?? "",
                page: currentPage + 1,
                size: pageSize
            )
            let existingIDs = Set(trips.map(\.id))
            trips.append(contentsOf: result.items.filter { !existingIDs.contains($0.id) })
            currentPage = result.page
            hasNext = result.hasNext
        } catch {
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
    }

    func createTrip(name: String, region: String, startDate: Date, nights: Int) async -> Bool {
        do {
            let created = try await tripService.createTrip(
                name: name,
                region: region,
                startDate: startDate,
                nights: nights
            )
            trips.insert(created, at: 0)
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func join(code: String) async -> Bool {
        do {
            try await tripService.join(code: code.trimmingCharacters(in: .whitespacesAndNewlines))
            await load()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func deleteTrips(ids: [Int64]) async -> Bool {
        guard !ids.isEmpty else { return false }

        do {
            _ = try await tripService.deleteTrips(ids: ids)
            let deletedIDs = Set(ids)
            trips.removeAll { deletedIDs.contains($0.id) }
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}
