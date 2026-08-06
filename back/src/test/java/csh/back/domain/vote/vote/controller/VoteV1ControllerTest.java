package csh.back.domain.vote.vote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import csh.back.domain.vote.item.entity.VoteItem;
import csh.back.domain.vote.item.repository.VoteItemRepository;
import csh.back.domain.vote.user.entity.VoteUser;
import csh.back.domain.vote.user.repository.VoteUserRepository;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoteV1ControllerTest {

    private static final String BASE_URL = "/api/v1";

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
        owner = memberRepository.findById(1L).orElseThrow();

        tripGroup = tripGroupRepository.save(new TripGroup(
                owner,
                "투표컨트롤러여행",
                "강릉",
                1,
                "VOTE-CTRL-JOIN",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2)
        ));

        ownerTripMember = tripMemberRepository.save(new TripMember(owner, tripGroup, true));
    }

    private Timeline createTimeline() {
        return timelineRepository.save(Timeline.builder()
                .tripGroup(tripGroup)
                .dayNumber(1L)
                .startTime(LocalDateTime.of(2026, 11, 1, 9, 0))
                .endTime(LocalDateTime.of(2026, 11, 1, 10, 0))
                .build());
    }

    private Vote createVote(Timeline timeline) {
        return voteRepository.save(new Vote(
                tripGroup, timeline, ownerTripMember, tripGroup.getStartDate().minusDays(1).atStartOfDay()));
    }

    @Test
    @DisplayName("투표 목록 조회 - 200")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void findVoteList() throws Exception {
        Timeline timeline = createTimeline();
        createVote(timeline);

        mvc.perform(get(BASE_URL + "/trips/" + tripGroup.getId() + "/votes"))
                .andDo(print())
                .andExpect(handler().handlerType(VoteV1Controller.class))
                .andExpect(handler().methodName("findVoteList"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("투표 생성 - 201")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void createVote() throws Exception {
        Timeline timeline = createTimeline();

        ResultActions resultActions = mvc.perform(
                        post(BASE_URL + "/trips/" + tripGroup.getId() + "/votes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "timelineId": %d
                                        }
                                        """.formatted(timeline.getId()))
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(VoteV1Controller.class))
                .andExpect(handler().methodName("createVote"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.voteId").exists());

        String body = resultActions.andReturn().getResponse().getContentAsString();
        Long voteId = new ObjectMapper().readTree(body).get("data").get("voteId").asLong();
        Vote saved = voteRepository.findById(voteId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getTimeline().getId()).isEqualTo(timeline.getId());
    }

    @Test
    @DisplayName("투표 항목 및 투표 수 조회 - 200")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void findVoteItemAndCount() throws Exception {
        Timeline timeline = createTimeline();
        Vote vote = createVote(timeline);
        TripPlace place = tripPlaceRepository.save(new TripPlace(
                tripGroup, "경포대", "관광", "강릉 경포대", "kakao-vote-ctrl-1", "url", ownerTripMember));
        VoteItem voteItem = voteItemRepository.save(new VoteItem(vote, place));
        voteUserRepository.save(new VoteUser(vote, voteItem, ownerTripMember, 0));

        mvc.perform(get(BASE_URL + "/trips/" + tripGroup.getId() + "/votes/" + vote.getId() + "/count"))
                .andDo(print())
                .andExpect(handler().handlerType(VoteV1Controller.class))
                .andExpect(handler().methodName("findVoteItemAndCount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voteResults[0].tripPlaceId").value(place.getId()))
                .andExpect(jsonPath("$.data.voteResults[0].count").value(1))
                .andExpect(jsonPath("$.data.voteResults[0].isVoted").value(true))
                .andExpect(jsonPath("$.data.updateCount").value(0));
    }

    @Test
    @DisplayName("특정 장소 투표 참여자 조회 - 200")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void findVoteUserThisPlace() throws Exception {
        Timeline timeline = createTimeline();
        Vote vote = createVote(timeline);
        TripPlace place = tripPlaceRepository.save(new TripPlace(
                tripGroup, "오죽헌", "관광", "강릉 오죽헌", "kakao-vote-ctrl-2", "url", ownerTripMember));
        VoteItem voteItem = voteItemRepository.save(new VoteItem(vote, place));
        voteUserRepository.save(new VoteUser(vote, voteItem, ownerTripMember, 0));

        mvc.perform(get(BASE_URL + "/trips/" + tripGroup.getId() + "/votes/" + vote.getId()
                        + "/places/" + place.getId()))
                .andDo(print())
                .andExpect(handler().handlerType(VoteV1Controller.class))
                .andExpect(handler().methodName("findVoteUserThisPlace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value(owner.getName()));
    }

    @Test
    @DisplayName("투표 결과 장소 확정 - 200")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void confirmVote() throws Exception {
        Timeline timeline = createTimeline();
        Vote vote = createVote(timeline);
        TripPlace place = tripPlaceRepository.save(new TripPlace(
                tripGroup, "주문진", "관광", "강릉 주문진", "kakao-vote-ctrl-3", "url", ownerTripMember));
        VoteItem voteItem = voteItemRepository.save(new VoteItem(vote, place));
        voteUserRepository.save(new VoteUser(vote, voteItem, ownerTripMember, 0));

        mvc.perform(patch(BASE_URL + "/trips/" + tripGroup.getId() + "/votes/" + vote.getId() + "/confirm"))
                .andDo(print())
                .andExpect(handler().handlerType(VoteV1Controller.class))
                .andExpect(handler().methodName("confirmVote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedPlaceId").value(place.getId()))
                .andExpect(jsonPath("$.data.isTie").value(false));
    }

    @Test
    @DisplayName("투표 익명/실명 개별 변경 - 200")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void updateAnonymous() throws Exception {
        Timeline timeline = createTimeline();
        Vote vote = createVote(timeline);

        mvc.perform(
                        patch(BASE_URL + "/trips/" + tripGroup.getId() + "/votes/" + vote.getId() + "/anonymous")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "isAnonymous": false
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(handler().handlerType(VoteV1Controller.class))
                .andExpect(handler().methodName("updateAnonymous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        Vote updated = voteRepository.findById(vote.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.isAnonymous()).isFalse();
    }
}
