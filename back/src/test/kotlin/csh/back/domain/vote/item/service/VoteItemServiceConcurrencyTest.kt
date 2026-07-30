package csh.back.domain.vote.item.service

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
import csh.back.domain.vote.item.repository.VoteItemRepository
import csh.back.domain.vote.user.repository.VoteUserRepository
import csh.back.domain.vote.vote.entity.Vote
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
class VoteItemServiceConcurrencyTest {

    @Autowired lateinit var voteItemService: VoteItemService

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var timelineRepository: TimelineRepository

    @Autowired lateinit var voteRepository: VoteRepository

    @Autowired lateinit var voteItemRepository: VoteItemRepository

    @Autowired lateinit var voteUserRepository: VoteUserRepository

    @Autowired lateinit var tripPlaceRepository: TripPlaceRepository

    private lateinit var memberA: Member
    private lateinit var memberB: Member
    private lateinit var tripMemberA: TripMember
    private lateinit var tripMemberB: TripMember
    private lateinit var tripGroup: TripGroup
    private lateinit var vote: Vote
    private lateinit var place: TripPlace

    @BeforeEach
    fun setUp() {
        memberA = memberRepository.save(Member("vote-item-concurrency-a-${System.nanoTime()}@test.com", "pw", "동시성유저A"))
        memberB = memberRepository.save(Member("vote-item-concurrency-b-${System.nanoTime()}@test.com", "pw", "동시성유저B"))

        tripGroup = tripGroupRepository.save(
            TripGroup.builder()
                .owner(memberA)
                .name("동시성테스트여행")
                .region("부산")
                .nights(1)
                .joinCode("VOTE-ITEM-CONCURRENCY-${System.nanoTime()}")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 2))
                .build(),
        )

        tripMemberA = tripMemberRepository.save(
            TripMember.builder().member(memberA).tripGroup(tripGroup).isAdmin(true).build(),
        )
        tripMemberB = tripMemberRepository.save(
            TripMember.builder().member(memberB).tripGroup(tripGroup).isAdmin(false).build(),
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

        place = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "동시성장소",
                category = "관광",
                address = "주소",
                kakaoPlaceId = "kakao-vote-item-concurrency-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = tripMemberA,
            ),
        )
    }

    @Test
    @DisplayName("두 유저가 같은 신규 장소에 동시에 첫 투표를 해도 예외 없이 VoteItem은 하나만 생성된다")
    fun concurrentFirstVoteOnSamePlaceDoesNotThrow() {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val errors = mutableListOf<Throwable>()

        listOf(memberA, memberB).forEach { member ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    voteItemService.saveVoteItem(tripGroup.id!!, member.id!!, vote.id!!, place.id!!)
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(errors).isEmpty()
        assertThat(voteItemRepository.findByVoteIdAndTripPlaceId(vote.id!!, place.id!!)).isPresent
        assertThat(voteUserRepository.findByVoteIdAndTripMemberId(vote.id!!, tripMemberA.id!!)).isPresent
        assertThat(voteUserRepository.findByVoteIdAndTripMemberId(vote.id!!, tripMemberB.id!!)).isPresent
    }
}