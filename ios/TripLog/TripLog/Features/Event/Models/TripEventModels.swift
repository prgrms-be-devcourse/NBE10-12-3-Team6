import Foundation

struct TripEventPayload: Codable, Identifiable, Hashable {
    let eventId: String
    let eventType: String
    let message: String
    let tripGroupId: Int64
    let actorMemberId: Int64?
    let dayNumber: Int?
    let timelineId: Int64?
    let timelineIds: [Int64]
    let voteId: Int64?
    let tripPlaceId: Int64?
    let occurredAt: String

    private enum CodingKeys: String, CodingKey {
        case eventId
        case eventType
        case message
        case tripGroupId
        case actorMemberId
        case dayNumber
        case timelineId
        case timelineIds
        case voteId
        case tripPlaceId
        case occurredAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        eventId = try container.decode(String.self, forKey: .eventId)
        eventType = try container.decode(String.self, forKey: .eventType)
        message = try container.decode(String.self, forKey: .message)
        tripGroupId = try container.decodeIfPresent(Int64.self, forKey: .tripGroupId) ?? 0
        actorMemberId = try container.decodeIfPresent(Int64.self, forKey: .actorMemberId)
        dayNumber = try container.decodeIfPresent(Int.self, forKey: .dayNumber)
        timelineId = try container.decodeIfPresent(Int64.self, forKey: .timelineId)
        timelineIds = try container.decodeIfPresent([Int64].self, forKey: .timelineIds) ?? []
        voteId = try container.decodeIfPresent(Int64.self, forKey: .voteId)
        tripPlaceId = try container.decodeIfPresent(Int64.self, forKey: .tripPlaceId)
        occurredAt = try container.decodeIfPresent(String.self, forKey: .occurredAt) ?? ""
    }

    var id: String { eventId }
    var topic: TripEventTopic {
        switch eventType {
        case "TRIP_GROUP_UPDATED", "TRIP_MEMBER_JOINED", "FREE_TIME_RANGE_UPDATED": .trip
        case "WISH_PLACE_ADDED", "WISH_PLACE_DELETED": .place
        case "TIMELINE_CREATED", "TIMELINE_BATCH_CREATED", "TIMELINE_TIME_UPDATED", "TIMELINE_DELETED", "TIMELINE_PLACE_CONFIRMED": .timeline
        case "VOTE_CREATED", "VOTE_PARTICIPATION_UPDATED", "VOTE_EXPIRED": .vote
        default: .trip
        }
    }

    var changeCount: Int {
        if eventType == "TIMELINE_BATCH_CREATED", !timelineIds.isEmpty {
            return timelineIds.count
        }
        return 1
    }

    var summaryEventType: String {
        eventType == "TIMELINE_BATCH_CREATED" ? "TIMELINE_CREATED" : eventType
    }
}

enum TripEventTopic: Hashable {
    case place, timeline, vote, trip

    var title: String {
        switch self {
        case .place: "후보 장소 변경"
        case .timeline: "시간 구간 변경"
        case .vote: "투표 변경"
        case .trip: "여행방 변경"
        }
    }
}

enum TripEventSyncScope: Hashable {
    case tripOverview
    case candidates
    case voteList
    case timeline(dayNumber: Int)
    case voteDetail(voteID: Int64)

    func matches(_ event: TripEventPayload) -> Bool {
        switch self {
        case .tripOverview:
            return event.eventType == "TRIP_MEMBER_JOINED"
        case .candidates:
            return ["WISH_PLACE_ADDED", "WISH_PLACE_DELETED"].contains(event.eventType)
        case .voteList:
            return [
                "TIMELINE_CREATED",
                "TIMELINE_BATCH_CREATED",
                "TIMELINE_TIME_UPDATED",
                "TIMELINE_DELETED",
                "VOTE_CREATED",
                "VOTE_PARTICIPATION_UPDATED",
                "TIMELINE_PLACE_CONFIRMED",
                "VOTE_EXPIRED",
            ].contains(event.eventType)
        case .timeline(let dayNumber):
            let supported = [
                "TIMELINE_CREATED",
                "TIMELINE_BATCH_CREATED",
                "TIMELINE_TIME_UPDATED",
                "TIMELINE_DELETED",
                "VOTE_CREATED",
                "TIMELINE_PLACE_CONFIRMED",
            ].contains(event.eventType)
            return supported && (event.dayNumber == nil || event.dayNumber == dayNumber)
        case .voteDetail(let voteID):
            let supported = [
                "WISH_PLACE_ADDED",
                "VOTE_PARTICIPATION_UPDATED",
                "TIMELINE_PLACE_CONFIRMED",
                "VOTE_EXPIRED",
            ].contains(event.eventType)
            guard supported else { return false }
            return event.eventType == "WISH_PLACE_ADDED" || event.voteId == nil || event.voteId == voteID
        }
    }
}
