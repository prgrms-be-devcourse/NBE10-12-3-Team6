package csh.back.global.initData

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@ActiveProfiles("test")
@Configuration
class TestInitData(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
) {

    @Autowired
    @Lazy
    lateinit var self: TestInitData

    @Bean
    fun testInitDataApplicationRunner() = ApplicationRunner {
        self.work1()
        self.work2()
    }

    @Transactional
    fun work1() {
        if (memberRepository.existsByEmail("admin@admin.com")) return

        val password = passwordEncoder.encode("1234")!!
        memberRepository.save(Member("admin@admin.com", password, "admin"))
        memberRepository.save(Member("member2@admin.com", password, "member2"))
        memberRepository.save(Member("member3@admin.com", password, "member3"))
    }

    @Transactional
    fun work2() {
        if (tripGroupRepository.count() > 0) return

        val member1 = memberRepository.findById(1L).get()
        val member2 = memberRepository.findById(2L).get()
        val member3 = memberRepository.findById(3L).get()

        val tripGroup1 = tripGroupRepository.save(
            TripGroup(
                owner = member1,
                name = "여행1",
                region = "지역1",
                nights = 2,
                joinCode = "Ad13dct",
                startDate = LocalDate.parse("2026-07-01"),
                endDate = LocalDate.parse("2026-07-03"),
            )
        )
        tripMemberRepository.save(
            TripMember(member = member1, tripGroup = tripGroup1, isAdmin = true)
        )

        val tripGroup2 = tripGroupRepository.save(
            TripGroup(
                owner = member2,
                name = "여행2",
                region = "지역2",
                nights = 2,
                joinCode = "Ad14dct",
                startDate = LocalDate.parse("2026-07-02"),
                endDate = LocalDate.parse("2026-07-05"),
            )
        )
        tripMemberRepository.save(
            TripMember(member = member2, tripGroup = tripGroup2, isAdmin = true)
        )

        val tripGroup3 = tripGroupRepository.save(
            TripGroup(
                owner = member1,
                name = "여행3",
                region = "지역3",
                nights = 3,
                joinCode = "Ad33dct",
                startDate = LocalDate.parse("2026-07-10"),
                endDate = LocalDate.parse("2026-07-13"),
            )
        )
        tripMemberRepository.save(
            TripMember(member = member1, tripGroup = tripGroup3, isAdmin = true)
        )

        tripMemberRepository.save(
            TripMember(member = member3, tripGroup = tripGroup1, isAdmin = false)
        )
        tripMemberRepository.save(
            TripMember(member = member3, tripGroup = tripGroup2, isAdmin = false)
        )
    }
}
