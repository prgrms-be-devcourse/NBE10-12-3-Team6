import Combine
import Foundation

@MainActor
final class TripDetailViewModel: ObservableObject {
    @Published var trip: TripDetail?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let tripID: Int64

    init(tripID: Int64) {
        self.tripID = tripID
    }

    func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            trip = try await TripService.shared.detail(tripID: tripID)
        } catch {
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
    }
}
