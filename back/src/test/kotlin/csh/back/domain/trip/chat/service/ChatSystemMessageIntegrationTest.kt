package csh.back.domain.trip.chat.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.chat.enums.MessageType
import csh.back.domain.trip.chat.repository.TripChatMessageRepository
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.domain.trip.group.settings.dto.request.TripGroupSettingsUpdateRequest
import csh.back.domain.trip.group.settings.service.TripGroupSettingsService
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.service.TripMemberService
import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.trip.place.repository.TripPlaceRepository
import csh.back.domain.trip.place.service.TripPlaceService
import csh.back.domain.trip.timeline.dto.request.TimelineAllCreateRequest
import csh.back.domain.trip.timeline.dto.request.TimelineCreateRequest
import csh.back.domain.trip.timeline.dto.request.TimelineUpdateRequest
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import csh.back.domain.trip.timeline.service.TimelineService
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.item.repository.VoteItemRepository
import csh.back.domain.vote.item.service.VoteItemService
import csh.back.domain.vote.user.entity.VoteUser
import csh.back.domain.vote.user.repository.VoteUserRepository
import csh.back.domain.vote.vote.entity.Vote
import csh.back.domain.vote.vote.repository.VoteRepository
import csh.back.domain.vote.vote.service.VoteService
import org.assertj.core.api.Assertions.assertThat
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
class ChatSystemMessageIntegrationTest {

    @Autowired lateinit var tripGroupService: TripGroupService
    @Autowired lateinit var tripGroupSettingsService: TripGroupSettingsService
    @Autowired lateinit var tripMemberService: TripMemberService
    @Autowired lateinit var tripPlaceService: TripPlaceService
    @Autowired lateinit var timelineService: TimelineService
    @Autowired lateinit var voteService: VoteService
    @Autowired lateinit var voteItemService: VoteItemService

    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var tripGroupRepository: TripGroupRepository
    @Autowired lateinit var tripMemberRepository: TripMemberRepository
    @Autowired lateinit var tripPlaceRepository: TripPlaceRepository
    @Autowired lateinit var timelineRepository: TimelineRepository
    @Autowired lateinit var voteRepository: VoteRepository
    @Autowired lateinit var voteItemRepository: VoteItemRepository
    @Autowired lateinit var voteUserRepository: VoteUserRepository
    @Autowired lateinit var tripChatMessageRepository: TripChatMessageRepository

    private lateinit var owner: Member
    private lateinit var tripGroup: TripGroup
    private lateinit var ownerTripMember: TripMember

