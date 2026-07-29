package csh.back.domain.trip.post.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.post.dto.request.CreatePostRequest
import csh.back.domain.trip.post.dto.request.UpdatePostRequest
import csh.back.domain.trip.post.dto.response.PostResponse
import csh.back.domain.trip.post.dto.response.PostTimelineResponse
import csh.back.domain.trip.post.dto.response.PostsDailyResponse
import csh.back.domain.trip.post.service.PostService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@ApiV1
@RequestMapping("/trips/{tripGroupId}/posts")
@Tag(name = "게시물", description = "게시글 API")
class PostV1Controller(
    private val postService: PostService
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "게시글 생성", description = "사진이 포함된 게시글을 생성합니다.")
    fun create(
        @PathVariable tripGroupId: Long,
        @RequestPart request: CreatePostRequest,
        @RequestPart(value = "image", required = false) image: MultipartFile?,
        @AuthenticationPrincipal member: AuthFilterDto
    ): PostResponse = postService.create(tripGroupId, member.id(), request.timelineId, image)

    @GetMapping("/{postId}")
    @Operation(summary = "게시글 조회", description = "게시글 단건 조회")
    fun getPost(
        @PathVariable tripGroupId: Long,
        @PathVariable postId: Long,
        @AuthenticationPrincipal member: AuthFilterDto
    ): PostResponse = postService.getPost(tripGroupId, postId)

    @GetMapping
    @Operation(summary = "게시글 전체 조회", description = "타임라인별 전체 게시글을 조회합니다.")
    fun getPosts(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto
    ): List<PostsDailyResponse> = postService.getPosts(tripGroupId, member.id())

    @PutMapping("/{postId}")
    @Operation(summary = "게시글 수정", description = "게시글 내용을 수정합니다.")
    fun update(
        @PathVariable tripGroupId: Long,
        @PathVariable postId: Long,
        @RequestBody request: UpdatePostRequest
    ) = postService.update(tripGroupId, postId, request)

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    fun delete(
        @PathVariable tripGroupId: Long,
        @PathVariable postId: Long
    ) = postService.delete(tripGroupId, postId)

    @GetMapping("/is-taken")
    @Operation(summary = "사진 촬영 가능 여부 판단")
    fun getPostsWithIsTaken(
        @PathVariable tripGroupId: Long,
        @RequestParam dayNumber: Int,
        @AuthenticationPrincipal member: AuthFilterDto
    ): ResponseData<PostTimelineResponse> = ResponseData(
        200,
        postService.getCurrentSlot(tripGroupId, member.id(), dayNumber)
    )
}
