package csh.back.domain.vote.item.controller;

import csh.back.domain.member.entity.Member;
import csh.back.domain.member.repository.MemberRepository;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.group.support.WithMockLoginUser;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.place.repository.TripPlaceRepository;
import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.domain.trip.timeline.repository.TimelineRepository;
import csh.back.domain.trip.chat.repository.TripChatMessageRepository;
import csh.back.domain.vote.item.repository.VoteItemRepository;
import csh.back.domain.vote.user.repository.VoteUserRepository;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.repository.VoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// REQUIRES_NEW로 별도 물리 트랜잭션을 여는 VoteItemInsertExecutor는 클래스 레벨 @Transactional로 감싼
// 테스트에서 setUp 데이터가 커밋되지 않아 FK 위반이 나므로, 실제 커밋 후 @AfterEach로 정리하는 방식을 쓴다.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class VoteItemV1ControllerTest {

    @Autowired
    private MockMvc mvc;

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
    private TripPlaceRepository tripPlaceRepository;

    @Autowired
    private VoteItemRepository voteItemRepository;

    @Autowired
    private VoteUserRepository voteUserRepository;

    @Autowired
    private TripChatMessageRepository tripChatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Member member;
    private TripGroup tripGroup;
    private TripMember tripMember;
    private Timeline timeline;
    private Vote vote;

    @BeforeEach
    void setUp() {
        member = memberRepository.findById(1L).orElseThrow();

        tripGroup = tripGroupRepository.save(new TripGroup(
                member,
                "투표항목컨트롤러여행",
                "여수",
                1,
                "VOTE-ITEM-CTRL-JOIN",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 2)
        ));

        tripMember = tripMemberRepository.save(new TripMember(member, tripGroup, true));

        timeline = timelineRepository.save(Timeline.builder()
                .tripGroup(tripGroup)
                .dayNumber(1L)
                .startTime(LocalDateTime.of(2027, 1, 1, 9, 0))
                .endTime(LocalDateTime.of(2027, 1, 1, 10, 0))
                .build());

        vote = voteRepository.save(new Vote(
                tripGroup, timeline, tripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));
    }

    // deleteAll()은 TestInitData가 앱 기동 시 한 번만 커밋해두는 공유 시드(Member/TripGroup 1~3)까지
    // 지워버려 다른 테스트 클래스를 깨뜨리므로, 이 테스트가 직접 만든 행만 참조로 골라 지운다.
    // member는 공유 시드(id=1)라 삭제하지 않는다.
    @AfterEach
    void tearDown() {
        voteItemRepository.findAllByVoteIdWithTripPlace(vote.getId())
                .forEach(voteItem -> {
                    voteUserRepository.deleteAll(voteUserRepository.findByVoteItemId(voteItem.getId()));
                    voteItemRepository.delete(voteItem);
                });
        voteRepository.delete(vote);
        tripPlaceRepository.deleteAll(tripPlaceRepository.findAllByTripGroupId(tripGroup.getId()));
        timelineRepository.delete(timeline);
        tripMemberRepository.delete(tripMember);
        tripChatMessageRepository.deleteAllByTripGroupId(tripGroup.getId());
        // TripGroup은 @SQLDelete로 소프트 삭제(UPDATE)되므로 repository.delete()로는 row가 실제로 지워지지 않아
        // join_code unique 인덱스를 계속 점유한다. JdbcTemplate으로 JPA 세션과 무관하게 물리 삭제한다.
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("DELETE FROM trip_groups WHERE id = ?", tripGroup.getId()));
    }

    private TripPlace createPlace(String kakaoPlaceId) {
        return tripPlaceRepository.save(new TripPlace(
                tripGroup, "장소-" + kakaoPlaceId, "관광", "주소", kakaoPlaceId, "url", tripMember));
    }

    @Test
    @DisplayName("투표 참여 - 201")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void saveVote() throws Exception {
        TripPlace place = createPlace("kakao-item-ctrl-1");

        mvc.perform(
                        post("/api/v1/trips/" + tripGroup.getId() + "/votes/" + vote.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "tripPlaceId": %d
                                        }
                                        """.formatted(place.getId()))
                )
                .andDo(print())
                .andExpect(handler().handlerType(VoteItemV1Controller.class))
                .andExpect(handler().methodName("saveVote"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberName").value(member.getName()))
                .andExpect(jsonPath("$.data.place").value(place.getName()))
                .andExpect(jsonPath("$.data.updateCount").value(0));
    }

    @Test
    @DisplayName("같은 유저가 같은 투표에 다른 장소로 재요청 - updateCount 증가 확인")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void saveVoteAgainIncreasesUpdateCount() throws Exception {
        TripPlace firstPlace = createPlace("kakao-item-ctrl-2");
        TripPlace secondPlace = createPlace("kakao-item-ctrl-3");

        mvc.perform(
                        post("/api/v1/trips/" + tripGroup.getId() + "/votes/" + vote.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "tripPlaceId": %d
                                        }
                                        """.formatted(firstPlace.getId()))
                )
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.updateCount").value(0));

        mvc.perform(
                        post("/api/v1/trips/" + tripGroup.getId() + "/votes/" + vote.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "tripPlaceId": %d
                                        }
                                        """.formatted(secondPlace.getId()))
                )
                .andDo(print())
                .andExpect(handler().handlerType(VoteItemV1Controller.class))
                .andExpect(handler().methodName("saveVote"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.place").value(secondPlace.getName()))
                .andExpect(jsonPath("$.data.updateCount").value(1));
    }
}
