package csh.back.domain.trip.place.controller

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.support.WithMockMember
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.trip.place.repository.TripPlaceRepository
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.item.repository.VoteItemRepository
import csh.back.domain.vote.vote.entity.Vote
import csh.back.domain.vote.vote.repository.VoteRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripPlaceDeleteV1ControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var tripGroupRepository: TripGroupRepository

    @Autowired
    lateinit var tripMemberRepository: TripMemberRepository

    @Autowired
    lateinit var tripPlaceRepository: TripPlaceRepository

    @Autowired
    lateinit var timelineRepository: TimelineRepository

    @Autowired
    lateinit var voteRepository: VoteRepository

    @Autowired
    lateinit var voteItemRepository: VoteItemRepository

    private lateinit var tripGroup: TripGroup
    private lateinit var authorTripMember: TripMember

    @BeforeEach
    fun setUp() {
        tripGroup = createTrip(LocalDate.now().plusDays(1))
    }

    private fun createTrip(startDate: LocalDate): TripGroup {
        val owner = memberRepository.findById(1L).orElseThrow()
        val group = tripGroupRepository.save(
            TripGroup(
                owner = owner,
                name = "위시 장소 삭제 테스트 여행",
                region = "부산",
                nights = 1,
                joinCode = UUID.randomUUID().toString(),
                startDate = startDate,
                endDate = startDate.plusDays(1),
            ),
        )
        authorTripMember = tripMemberRepository.save(
            TripMember(member = owner, tripGroup = group, isAdmin = true),
        )
        tripGroup = group
        return group
    }

    private fun createPlace(): TripPlace = tripPlaceRepository.save(
        TripPlace(
            tripGroup = tripGroup,
            name = "삭제대상장소",
            category = "관광",
            address = "주소",
            kakaoPlaceId = "kakao-place-delete-ctrl-${UUID.randomUUID()}",
            kakaoMapUrl = "url",
            createdBy = authorTripMember,
        ),
    )

    @Test
    @DisplayName("작성자가 위시 장소를 삭제하면 200과 함께 목록에서 제거된다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun deleteWishPlaceAsAuthorSucceeds() {
        val place = createPlace()

        mvc.perform(delete("$BASE_URL/trips/${tripGroup.id}/wish-places/${place.id}"))
            .andExpect(handler().handlerType(TripPlaceV1Controller::class.java))
            .andExpect(handler().methodName("deleteWishPlace"))
            .andExpect(status().isOk)

        assertThat(tripPlaceRepository.findById(place.id!!)).isEmpty
    }

    @Test
    @DisplayName("존재하지 않는 위시 장소를 삭제하려 하면 404를 응답한다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun deleteNonExistingWishPlaceReturnsNotFound() {
        mvc.perform(delete("$BASE_URL/trips/${tripGroup.id}/wish-places/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("후보 장소를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("작성자가 아닌 멤버가 삭제하려 하면 403을 응답한다")
    @WithMockMember(id = 3L, email = "member3@admin.com")
    fun deleteWishPlaceAsNonAuthorReturnsForbidden() {
        val other = memberRepository.findById(3L).orElseThrow()
        tripMemberRepository.save(TripMember(member = other, tripGroup = tripGroup, isAdmin = false))
        val place = createPlace()

        mvc.perform(delete("$BASE_URL/trips/${tripGroup.id}/wish-places/${place.id}"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("작성자만 삭제할 수 있습니다."))
    }

    @Test
    @DisplayName("여행 시작일 이후에는 위시 장소를 삭제할 수 없다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun deleteWishPlaceAfterTripStartedReturnsBadRequest() {
        val startedTrip = createTrip(LocalDate.now().minusDays(1))
        val place = createPlace()

        mvc.perform(delete("$BASE_URL/trips/${startedTrip.id}/wish-places/${place.id}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("여행 시작 전에만 후보 장소를 삭제할 수 있습니다."))
    }

    @Test
    @DisplayName("이미 투표에 사용된 위시 장소는 삭제할 수 없다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun deleteWishPlaceInUseReturnsConflict() {
        val place = createPlace()
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1L,
                startTime = LocalDateTime.of(tripGroup.startDate, LocalTime.of(9, 0)),
                endTime = LocalDateTime.of(tripGroup.startDate, LocalTime.of(10, 0)),
            ),
        )
        val vote = voteRepository.save(
            Vote(tripGroup, timeline, authorTripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )
        voteItemRepository.save(VoteItem(vote = vote, tripPlace = place))

        mvc.perform(delete("$BASE_URL/trips/${tripGroup.id}/wish-places/${place.id}"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 투표에 사용된 장소는 삭제할 수 없습니다."))
    }

    private companion object {
        const val BASE_URL = "/api/v1"
    }
}
