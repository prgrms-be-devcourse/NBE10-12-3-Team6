package csh.back.domain.trip.place.service

import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse
import csh.back.domain.trip.place.dto.response.TripPlaceSaveResponse
import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException
import csh.back.domain.trip.place.repository.TripPlaceRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TripPlaceService(
    private val tripPlaceRepository: TripPlaceRepository,
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripMemberValidator: TripMemberValidator,
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

        return try {
            TripPlaceSaveResponse.from(tripPlaceRepository.saveAndFlush(place))
        } catch (e: DataIntegrityViolationException) {
            // 사전 체크를 뚫고 동시성으로 들어온 케이스
            throw DuplicateTripPlaceException(kakaoPlaceId)
        }
    }
}