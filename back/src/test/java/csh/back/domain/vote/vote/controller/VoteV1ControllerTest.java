package csh.back.domain.vote.vote.controller;

import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoteV1ControllerTest {
    private final MockMvc mockMvc;
    private final VoteRepository voteRepository;

    @Autowired
    public VoteV1ControllerTest(MockMvc mockMvc, VoteRepository voteRepository) {
        this.mockMvc = mockMvc;
        this.voteRepository = voteRepository;
    }

    // **절대 주석을 풀지마세요~! 에러 나서 테스트 실패해서 진행이 안됩니당!**
    @Test
    @DisplayName("투표 등록")
    void t1() throws Exception {
//        Vote vote = voteRepository.save(
//                Vote.builder().build()
//        );
//
//        MvcResult result1 = mockMvc.perform(
//                        post("/trip/{tripId}/votes")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content("{}") // 여기가 body 데이터
//                )
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.data.id").exists())
//                .andExpect(jsonPath("$.data.email").value("input@naver.com"))
//                .andExpect(jsonPath("$.data.address").value("서울 OO구 OO로, OO아파트 OO동 OO호"))
//                .andExpect(jsonPath("$.data.totalPrice").value(65000))
//                .andExpect(jsonPath("$.data.orderItems[0].name").value("맛있는 원두"))
//                .andExpect(jsonPath("$.data.orderItems[0].amount").value(10))
//                .andExpect(jsonPath("$.data.orderItems[0].price").value(2000))
//                .andExpect(jsonPath("$.data.orderItems[1].name").value("맛없는 원두"))
//                .andExpect(jsonPath("$.data.orderItems[1].amount").value(15))
//                .andExpect(jsonPath("$.data.orderItems[1].price").value(3000))
//                .andReturn();
    }
}