package csh.back.domain.trip.place.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException
import csh.back.domain.trip.place.repository.TripPlaceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
class TripPlaceServiceConcurrencyTest {

    @Autowired lateinit var tripPlaceService: TripPlaceService

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var tripPlaceRepository: TripPlaceRepository

    private lateinit var memberA: Member
    private lateinit var memberB: Member
    private lateinit var tripGroup: TripGroup
    private lateinit var kakaoPlaceId: String

    @BeforeEach
    fun setUp() {
        memberA = memberRepository.save(Member("trip-place-concurrency-a-${System.nanoTime()}@test.com", "pw", "동시성유저A"))
        memberB = memberRepository.save(Member("trip-place-concurrency-b-${System.nanoTime()}@test.com", "pw", "동시성유저B"))

        tripGroup = tripGroupRepository.save(
            TripGroup.builder()
                .owner(memberA)
                .name("동시성테스트여행")
                .region("부산")
                .nights(1)
                .joinCode("TRIP-PLACE-CONCURRENCY-${System.nanoTime()}")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 2))
                .build(),
        )

        tripMemberRepository.save(TripMember.builder().member(memberA).tripGroup(tripGroup).isAdmin(true).build())
        tripMemberRepository.save(TripMember.builder().member(memberB).tripGroup(tripGroup).isAdmin(false).build())

        kakaoPlaceId = "kakao-trip-place-concurrency-${System.nanoTime()}"
    }

    @Test
    @DisplayName("두 유저가 같은 kakaoPlaceId로 동시에 장소를 저장하면 한 명만 성공하고 나머지는 DuplicateTripPlaceException을 받는다")
    fun concurrentSaveOfSamePlaceRejectsLoser() {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val successes = mutableListOf<Long>()
        val duplicateErrors = mutableListOf<DuplicateTripPlaceException>()
        val unexpectedErrors = mutableListOf<Throwable>()

        listOf(memberA, memberB).forEach { member ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    val response = tripPlaceService.savePlace(
                        tripGroupId = tripGroup.id!!,
                        name = "동시성장소",
                        category = "관광",
                        address = "주소",
                        kakaoPlaceId = kakaoPlaceId,
                        kakaoMapUrl = "url",
                        memberId = member.id!!,
                    )
                    synchronized(successes) { successes.add(response.id) }
                } catch (e: DuplicateTripPlaceException) {
                    synchronized(duplicateErrors) { duplicateErrors.add(e) }
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
        assertThat(duplicateErrors).hasSize(1)
        assertThat(tripPlaceRepository.findAllByTripGroupId(tripGroup.id!!)).hasSize(1)
    }
}