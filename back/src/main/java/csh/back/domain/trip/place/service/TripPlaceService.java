package csh.back.domain.trip.place.service;


import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.member.validator.TripMemberValidator;
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse;
import csh.back.domain.trip.place.dto.response.TripPlaceSaveResponse;
import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException;
import csh.back.domain.trip.place.repository.TripPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class TripPlaceService {
    private final TripPlaceRepository tripPlaceRepository;
    private final TripGroupRepository tripGroupRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripMemberValidator tripMemberValidator;

    public List<TripPlaceFindResponse> findWishPlaces(Long tripId, Long memberId) {

        tripMemberValidator.validMember(tripId, memberId);

        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripGroupId(tripId);
        return tripPlaces
                .stream()
                .map(TripPlaceFindResponse::from)
                .toList();
    }

    @Transactional
    public TripPlaceSaveResponse savePlace(Long tripId,
                                           String name,
                                           String theme,
                                           String address,
                                           String kakaoPlaceId,
                                           String kakaoMapUrl,
                                           Long memberId) {

        tripMemberValidator.validMember(tripId, memberId);

        TripGroup tripGroup = tripGroupRepository.findById(tripId).orElseThrow(RuntimeException::new);
        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripId).orElseThrow(RuntimeException::new);
        TripPlace place = TripPlace
                .builder()
                .tripGroup(tripGroup)
                .name(name)
                .theme(theme)
                .address(address)
                .kakaoPlaceId(kakaoPlaceId)
                .kakaoMapUrl(kakaoMapUrl)
                .createdBy(tripMember)
                .build();
        try {
            TripPlace saveResult = tripPlaceRepository.saveAndFlush(place);
            return TripPlaceSaveResponse.from(saveResult);
        } catch (DataIntegrityViolationException e) {
            // 사전 체크를 뚫고 동시성으로 들어온 케이스
            throw new DuplicateTripPlaceException(kakaoPlaceId);
        }
    }
}
