package csh.back.domain.vote.user.service

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
class VoteUserServiceConcurrencyTest {

    @Autowired lateinit var voteUserService: VoteUserService

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var timelineRepository: TimelineRepository

    @Autowired lateinit var voteRepository: VoteRepository

    @Autowired lateinit var voteItemRepository: VoteItemRepository

    @Autowired lateinit var voteUserRepository: VoteUserRepository

    @Autowired lateinit var tripPlaceRepository: TripPlaceRepository

    private lateinit var member: Member
    private lateinit var tripMember: TripMember
    private lateinit var tripGroup: TripGroup
    private lateinit var vote: Vote
    private lateinit var voteItemA: VoteItem
    private lateinit var voteItemB: VoteItem

    @BeforeEach
    fun setUp() {
        member = memberRepository.save(Member("vote-user-concurrency-${System.nanoTime()}@test.com", "pw", "동시성유저"))

        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = member,
                name = "투표유저동시성테스트여행",
                region = "제주",
                nights = 1,
                joinCode = "VOTE-USER-CONCURRENCY-${System.nanoTime()}",
                startDate = LocalDate.of(2026, 12, 1),
                endDate = LocalDate.of(2026, 12, 2),
            ),
        )

        tripMember = tripMemberRepository.save(
            TripMember(member = member, tripGroup = tripGroup, isAdmin = true),
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
            Vote(tripGroup, timeline, tripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )

        val placeA = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "장소A",
                category = "관광",
                address = "주소",
                kakaoPlaceId = "kakao-vote-user-concurrency-a-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = tripMember,
            ),
        )
        val placeB = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "장소B",
                category = "관광",
                address = "주소",
                kakaoPlaceId = "kakao-vote-user-concurrency-b-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = tripMember,
            ),
        )
        voteItemA = voteItemRepository.save(VoteItem(vote = vote, tripPlace = placeA))
        voteItemB = voteItemRepository.save(VoteItem(vote = vote, tripPlace = placeB))
    }

    @Test
    @DisplayName("같은 유저가 같은 장소에 최초 투표를 동시에 두 번 보내도 updateCount는 0으로 유지된다")
    fun duplicateFirstVoteOnSamePlaceDoesNotIncreaseUpdateCount() {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val errors = mutableListOf<Throwable>()

        repeat(2) {
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    voteUserService.saveVoteUser(voteItemA, tripGroup.id!!, member.id!!)
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
        val voteUser = voteUserRepository.findByVoteIdAndTripMemberId(vote.id!!, tripMember.id!!).orElseThrow()
        assertThat(voteUser.updateCount).isEqualTo(0)
        assertThat(voteUser.voteItem.id).isEqualTo(voteItemA.id)
    }

    @Test
    @DisplayName("같은 유저가 서로 다른 장소에 동시에 최초 투표를 보내면 하나는 정상적으로 재투표(updateCount=1)로 처리된다")
    fun concurrentFirstVoteOnDifferentPlacesCountsAsRevote() {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val errors = mutableListOf<Throwable>()

        listOf(voteItemA, voteItemB).forEach { voteItem ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    voteUserService.saveVoteUser(voteItem, tripGroup.id!!, member.id!!)
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
        val voteUser = voteUserRepository.findByVoteIdAndTripMemberId(vote.id!!, tripMember.id!!).orElseThrow()
        assertThat(voteUser.updateCount).isEqualTo(1)
        assertThat(voteUser.voteItem.id).isIn(voteItemA.id, voteItemB.id)
    }
}