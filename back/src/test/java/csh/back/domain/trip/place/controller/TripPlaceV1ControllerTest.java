package csh.back.domain.trip.place.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import csh.back.domain.member.entity.Member;
import csh.back.domain.member.repository.MemberRepository;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.group.support.WithMockLoginUser;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse;
import csh.back.domain.trip.place.service.TripPlaceService;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripPlaceV1ControllerTest {

    private static final String BASE_URL = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TripPlaceService tripPlaceService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TripGroupRepository tripGroupRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    private Member owner;
    private TripGroup tripGroup;

    @BeforeEach
    void setUp() {
        owner = memberRepository.findById(1L).orElseThrow();

        tripGroup = tripGroupRepository.save(TripGroup.builder()
                .owner(owner)
                .name("장소컨트롤러여행")
                .region("부산")
                .nights(1)
                .joinCode("PLACE-CTRL-JOIN")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 2))
                .build());

        tripMemberRepository.save(TripMember.builder()
                .member(owner)
                .tripGroup(tripGroup)
                .isAdmin(true)
                .build());
    }

    @Test
    @DisplayName("위시 장소 목록 조회 - 200")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void findWishPlaces() throws Exception {
        tripPlaceService.savePlace(tripGroup.getId(), "광안리", "관광", "부산 광안리", "kakao-find-1", "url", owner.getId());

        List<TripPlaceFindResponse> expected = tripPlaceService.findWishPlaces(tripGroup.getId(), owner.getId());

        ResultActions resultActions = mvc.perform(
                        get(BASE_URL + "/trips/" + tripGroup.getId() + "/wish-places")
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TripPlaceV1Controller.class))
                .andExpect(handler().methodName("findWishPlaces"))
                .andExpect(status().isOk());

        for (int i = 0; i < expected.size(); i++) {
            resultActions
                    .andExpect(jsonPath("$.data[%d].tripPlaceId".formatted(i)).value(expected.get(i).tripPlaceId()))
                    .andExpect(jsonPath("$.data[%d].name".formatted(i)).value(expected.get(i).name()))
                    .andExpect(jsonPath("$.data[%d].category".formatted(i)).value(expected.get(i).category()))
                    .andExpect(jsonPath("$.data[%d].address".formatted(i)).value(expected.get(i).address()))
                    .andExpect(jsonPath("$.data[%d].createdBy".formatted(i)).value(expected.get(i).createdBy()));
        }
    }

    @Test
    @DisplayName("위시 장소 저장 - 200")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void saveWishPlace() throws Exception {
        ResultActions resultActions = mvc.perform(
                        post(BASE_URL + "/trips/" + tripGroup.getId() + "/wish-places")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "name": "해운대",
                                            "category": "관광",
                                            "address": "부산 해운대",
                                            "kakaoPlaceId": "kakao-save-1",
                                            "kakaoMapUrl": "http://map/save-1"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TripPlaceV1Controller.class))
                .andExpect(handler().methodName("saveWishPlace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("해운대"))
                .andExpect(jsonPath("$.data.category").value("관광"))
                .andExpect(jsonPath("$.data.address").value("부산 해운대"));
    }

    @Test
    @DisplayName("위시 장소 중복 저장 - 409")
    @WithMockLoginUser(id = 1L, email = "admin@admin.com")
    void saveWishPlaceDuplicate() throws Exception {
        tripPlaceService.savePlace(tripGroup.getId(), "태종대", "관광", "부산 태종대", "kakao-dup-ctrl", "url", owner.getId());

        ResultActions resultActions = mvc.perform(
                        post(BASE_URL + "/trips/" + tripGroup.getId() + "/wish-places")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "name": "태종대2",
                                            "category": "관광",
                                            "address": "부산 태종대",
                                            "kakaoPlaceId": "kakao-dup-ctrl",
                                            "kakaoMapUrl": "http://map/dup"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TripPlaceV1Controller.class))
                .andExpect(handler().methodName("saveWishPlace"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 장소입니다"));
    }
}
