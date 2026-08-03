package csh.back.domain.trip.post.controller

import tools.jackson.databind.ObjectMapper
import csh.back.domain.trip.group.support.WithMockLoginUser
import csh.back.domain.trip.post.dto.request.UpdatePostRequest
import csh.back.domain.trip.post.dto.response.PostCursorResponse
import csh.back.domain.trip.post.dto.response.PostResponse
import csh.back.domain.trip.post.dto.response.PostTimelineResponse
import csh.back.domain.trip.post.dto.response.PostsDailyResponse
import csh.back.domain.trip.post.like.service.PostLikeService
import csh.back.domain.trip.post.service.PostService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

@WebMvcTest(PostV1Controller::class)
@AutoConfigureMockMvc(addFilters = false)
class PostV1ControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var postService: PostService

    @MockitoBean
    lateinit var postLikeService: PostLikeService

    companion object {
        private const val BASE_URL = "/api/v1/trips/{tripGroupId}/posts"
        private const val TRIP_GROUP_ID = 1L
        private const val MEMBER_ID = 1L
        private const val POST_ID = 10L
        private const val TIMELINE_ID = 100L
    }

    @Test
    @DisplayName("게시글 생성")
    @WithMockLoginUser(id = MEMBER_ID)
    fun createPost() {
        val requestPart = MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
            {
                "timelineId": $TIMELINE_ID,
                "content": "부산 여행 시작!"
            }
            """.trimIndent().toByteArray()
        )

        val imagePart = MockMultipartFile(
            "image",
            "test-image.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "fake-image-data".toByteArray()
        )

        val response = PostResponse(
            id = POST_ID,
            timelineId = TIMELINE_ID,
            type = "IMAGE",
            originalFilename = "test-image.jpg",
            contentUrl = "https://example.com/test-image.jpg",
            content = "부산 여행 시작!",
            likeCount = 0L
        )

        `when`(
            postService.create(
                eq(TRIP_GROUP_ID),
                eq(MEMBER_ID),
                eq(TIMELINE_ID),
                any()
            )
        ).thenReturn(response)

        mvc.perform(
            multipart(BASE_URL, TRIP_GROUP_ID)
                .file(requestPart)
                .file(imagePart)
                .contentType(MediaType.MULTIPART_FORM_DATA)
        )
            .andDo(print())
            .andExpect(handler().handlerType(PostV1Controller::class.java))
            .andExpect(handler().methodName("create"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(POST_ID))
            .andExpect(jsonPath("$.timelineId").value(TIMELINE_ID))
            .andExpect(jsonPath("$.type").value("IMAGE"))
            .andExpect(jsonPath("$.originalFilename").value("test-image.jpg"))
            .andExpect(jsonPath("$.contentUrl").value("https://example.com/test-image.jpg"))
            .andExpect(jsonPath("$.normalContentUrl").value("https://example.com/test-image.jpg"))
            .andExpect(jsonPath("$.dataSaverContentUrl").value("https://example.com/test-image.jpg"))
            .andExpect(jsonPath("$.content").value("부산 여행 시작!"))
            .andExpect(jsonPath("$.likeCount").value(0))

        verify(postService).create(
            eq(TRIP_GROUP_ID),
            eq(MEMBER_ID),
            eq(TIMELINE_ID),
            any()
        )
    }

    @Test
    @DisplayName("게시글 단건 조회")
    @WithMockLoginUser(id = MEMBER_ID)
    fun getPost() {
        val response = PostResponse(
            id = POST_ID,
            timelineId = TIMELINE_ID,
            type = "IMAGE",
            originalFilename = "test-image.jpg",
            contentUrl = "https://example.com/test-image.jpg",
            content = "부산 여행",
            likeCount = 3L
        )

        `when`(
            postService.getPost(TRIP_GROUP_ID, POST_ID)
        ).thenReturn(response)

        mvc.perform(
            get("$BASE_URL/{postId}", TRIP_GROUP_ID, POST_ID)
        )
            .andDo(print())
            .andExpect(handler().handlerType(PostV1Controller::class.java))
            .andExpect(handler().methodName("getPost"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(POST_ID))
            .andExpect(jsonPath("$.timelineId").value(TIMELINE_ID))
            .andExpect(jsonPath("$.type").value("IMAGE"))
            .andExpect(jsonPath("$.originalFilename").value("test-image.jpg"))
            .andExpect(jsonPath("$.contentUrl").value("https://example.com/test-image.jpg"))
            .andExpect(jsonPath("$.normalContentUrl").value("https://example.com/test-image.jpg"))
            .andExpect(jsonPath("$.dataSaverContentUrl").value("https://example.com/test-image.jpg"))
            .andExpect(jsonPath("$.content").value("부산 여행"))
            .andExpect(jsonPath("$.likeCount").value(3))

        verify(postService).getPost(TRIP_GROUP_ID, POST_ID)
    }

    @Test
    @DisplayName("게시글 전체 조회")
    @WithMockLoginUser(id = MEMBER_ID)
    fun getPosts() {
        val date = LocalDate.of(2026, 7, 30)
        val startTime = LocalDateTime.of(2026, 7, 30, 10, 0)
        val endTime = LocalDateTime.of(2026, 7, 30, 11, 0)
        val createdAt = LocalDateTime.of(2026, 7, 30, 10, 20)

        val response = listOf(
            PostsDailyResponse(
                date = date,
                posts = listOf(
                    PostsDailyResponse.PostSummary(
                        postId = POST_ID,
                        originalFilename = "test-image.jpg",
                        contentUrl = "https://example.com/test-image.jpg",
                        normalContentUrl = "https://example.com/test-image-normal.webp",
                        dataSaverContentUrl = "https://example.com/test-image-data-saver.webp",
                        timelineId = TIMELINE_ID,
                        startTime = startTime,
                        endTime = endTime,
                        confirmedPlaceName = "광안리",
                        createdAt = createdAt,
                        likeCount = 5L,
                        authorMemberId = MEMBER_ID
                    )
                )
            )
        )
        val cursorResponse = PostCursorResponse(
            groups = response,
            nextCursor = "next-cursor",
            hasNext = true
        )

        `when`(
            postService.getPosts(TRIP_GROUP_ID, MEMBER_ID, null, 10)
        ).thenReturn(cursorResponse)

        mvc.perform(
            get(BASE_URL, TRIP_GROUP_ID)
        )
            .andDo(print())
            .andExpect(handler().handlerType(PostV1Controller::class.java))
            .andExpect(handler().methodName("getPosts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groups[0].date").value("2026-07-30"))
            .andExpect(jsonPath("$.groups[0].posts[0].postId").value(POST_ID))
            .andExpect(jsonPath("$.groups[0].posts[0].originalFilename").value("test-image.jpg"))
            .andExpect(
                jsonPath("$.groups[0].posts[0].contentUrl")
                    .value("https://example.com/test-image.jpg")
            )
            .andExpect(
                jsonPath("$.groups[0].posts[0].normalContentUrl")
                    .value("https://example.com/test-image-normal.webp")
            )
            .andExpect(
                jsonPath("$.groups[0].posts[0].dataSaverContentUrl")
                    .value("https://example.com/test-image-data-saver.webp")
            )
            .andExpect(jsonPath("$.groups[0].posts[0].timelineId").value(TIMELINE_ID))
            .andExpect(jsonPath("$.groups[0].posts[0].startTime").value("2026-07-30T10:00:00"))
            .andExpect(jsonPath("$.groups[0].posts[0].endTime").value("2026-07-30T11:00:00"))
            .andExpect(jsonPath("$.groups[0].posts[0].confirmedPlaceName").value("광안리"))
            .andExpect(jsonPath("$.groups[0].posts[0].createdAt").value("2026-07-30T10:20:00"))
            .andExpect(jsonPath("$.groups[0].posts[0].likeCount").value(5))
            .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
            .andExpect(jsonPath("$.hasNext").value(true))

        verify(postService).getPosts(TRIP_GROUP_ID, MEMBER_ID, null, 10)
    }

    @Test
    @DisplayName("게시글 수정")
    @WithMockLoginUser(id = MEMBER_ID)
    fun updatePost() {
        val request = UpdatePostRequest(
            content = "수정된 게시글 내용"
        )

        doNothing()
            .`when`(postService)
            .update(TRIP_GROUP_ID, POST_ID, request)

        mvc.perform(
            put("$BASE_URL/{postId}", TRIP_GROUP_ID, POST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(print())
            .andExpect(handler().handlerType(PostV1Controller::class.java))
            .andExpect(handler().methodName("update"))
            .andExpect(status().isOk)
            .andExpect(content().string(""))

        verify(postService).update(TRIP_GROUP_ID, POST_ID, request)
    }

    @Test
    @DisplayName("게시글 삭제")
    @WithMockLoginUser(id = MEMBER_ID)
    fun deletePost() {
        doNothing()
            .`when`(postService)
            .delete(TRIP_GROUP_ID, POST_ID)

        mvc.perform(
            delete("$BASE_URL/{postId}", TRIP_GROUP_ID, POST_ID)
        )
            .andDo(print())
            .andExpect(handler().handlerType(PostV1Controller::class.java))
            .andExpect(handler().methodName("delete"))
            .andExpect(status().isOk)
            .andExpect(content().string(""))

        verify(postService).delete(TRIP_GROUP_ID, POST_ID)
    }

    @Test
    @DisplayName("현재 시간대 사진 촬영 여부 조회")
    @WithMockLoginUser(id = MEMBER_ID)
    fun getPostsWithIsTaken() {
        val response = PostTimelineResponse(
            startTime = LocalDateTime.of(2026, 7, 30, 10, 0),
            endTime = LocalDateTime.of(2026, 7, 30, 11, 0),
            timelineId = TIMELINE_ID,
            confirmedPlaceName = "광안리",
            isTaken = true
        )

        `when`(
            postService.getCurrentSlot(
                TRIP_GROUP_ID,
                MEMBER_ID,
                1
            )
        ).thenReturn(response)

        mvc.perform(
            get("$BASE_URL/is-taken", TRIP_GROUP_ID)
                .queryParam("dayNumber", "1")
        )
            .andDo(print())
            .andExpect(handler().handlerType(PostV1Controller::class.java))
            .andExpect(handler().methodName("getPostsWithIsTaken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.data.startTime").value("2026-07-30T10:00:00"))
            .andExpect(jsonPath("$.data.endTime").value("2026-07-30T11:00:00"))
            .andExpect(jsonPath("$.data.timelineId").value(TIMELINE_ID))
            .andExpect(jsonPath("$.data.confirmedPlaceName").value("광안리"))
            .andExpect(jsonPath("$.data.isTaken").value(true))

        verify(postService).getCurrentSlot(
            TRIP_GROUP_ID,
            MEMBER_ID,
            1
        )
    }

    @Test
    @DisplayName("인증 정보 없이 게시글 생성 요청")
    fun createPostWithoutAuthentication() {
        val requestPart = MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
            {
                "timelineId": null,
                "content": "인증 없는 요청"
            }
            """.trimIndent().toByteArray()
        )

        mvc.perform(
            multipart(BASE_URL, TRIP_GROUP_ID)
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA)
        )
            .andDo(print())
            .andExpect(status().is5xxServerError)
    }
}
