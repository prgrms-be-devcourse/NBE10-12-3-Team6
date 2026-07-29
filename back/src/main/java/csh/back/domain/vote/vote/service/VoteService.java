package csh.back.domain.vote.vote.service;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.member.validator.TripMemberValidator;
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse;
import csh.back.domain.trip.place.service.TripPlaceService;
import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.domain.trip.timeline.repository.TimelineRepository;
import csh.back.domain.vote.item.entity.VoteItem;
import csh.back.domain.vote.item.repository.VoteItemRepository;
import csh.back.domain.vote.user.entity.VoteUser;
import csh.back.domain.vote.user.repository.VoteUserRepository;
import csh.back.domain.vote.vote.dto.response.*;
import csh.back.domain.vote.vote.dto.web.VoteTimelineResponse;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.enums.VoteStatus;
import csh.back.domain.vote.vote.repository.VoteRepository;
import csh.back.domain.vote.vote.repository.VoteTimelineIdProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final TimelineRepository timeLineRepository;
    private final TripMemberValidator tripMemberValidator;
    private final TripMemberRepository tripMemberRepository;
    private final TripPlaceService tripPlaceService;

    private final int DEFAULT_UPDATE_COUNT = 0;

    // 투표 목록 조회해오는거(투표탭에서 사용됨)
    public List<VoteFindListResponse> findVoteList(Long tripGroupId, Long memberId) {
        tripMemberValidator.validMember(tripGroupId, memberId);

        TripGroup tripGroup = tripGroupRepository.findById(tripGroupId).orElseThrow(RuntimeException::new);

        Integer totalDays = tripGroup.getNights() + 1;
        Map<Long, List<Timeline>> byDay = timeLineRepository.findAllByTripGroupId(tripGroupId).stream()
                .collect(Collectors.groupingBy(Timeline::getDayNumber));

        Map<Long, Vote> byTimeLineId = voteRepository.findVotesWithTimelineByTripGroupId(tripGroupId).stream()
                .collect(Collectors.toMap(
                        vote -> vote.getTimeline().getId(),
                        vote -> vote
                ));

        List<VoteFindListResponse> voteFindListResponses = new ArrayList<>();
        for (int day = 1; day <= totalDays; day++) {
            List<VoteWithTimelineResponse> timeLineResponses =
                    byDay.getOrDefault((long) day, List.of()).stream()
                            .map(timeLine -> createVoteAndTimeLineResponse(timeLine, byTimeLineId))
                            .toList();

            voteFindListResponses.add(
                    VoteFindListResponse.of(tripGroup.getStartDate().plusDays(day - 1), timeLineResponses)
            );
        }

        return voteFindListResponses;
    }

    // 장소 : 몇표를 표현하기 위한 메소드(투표하는 화면 진입 시 사용)
    public VoteFindWithUpdateCountResponse findVoteItemAndCount(Long tripGroupId, Long voteId, Long memberId) {
        tripMemberValidator.validMember(tripGroupId, memberId);

        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId).orElseThrow(RuntimeException::new); // 이게 없으면 에러가 맞지
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
        List<TripPlaceFindResponse> wishPlaceFindResponses = tripPlaceService.findWishPlaces(tripGroupId, memberId);
        //장소의 아이디를 키로 하여 위의 맵에서 횟수를 매핑하여 반환
        return VoteFindWithUpdateCountResponse.of(voteFindResponses, wishPlaceFindResponses, updateCount, vote);
    }

    public Map<Long, Long> findAllVoteIds(List<Long> timeLineIds) {
        List<VoteTimelineIdProjection> voteTimeLineIdProjections = voteRepository.findVoteIdsByTimeLineIds(timeLineIds);
        return voteTimeLineIdProjections.stream()
                .collect(Collectors.toMap(
                    item -> item.getTimelineId(),
                    item -> item.getVoteId()
                ));
    }

    // 해당 장소에 투표한 유저가 누구인지 표현하기 위한 메소드(현재 사용안됨)
    public List<VoteFindUserResponse> findUserVoteThisPlace(Long tripGroupId, Long voteId, Long tripPlaceId, Long memberId) {
        tripMemberValidator.validMember(tripGroupId, memberId);

        VoteItem voteItem = voteItemRepository.findByVoteIdAndTripPlaceId(voteId, tripPlaceId).orElseThrow(RuntimeException::new);
        List<VoteUser> voteUsers = voteUserRepository.findByVoteItemId(voteItem.getId());
        List<VoteFindUserResponse> responses = voteUsers.stream().map(VoteFindUserResponse::from).toList();
        return responses;
    }

    @Transactional
    public VoteCreateResponse wrapperCreateVote(Long tripGroupId, Long memberId, Long timeLineId) {
        Timeline timeLine = timeLineRepository.findById(timeLineId).orElseThrow(RuntimeException::new);
        return createVote(tripGroupId, memberId, timeLine);
    }

    @Transactional
    public VoteCreateResponse createVote(Long tripGroupId, Long memberId, Timeline timeLine) {
        tripMemberValidator.validMember(tripGroupId, memberId);
        TripGroup tripGroup = tripGroupRepository.findById(tripGroupId).orElseThrow(RuntimeException::new);
        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId).orElseThrow(RuntimeException::new);
        LocalDateTime expireTime = tripGroup.getStartDate().minusDays(1).atStartOfDay();
        Vote vote = Vote
                .builder()
                .tripGroup(tripGroup)
                .timeline(timeLine)
                .tripMember(tripMember)
                .expireTime(expireTime)
                .build();
        Vote saved = voteRepository.save(vote);
        return VoteCreateResponse.from(saved);
    }

    @Transactional
    public void createVoteBatch(Long tripGroupId, Long memberId, List<Timeline> timeLines) {
        tripMemberValidator.validMember(tripGroupId, memberId);
        TripGroup tripGroup = tripGroupRepository.findById(tripGroupId).orElseThrow(RuntimeException::new);
        LocalDateTime expireTime = tripGroup.getStartDate().minusDays(1).atStartOfDay();
        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId).orElseThrow(RuntimeException::new);

        List<Vote> votes = timeLines.stream()
                .map(timeLine -> Vote
                        .builder()
                        .tripGroup(tripGroup)
                        .timeline(timeLine)
                        .tripMember(tripMember)
                        .expireTime(expireTime)
                        .build())
                .toList();
        voteRepository.saveAll(votes);
    }

    public VoteTimelineResponse voteConfirm(Long maxVoteItemId, Long voteId) {
        VoteItem voteItem = voteItemRepository.findById(maxVoteItemId).orElseThrow(RuntimeException::new);
        Long confirmPlaceId = voteItem.getTripPlace().getId();
        Timeline timeLine = voteRepository.findTimeLineByVoteId(voteId).orElseThrow(RuntimeException::new);
        voteItem.getVote().updateStatus(VoteStatus.CONFIRMED);
        return VoteTimelineResponse.of(confirmPlaceId, timeLine);
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

    private VoteWithTimelineResponse createVoteAndTimeLineResponse(
            Timeline timeLine,
            Map<Long, Vote> byTimeLindId
    ) {
        Long timeLineId = timeLine.getId();
        Vote vote = byTimeLindId.get(timeLineId);
        if(vote == null) {
            log.error("Timeline {}과 연결된 Vote가 존재 하지 않습니다! 확인 해주세요!", timeLineId);
            return VoteWithTimelineResponse.of(timeLine, null);
        }
        return VoteWithTimelineResponse.of(timeLine, vote);
    }


}
