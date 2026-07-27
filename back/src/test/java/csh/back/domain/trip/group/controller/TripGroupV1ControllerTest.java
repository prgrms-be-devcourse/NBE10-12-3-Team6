package csh.back.domain.trip.group.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.trip.group.dto.response.TripGroupDetailResponse;
import csh.back.domain.trip.group.dto.response.TripGroupResponse;
import csh.back.domain.trip.group.service.TripGroupService;
import csh.back.domain.trip.group.support.WithMockLoginUser;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest
//테스트를 쉽게 하기 위해 제공하는 에노테이션 ->컨트롤러 계층을 별도로 실행하지 않고도 요청과 응답을 모킹하여 테스트 가능함
/*
 * addFilters : spring security와 같은 필터를 추가할지에 대한 여부, 기본값 true
 * */
@AutoConfigureMockMvc // 톰캣만 가짜 → MockMvc로 HTTP 요청 흉내
public class TripGroupV1ControllerTest {
	@Autowired
	MockMvc mvc;  // 실제 서버 안 띄우고 요청/응답 테스트성

	@Autowired
	private TripGroupService tripGroupService;

	private String BASE_URL = "/api/v1";

	@WithMockLoginUser
		// jwt 인증 없이 테스트 진행하고 싶으면 -> SecurityContext 직접 주입
	void t1() throws Exception {
		ResultActions resultActions = mvc
				.perform(
						get(BASE_URL + "/trips")
				)
				.andDo(print());

		AuthFilterDto member = (AuthFilterDto) SecurityContextHolder.getContext()
				.getAuthentication()
				.getPrincipal();

		List<TripGroupResponse> tripGroups = tripGroupService.getGroups(member.id(), "", "");

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("getAllGroups"))
				.andExpect(status().isOk());

