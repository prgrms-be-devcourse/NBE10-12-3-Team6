package csh.back.global.initData;

import csh.back.domain.member.entity.Member;
import csh.back.domain.member.repository.MemberRepository;
import csh.back.domain.member.service.MemberService;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@ActiveProfiles("test")
@Configuration
@RequiredArgsConstructor
public class TestInitData {

	@Autowired
	@Lazy
	private TestInitData self;
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final TripGroupRepository tripGroupRepository;
	private final TripMemberRepository tripMemberRepository;

	@Bean
	ApplicationRunner testInitDataApplicationRunner() {
		return args -> {
			self.work1();
			self.work2();
		};
	}

	@Transactional
	public void work1() {
		if (memberRepository.existsByEmail("admin@admin.com")) return;

		Member member1 = memberRepository.save(Member.builder()
				.email("admin@admin.com")
				.password(passwordEncoder.encode("1234"))
				.name("admin")
				.build());

		Member member2 = memberRepository.save(Member.builder()
				.email("member2@admin.com")
				.password(passwordEncoder.encode("1234"))
				.name("member2")
				.build());

		Member member3 = memberRepository.save(Member.builder()
				.email("member3@admin.com")
				.password(passwordEncoder.encode("1234"))
				.name("member3")
				.build());
	}

	@Transactional
	public void work2() {
		if (tripGroupRepository.count() > 0) return;

		Member member1 = memberRepository.findById(1L).get();
		Member member2 = memberRepository.findById(2L).get();
		Member member3 = memberRepository.findById(3L).get();

		TripGroup tripGroup1 = tripGroupRepository.save(
				TripGroup.builder()
						.owner(member1)
						.name("여행1")
						.region("지역1")
						.nights(2)
						.joinCode("Ad13dct")
						.startDate(LocalDate.parse("2026-07-01"))
						.endDate(LocalDate.parse("2026-07-03"))
						.build());
		tripMemberRepository.save(
				TripMember.builder()
						.tripGroup(tripGroup1)
						.member(member1)
						.isAdmin(true)
						.build()
		);

		TripGroup tripGroup2 = tripGroupRepository.save(
				TripGroup.builder()
						.owner(member2)
						.name("여행2")
						.region("지역2")
						.nights(2)
						.joinCode("Ad14dct")
						.startDate(LocalDate.parse("2026-07-02"))
						.endDate(LocalDate.parse("2026-07-05"))
						.build());
		tripMemberRepository.save(
				TripMember.builder()
						.tripGroup(tripGroup2)
						.member(member2)
						.isAdmin(true)
						.build()
		);

		TripGroup tripGroup3 = tripGroupRepository.save(
				TripGroup.builder()
						.owner(member1)
						.name("여행3")
						.region("지역3")
						.nights(3)
						.joinCode("Ad33dct")
						.startDate(LocalDate.parse("2026-07-10"))
						.endDate(LocalDate.parse("2026-07-13"))
						.build());
		tripMemberRepository.save(
				TripMember.builder()
						.tripGroup(tripGroup3)
						.member(member1)
						.isAdmin(true)
						.build()
		);

		tripMemberRepository.save(
				TripMember.builder()
						.tripGroup(tripGroup1)
						.member(member3)
						.build()
		);
		tripMemberRepository.save(
				TripMember.builder()
						.tripGroup(tripGroup2)
						.member(member3)
						.build()
		);
	}
}
