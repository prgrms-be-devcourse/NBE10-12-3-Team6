package csh.back.domain.vote.vote.service;

import csh.back.domain.member.entity.Member;
import csh.back.domain.member.repository.MemberRepository;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.place.repository.TripPlaceRepository;
import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.domain.trip.timeline.repository.TimelineRepository;
import csh.back.domain.vote.item.entity.VoteItem;
import csh.back.domain.vote.item.repository.VoteItemRepository;
import csh.back.domain.vote.user.entity.VoteUser;
import csh.back.domain.vote.user.repository.VoteUserRepository;
import csh.back.domain.vote.vote.dto.response.VoteFindListResponse;
import csh.back.domain.vote.vote.dto.response.VoteCreateResponse;
import csh.back.domain.vote.vote.dto.response.VoteFindUserResponse;
import csh.back.domain.vote.vote.dto.response.VoteFindWithUpdateCountResponse;
import csh.back.domain.vote.vote.dto.web.VoteTimelineResponse;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.enums.VoteStatus;
import csh.back.domain.vote.vote.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class VoteServiceTest {

    @Autowired
    private VoteService voteService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TripGroupRepository tripGroupRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private TimelineRepository timelineRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteItemRepository voteItemRepository;

    @Autowired
    private VoteUserRepository voteUserRepository;

    @Autowired
    private TripPlaceRepository tripPlaceRepository;

    private Member owner;
    private TripGroup tripGroup;
    private TripMember ownerTripMember;

    @BeforeEach
    void setUp() {
        owner = memberRepository.save(new Member("vote-owner@test.com", "pw", "투표주인장"));

        tripGroup = tripGroupRepository.save(new TripGroup(
                owner,
                "투표테스트여행",
                "서울",
                1,
                "VOTE-JOIN-1",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2)
        ));

        ownerTripMember = tripMemberRepository.save(new TripMember(owner, tripGroup, true));
    }

    private Timeline createTimeline(long dayNumber, LocalDateTime start, LocalDateTime end) {
        return timelineRepository.save(Timeline.builder()
                .tripGroup(tripGroup)
                .dayNumber(dayNumber)
                .startTime(start)
                .endTime(end)
                .build());
    }

    private TripPlace createPlace(String kakaoPlaceId) {
        return tripPlaceRepository.save(new TripPlace(
                tripGroup, "장소-" + kakaoPlaceId, "관광", "주소", kakaoPlaceId, "url", ownerTripMember));
    }

    @Test
    @DisplayName("findVoteList - 일자별 그룹핑, 타임라인 없는 날은 빈 리스트")
    void findVoteList() {
        Timeline day1Timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, day1Timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));

        List<VoteFindListResponse> result = voteService.findVoteList(tripGroup.getId(), owner.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDate()).isEqualTo(tripGroup.getStartDate());
        assertThat(result.get(0).getTimeLines()).hasSize(1);
        assertThat(result.get(0).getTimeLines().get(0).getVoteId()).isEqualTo(vote.getId());
        assertThat(result.get(0).getTimeLines().get(0).getVoteStatus()).isEqualTo(VoteStatus.PENDING.getNickname());

        assertThat(result.get(1).getDate()).isEqualTo(tripGroup.getStartDate().plusDays(1));
        assertThat(result.get(1).getTimeLines()).isEmpty();
    }

    @Test
    @DisplayName("wrapperCreateVote/createVote - 투표 생성 후 저장 값 확인")
    void createVote() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0),
                LocalDateTime.of(2026, 10, 1, 10, 0));

        VoteCreateResponse response = voteService.wrapperCreateVote(tripGroup.getId(), owner.getId(), timeline.getId());

        Vote saved = voteRepository.findById(response.getVoteId()).orElseThrow();
        assertThat(saved.getTripGroup().getId()).isEqualTo(tripGroup.getId());
        assertThat(saved.getTimeline().getId()).isEqualTo(timeline.getId());
        assertThat(saved.getExpireTime()).isEqualTo(tripGroup.getStartDate().minusDays(1).atStartOfDay());
        assertThat(saved.getStatus()).isEqualTo(VoteStatus.PENDING);
    }

    @Test
    @DisplayName("createVoteBatch - 여러 타임라인에 대해 일괄 생성됨")
    void createVoteBatch() {
        Timeline timeline1 = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Timeline timeline2 = createTimeline(2L,
                LocalDateTime.of(2026, 10, 2, 9, 0), LocalDateTime.of(2026, 10, 2, 10, 0));

        voteService.createVoteBatch(tripGroup.getId(), owner.getId(), List.of(timeline1, timeline2));

        List<Vote> votes = voteRepository.findVotesWithTimelineByTripGroupId(tripGroup.getId());
        assertThat(votes).hasSize(2);
        assertThat(votes).extracting(vote -> vote.getTimeline().getId())
                .containsExactlyInAnyOrder(timeline1.getId(), timeline2.getId());
    }

    @Test
    @DisplayName("findVoteItemAndCount - 항목별 카운트 집계, 미투표 유저는 updateCount=0")
    void findVoteItemAndCount() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));

        TripPlace place1 = createPlace("kakao-count-1");
        TripPlace place2 = createPlace("kakao-count-2");

        VoteItem voteItem1 = voteItemRepository.save(new VoteItem(vote, place1));
        VoteItem voteItem2 = voteItemRepository.save(new VoteItem(vote, place2));

        Member other = memberRepository.save(new Member("vote-other@test.com", "pw", "다른투표자"));
        TripMember otherTripMember = tripMemberRepository.save(new TripMember(other, tripGroup, false));

        voteUserRepository.save(new VoteUser(vote, voteItem1, ownerTripMember, 0));
        voteUserRepository.save(new VoteUser(vote, voteItem2, otherTripMember, 0));

        VoteFindWithUpdateCountResponse response =
                voteService.findVoteItemAndCount(tripGroup.getId(), vote.getId(), owner.getId());

        assertThat(response.getUpdateCount()).isEqualTo(0);
        assertThat(response.getVoteStatus()).isEqualTo(VoteStatus.PENDING.getNickname());
        assertThat(response.getVoteResults()).hasSize(2);

        response.getVoteResults().forEach(voteFindResponse -> {
            if (voteFindResponse.getTripPlaceId().equals(place1.getId())) {
                assertThat(voteFindResponse.getCount()).isEqualTo(1L);
                assertThat(voteFindResponse.isVoted()).isTrue();
            } else if (voteFindResponse.getTripPlaceId().equals(place2.getId())) {
                assertThat(voteFindResponse.getCount()).isEqualTo(1L);
                assertThat(voteFindResponse.isVoted()).isFalse();
            }
        });
    }

    @Test
    @DisplayName("voteConfirm - 확정 처리 후 Vote.status가 CONFIRMED로 변경")
    void voteConfirm() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));
        TripPlace place = createPlace("kakao-confirm-1");
        VoteItem voteItem = voteItemRepository.save(new VoteItem(vote, place));

        VoteTimelineResponse response = voteService.voteConfirm(voteItem.getId(), vote.getId());

        assertThat(response.getConfirmPlaceId()).isEqualTo(place.getId());
        assertThat(response.getTimeline().getId()).isEqualTo(timeline.getId());

        Vote confirmed = voteRepository.findById(vote.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
    }

    @Test
    @DisplayName("expireVote - 상태가 EXPIRED로 변경")
    void expireVote() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));

        voteService.expireVote(vote.getId());

        Vote expired = voteRepository.findById(vote.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(VoteStatus.EXPIRED);
    }

    @Test
    @DisplayName("findUserVoteThisPlace - 특정 장소에 투표한 유저 목록 반환")
    void findUserVoteThisPlace() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));
        TripPlace place = createPlace("kakao-users-1");
        VoteItem voteItem = voteItemRepository.save(new VoteItem(vote, place));

        Member other = memberRepository.save(new Member("vote-user2@test.com", "pw", "투표자2"));
        TripMember otherTripMember = tripMemberRepository.save(new TripMember(other, tripGroup, false));

        voteUserRepository.save(new VoteUser(vote, voteItem, ownerTripMember, 0));
        voteUserRepository.save(new VoteUser(vote, voteItem, otherTripMember, 0));

        List<VoteFindUserResponse> responses =
                voteService.findUserVoteThisPlace(tripGroup.getId(), vote.getId(), place.getId(), owner.getId());

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(VoteFindUserResponse::getName)
                .containsExactlyInAnyOrder(owner.getName(), other.getName());
    }

    @Test
    @DisplayName("updateAnonymous - 방장이 변경 시 isAnonymous 값이 변경됨")
    void updateAnonymous() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));

        boolean result = voteService.updateAnonymous(tripGroup.getId(), vote.getId(), owner.getId(), false);

        assertThat(result).isFalse();
        Vote updated = voteRepository.findById(vote.getId()).orElseThrow();
        assertThat(updated.isAnonymous()).isFalse();
    }

    @Test
    @DisplayName("updateAnonymous - 방장이 아니면 예외 발생")
    void updateAnonymousNonAdmin() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));

        Member other = memberRepository.save(new Member("vote-nonadmin@test.com", "pw", "비방장"));
        tripMemberRepository.save(new TripMember(other, tripGroup, false));

        assertThatThrownBy(() -> voteService.updateAnonymous(tripGroup.getId(), vote.getId(), other.getId(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("여행 모임 방장만 접근할 수 있습니다.");
    }

    @Test
    @DisplayName("updateAnonymous - PENDING 상태가 아니면 예외 발생")
    void updateAnonymousNotPending() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));
        vote.updateStatus(VoteStatus.CONFIRMED);

        assertThatThrownBy(() -> voteService.updateAnonymous(tripGroup.getId(), vote.getId(), owner.getId(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 확정되었거나 만료된 투표입니다.");
    }
}
