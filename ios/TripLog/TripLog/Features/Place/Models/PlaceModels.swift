import Foundation

struct WishPlace: Codable, Identifiable, Hashable {
    let tripPlaceId: Int64
    let name: String
    let address: String
    let category: String
    let createdBy: String
    let createdByMemberId: Int64

    var id: Int64 { tripPlaceId }
}

struct WishPlaceCreateRequest: Encodable {
    let name: String
    let category: String
    let address: String
    let kakaoPlaceId: String
    let kakaoMapUrl: String
}

struct WishPlaceCreateResponse: Codable {
    let id: Int64
    let name: String
    let category: String
    let address: String
}

struct KakaoPlaceSearchResponse: Decodable {
    let documents: [KakaoPlaceDocument]
}

struct KakaoPlaceDocument: Decodable {
    let id: String
    let placeName: String
    let addressName: String
    let roadAddressName: String
    let categoryGroupName: String
    let placeURL: String

    enum CodingKeys: String, CodingKey {
        case id
        case placeName = "place_name"
        case addressName = "address_name"
        case roadAddressName = "road_address_name"
        case categoryGroupName = "category_group_name"
        case placeURL = "place_url"
    }

    var displayAddress: String {
        roadAddressName.isEmpty ? addressName : roadAddressName
    }
}