		for (int i = 0; i < tripGroups.size(); i++) {
			TripGroupResponse trip = tripGroups.get(i);
			resultActions.andExpect(jsonPath("$.data[%d].ownerId".formatted(i)).value(trip.ownerId()));
		}
	}

	@Test
	@DisplayName("user 정보 없이 모임방 조회")
	void t2() throws Exception {
		ResultActions resultActions = mvc
				.perform(
						get(BASE_URL + "/trips")
				)
				.andDo(print());

		resultActions
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("모임방 생성")
	@WithMockLoginUser
	void t3() throws Exception {
		//Mock user로부터 등록된 security에서 user 정보 꺼내기
		AuthFilterDto owner = (AuthFilterDto) SecurityContextHolder.getContext()
				.getAuthentication()
				.getPrincipal();

		ResultActions resultActions = mvc
				.perform(
						post(BASE_URL + "/trips")
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{
											"name": "test travel",
											"region" : "test region",
											"startDate" : "2026-07-01",
											"nights": 4
										}
										""")
				).andDo(print());

		String body = resultActions.andReturn().getResponse().getContentAsString();
		Long id = new ObjectMapper().readTree(body).get("data").get("id").asLong();

		TripGroupDetailResponse tripGroup = tripGroupService.getGroupDetail(id, owner.id());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("saveGroup"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(tripGroup.id()))
				.andExpect(jsonPath("$.data.name").value(tripGroup.name()))
				.andExpect(jsonPath("$.data.ownerId").value(tripGroup.ownerId()))
				.andExpect(jsonPath("$.data.region").value(tripGroup.region()))
				.andExpect(jsonPath("$.data.joinCode").value(tripGroup.joinCode()))
				.andExpect(jsonPath("$.data.nights").value(tripGroup.nights()))
				.andExpect(jsonPath("$.data.startDate").value(Matchers.startsWith(tripGroup.startDate().toString())))
				.andExpect(jsonPath("$.data.endDate").value(Matchers.startsWith(tripGroup.endDate().toString())));
	}

	@Test
	@DisplayName("모임방 생성 with 존재 하지 않는 사용자")
	@WithMockLoginUser(id = 10L, email = "excep@excep.com")
	void t4() throws Exception {
		ResultActions resultActions = mvc
				.perform(
						post(BASE_URL + "/trips")
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{
											"name": "test travel",
											"region" : "test region",
											"startDate" : "2026-07-01",
											"nights": 4
										}
										""")
				).andDo(print());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("saveGroup"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("존재하지 않는 유저".stripIndent().trim()));
	}

	@Test
	@DisplayName("모임방 생성 with request 필드 중 하나가 전달되지 않은 경우")
	@WithMockLoginUser()
	void t5() throws Exception {
		ResultActions resultActions = mvc
				.perform(
						post(BASE_URL + "/trips")
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{
											"name": "test travel",
											"region" : "test region",
											"startDate" : "2026-07-01"
										}
										""")
				).andDo(print());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("saveGroup"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("nights: must not be null".stripIndent().trim()));
	}

	@Test
	@DisplayName("모임방 상세 조회")
	@WithMockLoginUser()
	void t6() throws Exception {
		Long id = 1L;

		AuthFilterDto owner = (AuthFilterDto) SecurityContextHolder.getContext()
				.getAuthentication()
				.getPrincipal();

		ResultActions resultActions = mvc
				.perform(
						get(BASE_URL + "/trips/" + id)
				)
				.andDo(print());

		TripGroupDetailResponse tripGroup = tripGroupService.getGroupDetail(id, owner.id());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("getGroupDetail"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(tripGroup.id()))
				.andExpect(jsonPath("$.data.name").value(tripGroup.name()))
				.andExpect(jsonPath("$.data.ownerId").value(tripGroup.ownerId()))
				.andExpect(jsonPath("$.data.region").value(tripGroup.region()))
				.andExpect(jsonPath("$.data.joinCode").value(tripGroup.joinCode()))
				.andExpect(jsonPath("$.data.nights").value(tripGroup.nights()))
				.andExpect(jsonPath("$.data.startDate").value(Matchers.startsWith(tripGroup.startDate().toString())))
				.andExpect(jsonPath("$.data.endDate").value(Matchers.startsWith(tripGroup.endDate().toString())));

		for (int i = 0; i < tripGroup.members().size(); i++) {
			resultActions
					.andExpect(jsonPath("$.data.members[%d].memberId".formatted(i)).value(tripGroup.members().get(i).memberId()))
					.andExpect(jsonPath("$.data.members[%d].name".formatted(i)).value(tripGroup.members().get(i).name()));
		}
	}

	@Test
	@DisplayName("모임방 상세 조회 with 참여자가 아닌 경우")
	@WithMockLoginUser(id = 2L, email = "member2@admin.com")
	void t7() throws Exception {
		Long id = 1L;

		ResultActions resultActions = mvc
				.perform(
						get(BASE_URL + "/trips/" + id)
				)
				.andDo(print());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("getGroupDetail"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("해당 모임의 멤버가 아닙니다."));
	}

	@Test
	@DisplayName("모임방 상세 조회 with 존재하지 않는 모임")
	@WithMockLoginUser()
	void t8() throws Exception {
		Long id = 10L;

		ResultActions resultActions = mvc
				.perform(
						get(BASE_URL + "/trips/" + id)
				)
				.andDo(print());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("getGroupDetail"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("존재하지 않는 모임입니다."));
	}

	@Test
	@DisplayName("모임방 상세 조회 수정")
	@WithMockLoginUser()
	void t9() throws Exception {
		Long id = 1L;

		AuthFilterDto owner = (AuthFilterDto) SecurityContextHolder.getContext()
				.getAuthentication()
				.getPrincipal();

		ResultActions resultActions = mvc
				.perform(
						patch(BASE_URL + "/trips/" + id)
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{
										    "name": "테스트 제목 수정"
										}
										""")
				)
				.andDo(print());

		TripGroupDetailResponse tripGroup = tripGroupService.getGroupDetail(id, owner.id());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("modifyGroupName"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(tripGroup.id()))
				.andExpect(jsonPath("$.data.name").value(tripGroup.name()))
				.andExpect(jsonPath("$.data.ownerId").value(tripGroup.ownerId()))
				.andExpect(jsonPath("$.data.region").value(tripGroup.region()))
				.andExpect(jsonPath("$.data.joinCode").value(tripGroup.joinCode()))
				.andExpect(jsonPath("$.data.nights").value(tripGroup.nights()))
				.andExpect(jsonPath("$.data.startDate").value(Matchers.startsWith(tripGroup.startDate().toString())))
				.andExpect(jsonPath("$.data.endDate").value(Matchers.startsWith(tripGroup.endDate().toString())));
	}

	@Test
	@DisplayName("모임방 상세 조회 수정 with 모임 소유자가 아닌 사용자 접근")
	@WithMockLoginUser(id = 2L, email = "memer2@admin.com")
	void t10() throws Exception {
		Long id = 1L;

		ResultActions resultActions = mvc
				.perform(
						patch(BASE_URL + "/trips/" + id)
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{
										    "name": "테스트 제목 수정"
										}
										""")
				)
				.andDo(print());

		resultActions
				.andExpect(handler().handlerType(TripGroupV1Controller.class))
				.andExpect(handler().methodName("modifyGroupName"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("해당 모임의 소유자가 아닙니다."));
	}
}
