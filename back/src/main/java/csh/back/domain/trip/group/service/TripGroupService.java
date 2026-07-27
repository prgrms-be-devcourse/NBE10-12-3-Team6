package csh.back.domain.trip.group.service;

import csh.back.domain.member.entity.Member;
import csh.back.domain.member.repository.MemberRepository;
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest;
import csh.back.domain.trip.group.dto.request.TripGroupRequest;
import csh.back.domain.trip.group.dto.response.TripGroupDetailResponse;
import csh.back.domain.trip.group.dto.response.TripGroupResponse;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.exception.NonMemberException;
import csh.back.domain.trip.group.exception.NotFoundException;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.dto.response.TripMemeberResponse;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripGroupService {

	private final TripGroupRepository tripGroupRepository;
	private final TripMemberRepository tripMemberRepository;
	private final MemberRepository memberRepository;

	// 모임방 조회
	@Transactional(readOnly = true)
	public List<TripGroupResponse> getGroups(Long ownerId, String keyword, String startDate) {
		List<TripGroup> tripGroups = tripGroupRepository.findAllByMemberIdWithSearch(ownerId, keyword, startDate);
		return tripGroups
				.stream()
				.map(TripGroupResponse::from)
				.toList();
	}

	// 모임방 생성
	@Transactional
	public TripGroupResponse writeGroup(TripGroupRequest request, Long ownerId) {
		LocalDate startDate = LocalDate.parse(request.startDate());

		LocalDate endDate = startDate.plusDays(request.nights());

		Member owner = memberRepository.findById(ownerId)
				.orElseThrow(() -> new NotFoundException("존재하지 않는 유저"));

		TripGroup group = TripGroup.builder()
				.owner(owner)
				.name(request.name())
				.region(request.region())
				.nights(request.nights())
				.joinCode(createJoinCode())
				.startDate(startDate)
				.endDate(endDate)
				.build();
		TripGroup savedGroup = tripGroupRepository.save(group);

		tripMemberRepository.save(
				TripMember.builder()
						.tripGroup(savedGroup)
						.member(owner)
						.isAdmin(true)
						.build()
		);

		return TripGroupResponse.from(savedGroup);
	}

	//모임 상세 조회
	@Transactional(readOnly = true)
	public TripGroupDetailResponse getGroupDetail(Long tripId, Long ownerId) {
		TripGroup group = tripGroupRepository.findById(tripId)
				.orElseThrow(() -> new NotFoundException("존재하지 않는 모임입니다."));

		boolean isMember = tripMemberRepository.existsByTripGroupIdAndMemberId(tripId, ownerId);
		if (!isMember) {
			throw new NonMemberException("해당 모임의 멤버가 아닙니다.");
		}

		List<TripMemeberResponse> members = tripMemberRepository.findByTripGroupId(tripId)
				.stream()
				.map(tm -> TripMemeberResponse.from(tm))
				.toList();

		return TripGroupDetailResponse.from(group, members);
	}

	//모임 상세 수정
	//TODO 1차 mvp에서는 name만 수정, 혹시몰라 patch로 진행
	@Transactional
	public TripGroupResponse modifyGroupDetail(Long tripId, Long ownerId, TripGroupModifyRequest request) {
		TripGroup group = tripGroupRepository.findById(tripId)
				.orElseThrow(() -> new NotFoundException("존재하지 않는 모임입니다."));

		if (!group.getOwner().getId().equals(ownerId)) {
			throw new NonMemberException("해당 모임의 소유자가 아닙니다.");
		}

		group.modify(request);
		return TripGroupResponse.from(group);
	}

	//초대링크 생성 함수
	public String createJoinCode() {
		String joinCode;
		do {
			//count: 글자수 제한, letters: 영문혼합, numbers: 숫자혼합
			joinCode = RandomStringUtils.random(7, true, true); //setlog와 같은 문자열 생성
		} while (tripGroupRepository.existsByJoinCode(joinCode)); //혹시라도 다른방과 url이 같은걸 막기위해
		return joinCode;
	}

	public TripGroup findTripGroupById(Long tripId) {
		return tripGroupRepository.findById(tripId).orElseThrow(RuntimeException::new);
	}
}
