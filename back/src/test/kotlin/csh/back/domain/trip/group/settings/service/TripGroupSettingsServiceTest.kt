package csh.back.domain.trip.group.settings.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.settings.entity.TripGroupSettings
import csh.back.domain.trip.group.settings.exception.TripGroupSettingsLockedException
import csh.back.domain.trip.group.settings.repository.TripGroupSettingsRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import csh.back.domain.vote.vote.entity.Vote
import csh.back.domain.vote.vote.enums.VoteStatus
import csh.back.domain.vote.vote.repository.VoteRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TripGroupSettingsServiceTest {

    @Autowired lateinit var tripGroupSettingsService: TripGroupSettingsService

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var tripGroupSettingsRepository: TripGroupSettingsRepository

    @Autowired lateinit var timelineRepository: TimelineRepository

    @Autowired lateinit var voteRepository: VoteRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private lateinit var owner: Member
    private lateinit var tripGroup: TripGroup
    private lateinit var ownerTripMember: TripMember

    @BeforeEach
    fun setUp() {
        owner = memberRepository.save(Member("settings-owner-${System.nanoTime()}@test.com", "pw", "설정방장"))
        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = owner,
                name = "설정테스트여행",
                region = "제주",
                nights = 1,
                joinCode = "SETTINGS-JOIN-${System.nanoTime()}",
                startDate = LocalDate.of(2026, 10, 1),
                endDate = LocalDate.of(2026, 10, 2),
            ),
        )
        ownerTripMember = tripMemberRepository.save(TripMember(member = owner, tripGroup = tripGroup, isAdmin = true))
    }

    private fun createVote(status: VoteStatus, dayNumber: Long): Vote {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = dayNumber,
                startTime = LocalDateTime.of(2026, 10, 1, 9, 0),
                endTime = LocalDateTime.of(2026, 10, 1, 10, 0),
            ),
        )
        val vote = voteRepository.save(
            Vote(tripGroup, timeline, ownerTripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )
        vote.updateStatus(status)
        return vote
    }

    @Test
    @DisplayName("updateAnonymousVote - 기존 설정 갱신, PENDING 투표만 일괄 반영")
    fun updateAnonymousVoteWithExistingSettings() {
        tripGroupSettingsRepository.save(TripGroupSettings(tripGroup = tripGroup, isAnonymousVote = true))
        val pendingVote = createVote(VoteStatus.PENDING, dayNumber = 1L)
        val confirmedVote = createVote(VoteStatus.CONFIRMED, dayNumber = 2L)

        val response = tripGroupSettingsService.updateAnonymousVote(tripGroup.id!!, owner.id!!, false)
        entityManager.flush()
        entityManager.clear()

        assertThat(response.isAnonymousVote).isFalse()
        assertThat(voteRepository.findById(pendingVote.id!!).orElseThrow().isAnonymous).isFalse()
        assertThat(voteRepository.findById(confirmedVote.id!!).orElseThrow().isAnonymous).isTrue()
    }

    @Test
    @DisplayName("updateAnonymousVote - 설정이 없으면 새로 생성")
    fun updateAnonymousVoteLazyCreate() {
        assertThat(tripGroupSettingsRepository.findByTripGroupId(tripGroup.id!!).isPresent).isFalse()

        val response = tripGroupSettingsService.updateAnonymousVote(tripGroup.id!!, owner.id!!, false)

        assertThat(response.isAnonymousVote).isFalse()
        assertThat(tripGroupSettingsRepository.findByTripGroupId(tripGroup.id!!).orElseThrow().isAnonymousVote).isFalse()
    }

    @Test
    @DisplayName("updateAnonymousVote - 여행 시작 후에는 예외 발생")
    fun updateAnonymousVoteAfterTripStartLocked() {
        val startedTripGroup = tripGroupRepository.save(
            TripGroup(
                owner = owner,
                name = "시작된여행",
                region = "제주",
                nights = 1,
                joinCode = "SETTINGS-JOIN-STARTED-${System.nanoTime()}",
                startDate = LocalDate.of(2020, 1, 1),
                endDate = LocalDate.of(2020, 1, 2),
            ),
        )
        tripMemberRepository.save(TripMember(member = owner, tripGroup = startedTripGroup, isAdmin = true))

        assertThatThrownBy { tripGroupSettingsService.updateAnonymousVote(startedTripGroup.id!!, owner.id!!, false) }
            .isInstanceOf(TripGroupSettingsLockedException::class.java)
            .hasMessage("여행 시작일부터 익명 투표 설정을 변경할 수 없습니다.")
    }

    @Test
    @DisplayName("updateAnonymousVote - 방장이 아니면 예외 발생")
    fun updateAnonymousVoteNonAdmin() {
        val other = memberRepository.save(Member("settings-nonadmin-${System.nanoTime()}@test.com", "pw", "비방장"))
        tripMemberRepository.save(TripMember(member = other, tripGroup = tripGroup, isAdmin = false))

        assertThatThrownBy { tripGroupSettingsService.updateAnonymousVote(tripGroup.id!!, other.id!!, false) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("여행 모임 방장만 접근할 수 있습니다.")
    }

    @Test
    @DisplayName("getSettings - 설정이 있으면 저장된 값 반환")
    fun getSettingsWithExistingSettings() {
        tripGroupSettingsRepository.save(TripGroupSettings(tripGroup = tripGroup, isAnonymousVote = false))

        val response = tripGroupSettingsService.getSettings(tripGroup.id!!, owner.id!!)

        assertThat(response.isAnonymousVote).isFalse()
        assertThat(response.days).allMatch { it.freeTimeMinutes == 60 }
    }

    @Test
    @DisplayName("getSettings - 설정이 없으면 기본값을 생성해서 반환")
    fun getSettingsWithoutSettings() {
        val response = tripGroupSettingsService.getSettings(tripGroup.id!!, owner.id!!)

        assertThat(response.isAnonymousVote).isTrue()
        assertThat(response.days).allMatch { it.freeTimeMinutes == 60 }
        assertThat(tripGroupSettingsRepository.findByTripGroupId(tripGroup.id!!).isPresent).isTrue()
    }

    @Test
    @DisplayName("getSettings - 그룹 멤버가 아니면 예외 발생")
    fun getSettingsNonMember() {
        val other = memberRepository.save(Member("settings-nonmember-${System.nanoTime()}@test.com", "pw", "비멤버"))

        assertThatThrownBy { tripGroupSettingsService.getSettings(tripGroup.id!!, other.id!!) }
            .isInstanceOf(NonMemberException::class.java)
            .hasMessage("해당 모임의 멤버가 아닙니다.")
    }
}