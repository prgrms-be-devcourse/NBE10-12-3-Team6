package csh.back.domain.trip.event.enums

enum class TripEventType {
    TRIP_GROUP_UPDATED,
    TRIP_MEMBER_JOINED,
    FREE_TIME_RANGE_UPDATED,
    WISH_PLACE_ADDED,
    WISH_PLACE_DELETED,
    TIMELINE_CREATED,
    TIMELINE_BATCH_CREATED,
    TIMELINE_TIME_UPDATED,
    TIMELINE_DELETED,
    VOTE_CREATED,
    VOTE_PARTICIPATION_UPDATED,
    TIMELINE_PLACE_CONFIRMED,
    VOTE_EXPIRED,
    ;

    fun affectsTimeline(): Boolean =
        when (this) {
            TIMELINE_CREATED,
            TIMELINE_BATCH_CREATED,
            TIMELINE_TIME_UPDATED,
            TIMELINE_DELETED,
            VOTE_CREATED,
            TIMELINE_PLACE_CONFIRMED,
            -> true

            else -> false
        }
}
