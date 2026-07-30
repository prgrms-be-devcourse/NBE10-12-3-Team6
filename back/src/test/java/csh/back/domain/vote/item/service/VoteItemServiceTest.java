package csh.back.domain.vote.item.service;

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
import csh.back.domain.vote.item.repository.VoteItemRepository;
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto;
import csh.back.domain.vote.user.repository.VoteUserRepository;
import csh.back.domain.vote.vote.entity.Vote;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class VoteItemServiceTest {

    @Autowired
    private VoteItemService voteItemService;

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

    private Member member;
    private TripGroup tripGroup;
    private TripMember tripMember;
    private Vote vote;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(new Member("vote-item-member@test.com", "pw", "투표항목유저"));

        tripGroup = tripGroupRepository.save(new TripGroup(
                member,
                "투표항목여행",
                "전주",
                1,
                "VOTE-ITEM-JOIN",
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 2)
        ));

        tripMember = tripMemberRepository.save(new TripMember(member, tripGroup, true));

        Timeline timeline = timelineRepository.save(Timeline.builder()
                .tripGroup(tripGroup)
                .dayNumber(1L)
                .startTime(LocalDateTime.of(2026, 12, 1, 9, 0))
                .endTime(LocalDateTime.of(2026, 12, 1, 10, 0))
                .build());

        vote = voteRepository.save(new Vote(
                tripGroup, timeline, tripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));
    }

    private TripPlace createPlace(String kakaoPlaceId) {
        return tripPlaceRepository.save(new TripPlace(
                tripGroup, "장소-" + kakaoPlaceId, "관광", "주소", kakaoPlaceId, "url", tripMember));
    }

    @Test
    @DisplayName("최초 투표 - VoteItem 신규 생성 + VoteUser 신규 생성(updateCount=0)")
    void firstVoteCreatesItemAndUser() {
        TripPlace place = createPlace("kakao-item-1");

        VoteUserSaveResponseDto response = voteItemService.saveVoteItem(
                tripGroup.getId(), member.getId(), vote.getId(), place.getId());

        assertThat(response.getMemberName()).isEqualTo(member.getName());
        assertThat(response.getPlace()).isEqualTo(place.getName());
        assertThat(response.getUpdateCount()).isEqualTo(0);

        assertThat(voteItemRepository.findByVoteIdAndTripPlaceId(vote.getId(), place.getId())).isPresent();
    }

    @Test
    @DisplayName("같은 유저가 다른 장소로 재투표 - 새 VoteItem 사용 + updateCount 증가")
    void revoteUpdatesTripPlaceAndIncreasesUpdateCount() {
        TripPlace firstPlace = createPlace("kakao-item-2");
        TripPlace secondPlace = createPlace("kakao-item-3");

        voteItemService.saveVoteItem(tripGroup.getId(), member.getId(), vote.getId(), firstPlace.getId());
        VoteUserSaveResponseDto response = voteItemService.saveVoteItem(
                tripGroup.getId(), member.getId(), vote.getId(), secondPlace.getId());

        assertThat(response.getPlace()).isEqualTo(secondPlace.getName());
        assertThat(response.getUpdateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateCount가 2인 상태에서 추가 변경 시도 시 예외 발생")
    void exceedingUpdateLimitThrows() {
        TripPlace place1 = createPlace("kakao-item-4");
        TripPlace place2 = createPlace("kakao-item-5");
        TripPlace place3 = createPlace("kakao-item-6");
        TripPlace place4 = createPlace("kakao-item-7");

        voteItemService.saveVoteItem(tripGroup.getId(), member.getId(), vote.getId(), place1.getId());
        voteItemService.saveVoteItem(tripGroup.getId(), member.getId(), vote.getId(), place2.getId());
        voteItemService.saveVoteItem(tripGroup.getId(), member.getId(), vote.getId(), place3.getId());

        assertThatThrownBy(() ->
                voteItemService.saveVoteItem(tripGroup.getId(), member.getId(), vote.getId(), place4.getId())
        ).isInstanceOf(RuntimeException.class);
    }
}
