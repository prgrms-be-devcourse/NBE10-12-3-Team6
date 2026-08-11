import Combine
import Foundation

@MainActor
final class PlaceCandidatesViewModel: ObservableObject {
    @Published var places: [WishPlace] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isAddPresented = false

    let trip: TripDetail
    private let service = PlaceService.shared

    init(trip: TripDetail) { self.trip = trip }

    func load() async {
        isLoading = true
        errorMessage = nil
        do { places = try await service.places(tripID: trip.id) }
        catch {
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
        isLoading = false
    }

    func create(_ result: PlaceSearchResult) async -> Bool {
        do {
            try await service.create(
                tripID: trip.id,
                request: WishPlaceCreateRequest(
                    name: result.name,
                    category: result.category,
                    address: result.address,
                    kakaoPlaceId: result.externalID,
                    kakaoMapUrl: result.mapURL
                )
            )
            await load()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func delete(_ place: WishPlace) async {
        do {
            try await service.delete(tripID: trip.id, placeID: place.id)
            places.removeAll { $0.id == place.id }
        } catch { errorMessage = error.localizedDescription }
    }
}
