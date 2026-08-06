package csh.back.domain.trip.place.service

import csh.back.domain.trip.chat.service.ChatService
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse
import csh.back.domain.trip.place.dto.response.TripPlaceSaveResponse
import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException
import csh.back.domain.trip.place.exception.TripAlreadyStartedException
import csh.back.domain.trip.place.exception.WishPlaceInUseException
import csh.back.domain.trip.place.repository.TripPlaceRepository
import csh.back.domain.vote.item.repository.VoteItemRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class TripPlaceService(
    private val tripPlaceRepository: TripPlaceRepository,
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripMemberValidator: TripMemberValidator,
    private val tripEventService: TripEventService,
    private val voteItemRepository: VoteItemRepository,
    private val chatService: ChatService,
) {

    fun findWishPlaces(tripGroupId: Long, memberId: Long): List<TripPlaceFindResponse> {
        tripMemberValidator.validMember(tripGroupId, memberId)

        return tripPlaceRepository.findAllByTripGroupId(tripGroupId)
            .map(TripPlaceFindResponse::from)
    }

    @Transactional
    fun savePlace(
        tripGroupId: Long,
        name: String,
        category: String,
        address: String,
        kakaoPlaceId: String,
        kakaoMapUrl: String,
        memberId: Long,
    ): TripPlaceSaveResponse {
        tripMemberValidator.validMember(tripGroupId, memberId)

        if (tripPlaceRepository.existsByKakaoPlaceIdAndTripGroupId(kakaoPlaceId, tripGroupId)) {
            throw DuplicateTripPlaceException(kakaoPlaceId)
        }

        val tripGroup = tripGroupRepository.findById(tripGroupId).orElseThrow(::RuntimeException)
        val tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId)
            .orElseThrow(::RuntimeException)
        val place = TripPlace(
            tripGroup = tripGroup,
            name = name,
            category = category,
            address = address,
            kakaoPlaceId = kakaoPlaceId,
            kakaoMapUrl = kakaoMapUrl,
            createdBy = tripMember,
        )

        val savedPlace = try {
            tripPlaceRepository.saveAndFlush(place)
        } catch (e: DataIntegrityViolationException) {
            // 사전 체크를 뚫고 동시성으로 들어온 케이스
            throw DuplicateTripPlaceException(kakaoPlaceId)
        }

        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.WISH_PLACE_ADDED,
                message = "${savedPlace.name} 후보 장소 추가",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                tripPlaceId = requireNotNull(savedPlace.id),
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${savedPlace.name} 후보 장소 추가")
        return TripPlaceSaveResponse.from(savedPlace)
    }

    @Transactional
    fun deletePlace(
        tripGroupId: Long,
        tripPlaceId: Long,
        memberId: Long,
    ) {
        tripMemberValidator.validMember(tripGroupId, memberId)

        val tripPlace = tripPlaceRepository.findByIdAndTripGroupId(tripPlaceId, tripGroupId)
            .orElseThrow { NotFoundException("후보 장소를 찾을 수 없습니다.") }

        if (tripPlace.createdBy.member.id != memberId) {
            throw NonMemberException("작성자만 삭제할 수 있습니다.")
        }

        if (!tripPlace.tripGroup.startDate.isAfter(LocalDate.now())) {
            throw TripAlreadyStartedException("여행 시작 전에만 후보 장소를 삭제할 수 있습니다.")
        }

        if (voteItemRepository.existsByTripPlaceId(tripPlaceId)) {
            throw WishPlaceInUseException("이미 투표에 사용된 장소는 삭제할 수 없습니다.")
        }

        try {
            tripPlaceRepository.delete(tripPlace)
            tripPlaceRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            // 사전 체크를 뚫고 동시성으로 투표에 사용된 케이스
            throw WishPlaceInUseException("이미 투표에 사용된 장소는 삭제할 수 없습니다.")
        }

        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.WISH_PLACE_DELETED,
                message = "${tripPlace.name}이(가) 후보 장소에서 삭제되었습니다.",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                tripPlaceId = tripPlaceId,
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${tripPlace.name}이(가) 후보 장소에서 삭제되었습니다.")
    }
}
