import Foundation

struct VoteDayGroup: Codable, Identifiable {
    let date: String
    let timeLines: [VoteTimeline]
    var id: String { date }
}

struct VoteTimeline: Codable, Identifiable, Hashable {
    let voteId: Int64?
    let timelineId: Int64?
    let startTime: String
    let confirmedPlaceName: String
    let voteStatus: String
    let isAnonymous: Bool

    var id: String { "\(timelineId ?? 0)-\(startTime)" }
    var timeLabel: String { LocalDateTimeFormatter.time(from: startTime) }
}

struct VoteDetail: Codable {
    let voteResults: [VoteResult]
    let wishPlaceFindResponses: [WishPlace]
    let updateCount: Int
    let voteStatus: String
    let confirmedPlaceId: Int64?
    let isAnonymous: Bool
}

struct VoteResult: Codable, Identifiable {
    let tripPlaceId: Int64?
    let place: String
    let count: Int
    let isVoted: Bool
    let voters: [VoteVoter]
    var id: String { "\(tripPlaceId ?? -1)-\(place)" }
}

struct VoteVoter: Codable, Identifiable {
    let tripMemberId: Int64
    let name: String
    var id: Int64 { tripMemberId }
}

struct VoteCreateRequest: Encodable { let timelineId: Int64 }
struct VoteCreateResponse: Codable { let voteId: Int64? }
struct VoteParticipateRequest: Encodable { let tripPlaceId: Int64 }
struct VoteParticipateResponse: Codable {
    let memberName: String
    let place: String
    let updateCount: Int
}
struct VoteAnonymousRequest: Encodable { let isAnonymous: Bool }
struct VoteConfirmResponse: Codable {
    let voteStatus: String
    let confirmedPlaceId: Int64
    let isTie: Bool
}
