package csh.back.domain.vote.vote.service;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.member.validator.TripMemberValidator;
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse;
import csh.back.domain.trip.place.service.TripPlaceService;
import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.domain.trip.timeline.repository.TimeLineRepository;
import csh.back.domain.vote.item.entity.VoteItem;
import csh.back.domain.vote.item.repository.VoteItemRepository;
import csh.back.domain.vote.user.entity.VoteUser;
import csh.back.domain.vote.user.repository.VoteUserRepository;
import csh.back.domain.vote.vote.dto.response.*;
import csh.back.domain.vote.vote.dto.web.VoteTimeLineResponse;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.enums.VoteStatus;
import csh.back.domain.vote.vote.repository.VoteRepository;
import csh.back.domain.vote.vote.repository.VoteTimeLineIdProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {
    private final VoteRepository voteRepository;
    private final VoteItemRepository voteItemRepository;
    private final VoteUserRepository voteUserRepository;
    private final TripGroupRepository tripGroupRepository;
    private final TimeLineRepository timeLineRepository;
    private final TripMemberValidator tripMemberValidator;
    private final TripMemberRepository tripMemberRepository;
    private final TripPlaceService tripPlaceService;

    private final int DEFAULT_UPDATE_COUNT = 0;

    // 투표 목록 조회해오는거(투표탭에서 사용됨)
    public List<VoteFindListResponse> findVoteList(Long tripId, Long memberId) {
        tripMemberValidator.validMember(tripId, memberId);

        TripGroup tripGroup = tripGroupRepository.findById(tripId).orElseThrow(RuntimeException::new);

        Integer totalDays = tripGroup.getNights() + 1;
        Map<Integer, List<TimeLine>> byDay = timeLineRepository.findAllByTripGroupId(tripId).stream()
                .collect(Collectors.groupingBy(TimeLine::getDayNumber));

        Map<Long, Vote> byTimeLineId = voteRepository.findVotesWithTimeLineByTripGroupId(tripId).stream()
                .collect(Collectors.toMap(
                        vote -> vote.getTimeLine().getId(),
                        vote -> vote
                ));

        List<VoteFindListResponse> voteFindListResponses = new ArrayList<>();
        for (int day = 1; day <= totalDays; day++) {
            List<VoteWithTimeLineResponse> timeLineResponses =
                    byDay.getOrDefault(day, List.of()).stream()
                            .map(timeLine -> createVoteAndTimeLineResponse(timeLine, byTimeLineId))
                            .toList();

            voteFindListResponses.add(
                    VoteFindListResponse.of(tripGroup.getStartDate().plusDays(day - 1), timeLineResponses)
            );
        }

        return voteFindListResponses;
    }

    // 장소 : 몇표를 표현하기 위한 메소드(투표하는 화면 진입 시 사용)
    public VoteFindWithUpdateCountResponse findVoteItemAndCount(Long tripId, Long voteId, Long memberId) {
        tripMemberValidator.validMember(tripId, memberId);

        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripId).orElseThrow(RuntimeException::new); // 이게 없으면 에러가 맞지
        VoteUser voteUser = voteUserRepository.findByVoteIdAndTripMemberId(voteId ,tripMember.getId()).orElse(null); // 이건 없을수있지

        //투표된 장소 목록을 조회해 옴
        List<VoteItem> voteItemList = voteItemRepository.findAllByVoteIdWithTripPlace(voteId);
        //각 장소에 몇포가 투표 되었는지 카운팅
        Map<Long, Long> countMap = voteCount(voteId);

        Vote vote = voteRepository.findById(voteId).orElseThrow(RuntimeException::new);

        VoteItem voteItem = voteUser != null ? voteUser.getVoteItem() : null;
        int updateCount = voteUser != null ? voteUser.getUpdateCount() : DEFAULT_UPDATE_COUNT;

        List<VoteFindResponse> voteFindResponses = voteItemList.stream()
                .map(vi -> VoteFindResponse.of(
                        vi.getTripPlace().getId(),
                        vi.getTripPlace().getName(),
                        countMap.getOrDefault(vi.getId(), 0L),
                        vi.equals(voteItem)
                ))
                .toList();
        List<TripPlaceFindResponse> wishPlaceFindResponses = tripPlaceService.findWishPlaces(tripId, memberId);
        //장소의 아이디를 키로 하여 위의 맵에서 횟수를 매핑하여 반환
        return VoteFindWithUpdateCountResponse.of(voteFindResponses, wishPlaceFindResponses, updateCount, vote);
    }

    public Map<Long, Long> findAllVoteIds(List<Long> timeLineIds) {
        List<VoteTimeLineIdProjection> voteTimeLineIdProjections = voteRepository.findVoteIdsByTimeLineIds(timeLineIds);
        return voteTimeLineIdProjections.stream()
                .collect(Collectors.toMap(
                    item -> item.getTimeLineId(),
                    item -> item.getVoteId()
                ));
    }

    // 해당 장소에 투표한 유저가 누구인지 표현하기 위한 메소드(현재 사용안됨)
    public List<VoteFindUserResponse> findUserVoteThisPlace(Long tripId, Long voteId, Long placeId, Long memberId) {
        tripMemberValidator.validMember(tripId, memberId);

        VoteItem voteItem = voteItemRepository.findByVoteIdAndTripPlaceId(voteId, placeId).orElseThrow(RuntimeException::new);
        List<VoteUser> voteUsers = voteUserRepository.findByVoteItemId(voteItem.getId());
        List<VoteFindUserResponse> responses = voteUsers.stream().map(VoteFindUserResponse::from).toList();
        return responses;
    }

    @Transactional
    public VoteCreateResponse wrapperCreateVote(Long tripId, Long memberId, Long timeLineId) {
        TimeLine timeLine = timeLineRepository.findById(timeLineId).orElseThrow(RuntimeException::new);
        return createVote(tripId, memberId, timeLine);
    }

    @Transactional
    public VoteCreateResponse createVote(Long tripId, Long memberId, TimeLine timeLine) {
        tripMemberValidator.validMember(tripId, memberId);
        TripGroup tripGroup = tripGroupRepository.findById(tripId).orElseThrow(RuntimeException::new);
        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripId).orElseThrow(RuntimeException::new);
        LocalDateTime expireTime = tripGroup.getStartDate().minusDays(1).atStartOfDay();
        Vote vote = Vote
                .builder()
                .tripGroup(tripGroup)
                .timeLine(timeLine)
                .tripMember(tripMember)
                .expireTime(expireTime)
                .build();
        Vote saved = voteRepository.save(vote);
        return VoteCreateResponse.from(saved);
    }

    @Transactional
    public void createVoteBatch(Long tripId, Long memberId, List<TimeLine> timeLines) {
        tripMemberValidator.validMember(tripId, memberId);
        TripGroup tripGroup = tripGroupRepository.findById(tripId).orElseThrow(RuntimeException::new);
        LocalDateTime expireTime = tripGroup.getStartDate().minusDays(1).atStartOfDay();
        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripId).orElseThrow(RuntimeException::new);

        List<Vote> votes = timeLines.stream()
                .map(timeLine -> Vote
                        .builder()
                        .tripGroup(tripGroup)
                        .timeLine(timeLine)
                        .tripMember(tripMember)
                        .expireTime(expireTime)
                        .build())
                .toList();
        voteRepository.saveAll(votes);
    }

    public VoteTimeLineResponse voteConfirm(Long maxVoteItemId, Long voteId) {
        VoteItem voteItem = voteItemRepository.findById(maxVoteItemId).orElseThrow(RuntimeException::new);
        Long confirmPlaceId = voteItem.getTripPlace().getId();
        TimeLine timeLine = voteRepository.findTimeLineByVoteId(voteId).orElseThrow(RuntimeException::new);
        voteItem.getVote().updateStatus(VoteStatus.CONFIRMED);
        return VoteTimeLineResponse.of(confirmPlaceId, timeLine);
    }

    public Map<Long, Long> voteCount(Long voteId) {
        return voteUserRepository.countGroupByVoteId(voteId)
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getVoteItemId(),
                        row -> row.getVoteCount()
                ));
    }

    public void expireVote(Long voteId) {
        Vote vote = voteRepository.findById(voteId).orElseThrow(RuntimeException::new);
        vote.updateStatus(VoteStatus.EXPIRED);
    }

    private VoteWithTimeLineResponse createVoteAndTimeLineResponse(
            TimeLine timeLine,
            Map<Long, Vote> byTimeLindId
    ) {
        Long timeLineId = timeLine.getId();
        Vote vote = byTimeLindId.get(timeLineId);
        if(vote == null) {
            log.error("TimeLine {}과 연결된 Vote가 존재 하지 않습니다! 확인 해주세요!", timeLineId);
            return VoteWithTimeLineResponse.of(timeLine, null);
        }
        return VoteWithTimeLineResponse.of(timeLine, vote);
    }


}
