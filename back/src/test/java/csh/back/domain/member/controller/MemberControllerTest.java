package csh.back.domain.member.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class MemberControllerTest {

    @Autowired
    MockMvc mvc;

    private static final String BASE_URL = "/api/v1/auth";

    @Test
    @DisplayName("회원가입 - 정상")
    void t1() throws Exception {
        ResultActions result = mvc.perform(
                post(BASE_URL + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "newuser@test.com",
                                    "password": "password123",
                                    "name": "테스트유저"
                                }
                                """)
        ).andDo(print());

        result
                .andExpect(handler().handlerType(MemberController.class))
                .andExpect(handler().methodName("signUp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.email").value("newuser@test.com"))
                .andExpect(jsonPath("$.data.name").value("테스트유저"));
    }

    @Test
    @DisplayName("회원가입 - 이미 사용 중인 이메일")
    void t2() throws Exception {
        ResultActions result = mvc.perform(
                post(BASE_URL + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "admin@admin.com",
                                    "password": "1234",
                                    "name": "어드민"
                                }
                                """)
        ).andDo(print());

        result
                .andExpect(handler().handlerType(MemberController.class))
                .andExpect(handler().methodName("signUp"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    @DisplayName("회원가입 - 이메일 형식 오류")
    void t3() throws Exception {
        ResultActions result = mvc.perform(
                post(BASE_URL + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "invalid-email",
                                    "password": "password123",
                                    "name": "테스트유저"
                                }
                                """)
        ).andDo(print());

        result
                .andExpect(handler().handlerType(MemberController.class))
                .andExpect(handler().methodName("signUp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    @DisplayName("회원가입 - 필수 필드 누락 (name)")
    void t4() throws Exception {
        ResultActions result = mvc.perform(
                post(BASE_URL + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@test.com",
                                    "password": "password123"
                                }
                                """)
        ).andDo(print());

        result
                .andExpect(handler().handlerType(MemberController.class))
                .andExpect(handler().methodName("signUp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    @DisplayName("로그인 - 정상")
    void t5() throws Exception {
        ResultActions result = mvc.perform(
                post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "admin@admin.com",
                                    "password": "1234"
                                }
                                """)
        ).andDo(print());

        result
                .andExpect(handler().handlerType(MemberController.class))
                .andExpect(handler().methodName("login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.email").value("admin@admin.com"))
                .andExpect(jsonPath("$.data.name").value("admin"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    @DisplayName("로그인 - 존재하지 않는 이메일")
    void t6() throws Exception {
        ResultActions result = mvc.perform(
                post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "notexist@test.com",
                                    "password": "1234"
                                }
                                """)
        ).andDo(print());

        result
                .andExpect(handler().handlerType(MemberController.class))
                .andExpect(handler().methodName("login"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("로그인 - 비밀번호 불일치")
    void t7() throws Exception {
        ResultActions result = mvc.perform(
                post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "admin@admin.com",
                                    "password": "wrongpassword"
                                }
                                """)
        ).andDo(print());

        result
                .andExpect(handler().handlerType(MemberController.class))
                .andExpect(handler().methodName("login"))
                .andExpect(status().isInternalServerError());
    }
}