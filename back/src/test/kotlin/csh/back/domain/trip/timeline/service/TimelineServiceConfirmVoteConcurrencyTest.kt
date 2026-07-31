package csh.back.domain.trip.timeline.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
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
import csh.back.domain.vote.user.entity.VoteUser
import csh.back.domain.vote.user.repository.VoteUserRepository
import csh.back.domain.vote.vote.entity.Vote
import csh.back.domain.vote.vote.enums.VoteStatus
import csh.back.domain.vote.vote.repository.VoteRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
class TimelineServiceConfirmVoteConcurrencyTest {

    @Autowired lateinit var timelineService: TimelineService

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var timelineRepository: TimelineRepository

    @Autowired lateinit var tripPlaceRepository: TripPlaceRepository

    @Autowired lateinit var voteRepository: VoteRepository

    @Autowired lateinit var voteItemRepository: VoteItemRepository

    @Autowired lateinit var voteUserRepository: VoteUserRepository

    private lateinit var tripGroup: TripGroup
    private lateinit var tripMemberA: TripMember
    private lateinit var vote: Vote

    @BeforeEach
    fun setUp() {
        val memberA = memberRepository.save(Member("confirm-vote-concurrency-a-${System.nanoTime()}@test.com", "pw", "동시성유저A"))
        val memberB = memberRepository.save(Member("confirm-vote-concurrency-b-${System.nanoTime()}@test.com", "pw", "동시성유저B"))

        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = memberA,
                name = "동시성테스트여행",
                region = "부산",
                nights = 1,
                joinCode = "CONFIRM-VOTE-CONCURRENCY-${System.nanoTime()}",
                startDate = LocalDate.of(2026, 12, 1),
                endDate = LocalDate.of(2026, 12, 2),
            ),
        )

        tripMemberA = tripMemberRepository.save(
            TripMember(member = memberA, tripGroup = tripGroup, isAdmin = true),
        )
        val tripMemberB = tripMemberRepository.save(
            TripMember(member = memberB, tripGroup = tripGroup, isAdmin = false),
        )

        val timeline: Timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1L,
                startTime = LocalDateTime.of(2026, 12, 1, 9, 0),
                endTime = LocalDateTime.of(2026, 12, 1, 10, 0),
            ),
        )

        vote = voteRepository.save(
            Vote(tripGroup, timeline, tripMemberA, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )

        val place: TripPlace = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "동시성장소",
                category = "관광",
                address = "주소",
                kakaoPlaceId = "kakao-confirm-vote-concurrency-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = tripMemberA,
            ),
        )

        val voteItem: VoteItem = voteItemRepository.save(VoteItem(vote, place))
        voteUserRepository.save(VoteUser(vote, voteItem, tripMemberA, 0))
        voteUserRepository.save(VoteUser(vote, voteItem, tripMemberB, 0))
    }

    @Test
    @DisplayName("같은 투표를 두 명이 동시에 확정해도 한 명만 성공하고 나머지는 IllegalStateException을 받으며 상태는 한 번만 CONFIRMED로 바뀐다")
    fun concurrentConfirmVoteRejectsLoser() {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val successes = mutableListOf<Long>()
        val illegalStateErrors = mutableListOf<IllegalStateException>()
        val unexpectedErrors = mutableListOf<Throwable>()

        val memberId = tripMemberA.member.id!!
        repeat(2) {
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    val response = timelineService.confirmVote(tripGroup.id!!, memberId, vote.id!!)
                    synchronized(successes) { successes.add(response.confirmedPlaceId) }
                } catch (e: IllegalStateException) {
                    synchronized(illegalStateErrors) { illegalStateErrors.add(e) }
                } catch (e: Throwable) {
                    synchronized(unexpectedErrors) { unexpectedErrors.add(e) }
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(unexpectedErrors).isEmpty()
        assertThat(successes).hasSize(1)
        assertThat(illegalStateErrors).hasSize(1)
        assertThat(voteRepository.findById(vote.id!!).orElseThrow().status).isEqualTo(VoteStatus.CONFIRMED)
    }
}