    @BeforeEach
    fun setUp() {
        owner = memberRepository.save(Member("chat-sys-owner-${System.nanoTime()}@test.com", "pw", "시스템메시지방장"))
        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = owner,
                name = "시스템메시지테스트여행",
                region = "강릉",
                nights = 2,
                joinCode = "CHAT-SYS-${System.nanoTime()}",
                startDate = LocalDate.now().plusDays(30),
                endDate = LocalDate.now().plusDays(32),
            ),
        )
        ownerTripMember = tripMemberRepository.save(TripMember(member = owner, tripGroup = tripGroup, isAdmin = true))
    }

    private fun assertSingleSystemMessage(expectedContent: String) {
        val messages = tripChatMessageRepository.findAll().filter { it.tripGroup.id == tripGroup.id }
        assertThat(messages).hasSize(1)
        assertThat(messages[0].messageType).isEqualTo(MessageType.SYSTEM)
        assertThat(messages[0].sender).isNull()
        assertThat(messages[0].content).isEqualTo(expectedContent)
    }

    @Test
    @DisplayName("여행방 정보 수정 시 시스템 메시지가 기록된다")
    fun modifyGroupDetailRecordsSystemMessage() {
        tripGroupService.modifyGroupDetail(tripGroup.id!!, owner.id!!, TripGroupModifyRequest(name = "수정된여행이름"))

        assertSingleSystemMessage("여행방 정보 변경")
    }

    @Test
    @DisplayName("특정 일차 자유시간 범위 수정 시 시스템 메시지가 기록된다")
    fun updateFreeTimeMinutesRecordsSystemMessage() {
        tripGroupSettingsService.updateFreeTimeMinutes(
            tripGroup.id!!,
            1,
            owner.id!!,
            TripGroupSettingsUpdateRequest(60),
        )

        assertSingleSystemMessage("1일차 자유시간 범위 변경")
    }

    @Test
    @DisplayName("모든 일차 자유시간 범위 수정 시 시스템 메시지가 기록된다")
    fun updateAllFreeTimeMinutesRecordsSystemMessage() {
        tripGroupSettingsService.updateAllFreeTimeMinutes(
            tripGroup.id!!,
            owner.id!!,
            TripGroupSettingsUpdateRequest(60),
        )

        assertSingleSystemMessage("모든 일차 자유시간 범위 변경")
    }

    @Test
    @DisplayName("여행방 참여 시 시스템 메시지가 기록된다")
    fun createJoinMemberRecordsSystemMessage() {
        val newMember = memberRepository.save(Member("chat-sys-joiner-${System.nanoTime()}@test.com", "pw", "새참여자"))

        tripMemberService.createJoinMember(tripGroup.joinCode, newMember.id!!)

        assertSingleSystemMessage("${newMember.name}님 여행방 참여")
    }

    @Test
    @DisplayName("후보 장소 추가 시 시스템 메시지가 기록된다")
    fun savePlaceRecordsSystemMessage() {
        val response = tripPlaceService.savePlace(
            tripGroupId = tripGroup.id!!,
            name = "경포대",
            category = "관광",
            address = "강릉시",
            kakaoPlaceId = "kakao-chat-sys-${System.nanoTime()}",
            kakaoMapUrl = "url",
            memberId = owner.id!!,
        )

        assertSingleSystemMessage("${response.name} 후보 장소 추가")
    }

    @Test
    @DisplayName("후보 장소 삭제 시 시스템 메시지가 기록된다")
    fun deletePlaceRecordsSystemMessage() {
        val place = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "경포대",
                category = "관광",
                address = "강릉시",
                kakaoPlaceId = "kakao-chat-sys-delete-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = ownerTripMember,
            ),
        )

        tripPlaceService.deletePlace(tripGroup.id!!, place.id!!, owner.id!!)

        assertSingleSystemMessage("${place.name}이(가) 후보 장소에서 삭제되었습니다.")
    }

    @Test
    @DisplayName("타임라인 생성 시 시스템 메시지가 기록된다")
    fun createTimelineRecordsSystemMessage() {
        timelineService.createTimeline(
            tripGroup.id!!,
            owner.id!!,
            TimelineCreateRequest(
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )

        assertSingleSystemMessage("1일차 시간 구간 추가")
    }

    @Test
    @DisplayName("타임라인 일괄 생성 시 시스템 메시지가 기록된다")
    fun createAllTimelinesRecordsSystemMessage() {
        timelineService.createAllTimelines(
            tripGroup.id!!,
            owner.id!!,
            TimelineAllCreateRequest(
                dayNumber = 2,
                timelines = listOf(
                    TimelineCreateRequest(
                        dayNumber = 2,
                        startTime = LocalDateTime.now().plusDays(31).withHour(9).withMinute(0),
                        endTime = LocalDateTime.now().plusDays(31).withHour(10).withMinute(0),
                    ),
                ),
            ),
        )

        assertSingleSystemMessage("2일차 시간 구간 추가")
    }

    @Test
    @DisplayName("타임라인 시간 수정 시 시스템 메시지가 기록된다")
    fun updateTimelineRecordsSystemMessage() {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )

        timelineService.updateTimeline(
            tripGroup.id!!,
            timeline.id!!,
            owner.id!!,
            TimelineUpdateRequest(
                startTime = LocalDateTime.now().plusDays(30).withHour(11).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(12).withMinute(0),
            ),
        )

        assertSingleSystemMessage("1일차 시간 구간 시간 변경")
    }

    @Test
    @DisplayName("타임라인 삭제 시 시스템 메시지가 기록된다")
    fun deleteTimelineRecordsSystemMessage() {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )

        timelineService.deleteTimeline(tripGroup.id!!, timeline.id!!, owner.id!!)

        assertSingleSystemMessage("1일차 시간 구간 삭제")
    }

    @Test
    @DisplayName("투표 확정(방장) 시 시스템 메시지가 기록된다")
    fun confirmVoteRecordsSystemMessage() {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )
        val vote = voteRepository.save(
            Vote(tripGroup, timeline, ownerTripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )
        val place = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "경포대",
                category = "관광",
                address = "강릉시",
                kakaoPlaceId = "kakao-chat-sys-confirm-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = ownerTripMember,
            ),
        )
        val voteItem = voteItemRepository.save(VoteItem(vote, place))
        voteUserRepository.save(VoteUser(vote, voteItem, ownerTripMember, 0))

        timelineService.confirmVote(tripGroup.id!!, owner.id!!, vote.id!!)

        assertSingleSystemMessage("1일차 9시 ${place.name} 확정!")
    }

    @Test
    @DisplayName("투표 결과가 없으면 시스템에 의해 만료 처리되고 시스템 메시지가 기록된다")
    fun expireAndConfirmBySystemRecordsExpiredSystemMessage() {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )
        val vote = voteRepository.save(
            Vote(tripGroup, timeline, ownerTripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )

        timelineService.expireAndConfirmBySystem(vote.id!!)

        assertSingleSystemMessage("1일차 시간 구간 투표 종료")
    }

    @Test
    @DisplayName("투표 결과가 있으면 시스템에 의해 장소가 확정되고 시스템 메시지가 기록된다")
    fun expireAndConfirmBySystemRecordsConfirmedSystemMessage() {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )
        val vote = voteRepository.save(
            Vote(tripGroup, timeline, ownerTripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )
        val place = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "경포대",
                category = "관광",
                address = "강릉시",
                kakaoPlaceId = "kakao-chat-sys-system-confirm-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = ownerTripMember,
            ),
        )
        val voteItem = voteItemRepository.save(VoteItem(vote, place))
        voteUserRepository.save(VoteUser(vote, voteItem, ownerTripMember, 0))

        timelineService.expireAndConfirmBySystem(vote.id!!)

        assertSingleSystemMessage("1일차 9시 ${place.name} 확정!")
    }

    @Test
    @DisplayName("투표 생성 시 시스템 메시지가 기록된다")
    fun wrapperCreateVoteRecordsSystemMessage() {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )

        voteService.wrapperCreateVote(tripGroup.id!!, owner.id!!, timeline.id!!)

        assertSingleSystemMessage("1일차 시간 구간 투표 생성")
    }

    @Test
    @DisplayName("투표 현황 변경 시 시스템 메시지가 기록된다")
    fun saveVoteItemRecordsSystemMessage() {
        val timeline = timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1,
                startTime = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                endTime = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0),
            ),
        )
        val vote = voteRepository.save(
            Vote(tripGroup, timeline, ownerTripMember, tripGroup.startDate.minusDays(1).atStartOfDay()),
        )
        val place = tripPlaceRepository.save(
            TripPlace(
                tripGroup = tripGroup,
                name = "경포대",
                category = "관광",
                address = "강릉시",
                kakaoPlaceId = "kakao-chat-sys-item-${System.nanoTime()}",
                kakaoMapUrl = "url",
                createdBy = ownerTripMember,
            ),
        )

        voteItemService.saveVoteItem(tripGroup.id!!, owner.id!!, vote.id!!, place.id!!)

        assertSingleSystemMessage("1일차 9시 투표 현황 변경")
    }
}
