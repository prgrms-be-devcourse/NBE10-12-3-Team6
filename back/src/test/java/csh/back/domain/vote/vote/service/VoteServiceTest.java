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
        owner = memberRepository.save(Member.builder()
                .email("vote-owner@test.com").password("pw").name("투표주인장").build());

        tripGroup = tripGroupRepository.save(TripGroup.builder()
                .owner(owner)
                .name("투표테스트여행")
                .region("서울")
                .nights(1)
                .joinCode("VOTE-JOIN-1")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 2))
                .build());

        ownerTripMember = tripMemberRepository.save(TripMember.builder()
                .member(owner).tripGroup(tripGroup).isAdmin(true).build());
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
        return tripPlaceRepository.save(TripPlace.builder()
                .tripGroup(tripGroup)
                .name("장소-" + kakaoPlaceId)
                .category("관광")
                .address("주소")
                .kakaoPlaceId(kakaoPlaceId)
                .kakaoMapUrl("url")
                .createdBy(ownerTripMember)
                .build());
    }

    @Test
    @DisplayName("findVoteList - 일자별 그룹핑, 타임라인 없는 날은 빈 리스트")
    void findVoteList() {
        Timeline day1Timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0));
        Vote vote = voteRepository.save(Vote.builder()
                .tripGroup(tripGroup)
                .timeline(day1Timeline)
                .tripMember(ownerTripMember)
                .expireTime(tripGroup.getStartDate().minusDays(1).atStartOfDay())
                .build());

        List<VoteFindListResponse> result = voteService.findVoteList(tripGroup.getId(), owner.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(tripGroup.getStartDate());
        assertThat(result.get(0).timeLines()).hasSize(1);
        assertThat(result.get(0).timeLines().get(0).voteId()).isEqualTo(vote.getId());
        assertThat(result.get(0).timeLines().get(0).voteStatus()).isEqualTo(VoteStatus.PENDING.getNickname());

        assertThat(result.get(1).date()).isEqualTo(tripGroup.getStartDate().plusDays(1));
        assertThat(result.get(1).timeLines()).isEmpty();
    }

    @Test
    @DisplayName("wrapperCreateVote/createVote - 투표 생성 후 저장 값 확인")
    void createVote() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0),
                LocalDateTime.of(2026, 10, 1, 10, 0));

        VoteCreateResponse response = voteService.wrapperCreateVote(tripGroup.getId(), owner.getId(), timeline.getId());

        Vote saved = voteRepository.findById(response.voteId()).orElseThrow();
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
        Vote vote = voteRepository.save(Vote.builder()
                .tripGroup(tripGroup).timeline(timeline).tripMember(ownerTripMember)
                .expireTime(tripGroup.getStartDate().minusDays(1).atStartOfDay()).build());

        TripPlace place1 = createPlace("kakao-count-1");
        TripPlace place2 = createPlace("kakao-count-2");

        VoteItem voteItem1 = voteItemRepository.save(VoteItem.builder().vote(vote).tripPlace(place1).build());
        VoteItem voteItem2 = voteItemRepository.save(VoteItem.builder().vote(vote).tripPlace(place2).build());

        Member other = memberRepository.save(Member.builder()
                .email("vote-other@test.com").password("pw").name("다른투표자").build());
        TripMember otherTripMember = tripMemberRepository.save(TripMember.builder()
                .member(other).tripGroup(tripGroup).isAdmin(false).build());

        voteUserRepository.save(VoteUser.builder()
                .vote(vote).voteItem(voteItem1).tripMember(ownerTripMember).updateCount(0).build());
        voteUserRepository.save(VoteUser.builder()
                .vote(vote).voteItem(voteItem2).tripMember(otherTripMember).updateCount(0).build());

        VoteFindWithUpdateCountResponse response =
                voteService.findVoteItemAndCount(tripGroup.getId(), vote.getId(), owner.getId());

        assertThat(response.updateCount()).isEqualTo(0);
        assertThat(response.voteStatus()).isEqualTo(VoteStatus.PENDING.getNickname());
        assertThat(response.voteResults()).hasSize(2);

        response.voteResults().forEach(voteFindResponse -> {
            if (voteFindResponse.tripPlaceId().equals(place1.getId())) {
                assertThat(voteFindResponse.count()).isEqualTo(1L);
                assertThat(voteFindResponse.isVoted()).isTrue();
            } else if (voteFindResponse.tripPlaceId().equals(place2.getId())) {
                assertThat(voteFindResponse.count()).isEqualTo(1L);
                assertThat(voteFindResponse.isVoted()).isFalse();
            }
        });
    }

    @Test
    @DisplayName("voteConfirm - 확정 처리 후 Vote.status가 CONFIRMED로 변경")
    void voteConfirm() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(Vote.builder()
                .tripGroup(tripGroup).timeline(timeline).tripMember(ownerTripMember)
                .expireTime(tripGroup.getStartDate().minusDays(1).atStartOfDay()).build());
        TripPlace place = createPlace("kakao-confirm-1");
        VoteItem voteItem = voteItemRepository.save(VoteItem.builder().vote(vote).tripPlace(place).build());

        VoteTimelineResponse response = voteService.voteConfirm(voteItem.getId(), vote.getId());

        assertThat(response.confirmPlaceId()).isEqualTo(place.getId());
        assertThat(response.timeline().getId()).isEqualTo(timeline.getId());

        Vote confirmed = voteRepository.findById(vote.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
    }

    @Test
    @DisplayName("expireVote - 상태가 EXPIRED로 변경")
    void expireVote() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(Vote.builder()
                .tripGroup(tripGroup).timeline(timeline).tripMember(ownerTripMember)
                .expireTime(tripGroup.getStartDate().minusDays(1).atStartOfDay()).build());

        voteService.expireVote(vote.getId());

        Vote expired = voteRepository.findById(vote.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(VoteStatus.EXPIRED);
    }

    @Test
    @DisplayName("findUserVoteThisPlace - 특정 장소에 투표한 유저 목록 반환")
    void findUserVoteThisPlace() {
        Timeline timeline = createTimeline(1L,
                LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 10, 1, 10, 0));
        Vote vote = voteRepository.save(Vote.builder()
                .tripGroup(tripGroup).timeline(timeline).tripMember(ownerTripMember)
                .expireTime(tripGroup.getStartDate().minusDays(1).atStartOfDay()).build());
        TripPlace place = createPlace("kakao-users-1");
        VoteItem voteItem = voteItemRepository.save(VoteItem.builder().vote(vote).tripPlace(place).build());

        Member other = memberRepository.save(Member.builder()
                .email("vote-user2@test.com").password("pw").name("투표자2").build());
        TripMember otherTripMember = tripMemberRepository.save(TripMember.builder()
                .member(other).tripGroup(tripGroup).isAdmin(false).build());

        voteUserRepository.save(VoteUser.builder()
                .vote(vote).voteItem(voteItem).tripMember(ownerTripMember).updateCount(0).build());
        voteUserRepository.save(VoteUser.builder()
                .vote(vote).voteItem(voteItem).tripMember(otherTripMember).updateCount(0).build());

        List<VoteFindUserResponse> responses =
                voteService.findUserVoteThisPlace(tripGroup.getId(), vote.getId(), place.getId(), owner.getId());

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(VoteFindUserResponse::name)
                .containsExactlyInAnyOrder(owner.getName(), other.getName());
    }
}
