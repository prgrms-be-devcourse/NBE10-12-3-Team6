package csh.back.domain.trip.place.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.trip.place.exception.TripAlreadyStartedException
import csh.back.domain.trip.place.exception.WishPlaceInUseException
import csh.back.domain.trip.place.repository.TripPlaceRepository
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.item.repository.VoteItemRepository
import csh.back.domain.vote.vote.entity.Vote
import csh.back.domain.vote.vote.repository.VoteRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
class TripPlaceDeleteServiceTest {

    @Autowired lateinit var tripPlaceService: TripPlaceService

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var tripPlaceRepository: TripPlaceRepository

    @Autowired lateinit var timelineRepository: TimelineRepository

    @Autowired lateinit var voteRepository: VoteRepository

    @Autowired lateinit var voteItemRepository: VoteItemRepository

    private lateinit var author: Member
    private lateinit var other: Member
    private lateinit var authorTripMember: TripMember

    private fun createTripGroup(startDate: LocalDate): TripGroup = tripGroupRepository.save(
        TripGroup(
            owner = author,
            name = "삭제테스트여행",
            region = "부산",
            nights = 1,
            joinCode = "TRIP-PLACE-DELETE-${System.nanoTime()}",
            startDate = startDate,
            endDate = startDate.plusDays(1),
        ),
    )

    private fun createPlace(tripGroup: TripGroup): TripPlace = tripPlaceRepository.save(
        TripPlace(
            tripGroup = tripGroup,
            name = "삭제대상장소",
            category = "관광",
            address = "주소",
            kakaoPlaceId = "kakao-trip-place-delete-${System.nanoTime()}",
            kakaoMapUrl = "url",
            createdBy = authorTripMember,
        ),
    )

    @BeforeEach
    fun setUp() {
        author = memberRepository.save(Member("trip-place-delete-author-${System.nanoTime()}@test.com", "pw", "작성자"))
        other = memberRepository.save(Member("trip-place-delete-other-${System.nanoTime()}@test.com", "pw", "타인"))
    }

    private fun joinAsMember(tripGroup: TripGroup, member: Member, isAdmin: Boolean = false): TripMember =
        tripMemberRepository.save(TripMember(member = member, tripGroup = tripGroup, isAdmin = isAdmin))

    @Test
    @DisplayName("작성자가 여행 시작 전, 투표에 사용되지 않은 장소를 삭제하면 정상적으로 삭제된다")
    fun deletePlaceByAuthorSucceeds() {
        val tripGroup = createTripGroup(LocalDate.now().plusDays(1))
        authorTripMember = joinAsMember(tripGroup, author, isAdmin = true)
        val place = createPlace(tripGroup)

        tripPlaceService.deletePlace(tripGroup.id!!, place.id!!, author.id!!)

        assertThat(tripPlaceRepository.findById(place.id!!)).isEmpty
    }

    @Test
    @DisplayName("존재하지 않는 장소를 삭제하려 하면 NotFoundException이 발생한다")
    fun deleteNonExistingPlaceThrowsNotFound() {
        val tripGroup = createTripGroup(LocalDate.now().plusDays(1))
        authorTripMember = joinAsMember(tripGroup, author, isAdmin = true)

        assertThatThrownBy { tripPlaceService.deletePlace(tripGroup.id!!, -1L, author.id!!) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    @DisplayName("작성자가 아닌 멤버가 삭제하려 하면 NonMemberException이 발생한다")
    fun deleteByNonAuthorThrowsNonMember() {
        val tripGroup = createTripGroup(LocalDate.now().plusDays(1))
        authorTripMember = joinAsMember(tripGroup, author, isAdmin = true)
        joinAsMember(tripGroup, other, isAdmin = false)
        val place = createPlace(tripGroup)

        assertThatThrownBy { tripPlaceService.deletePlace(tripGroup.id!!, place.id!!, other.id!!) }
            .isInstanceOf(NonMemberException::class.java)

        assertThat(tripPlaceRepository.findById(place.id!!)).isPresent
    }

    @Test
    @DisplayName("여행 시작일 이후에는 삭제할 수 없다")
    fun deleteAfterTripStartedThrows() {
        val tripGroup = createTripGroup(LocalDate.now().minusDays(1))
        authorTripMember = joinAsMember(tripGroup, author, isAdmin = true)
        val place = createPlace(tripGroup)

        assertThatThrownBy { tripPlaceService.deletePlace(tripGroup.id!!, place.id!!, author.id!!) }
            .isInstanceOf(TripAlreadyStartedException::class.java)

        assertThat(tripPlaceRepository.findById(place.id!!)).isPresent
    }

    @Test
    @DisplayName("이미 투표에 사용된 장소는 삭제할 수 없다")
    fun deleteWishPlaceInUseThrows() {
        val tripGroup = createTripGroup(LocalDate.now().plusDays(1))
        authorTripMember = joinAsMember(tripGroup, author, isAdmin = true)
        val place = createPlace(tripGroup)

        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1L,
                startTime = LocalDateTime.of(tripGroup.startDate, java.time.LocalTime.of(9, 0)),
                endTime = LocalDateTime.of(tripGroup.startDate, java.time.LocalTime.of(10, 0)),
            ),
        )
        val vote = voteRepository.save(
            Vote(tripGroup, timeline, authorTripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )
        voteItemRepository.save(VoteItem(vote = vote, tripPlace = place))

        assertThatThrownBy { tripPlaceService.deletePlace(tripGroup.id!!, place.id!!, author.id!!) }
            .isInstanceOf(WishPlaceInUseException::class.java)

        assertThat(tripPlaceRepository.findById(place.id!!)).isPresent
    }
}
