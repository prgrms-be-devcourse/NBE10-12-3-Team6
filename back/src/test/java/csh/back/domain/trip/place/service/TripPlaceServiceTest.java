package csh.back.domain.trip.place.service;

import csh.back.domain.member.entity.Member;
import csh.back.domain.member.repository.MemberRepository;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse;
import csh.back.domain.trip.place.dto.response.TripPlaceSaveResponse;
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TripPlaceServiceTest {

    @Autowired
    private TripPlaceService tripPlaceService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TripGroupRepository tripGroupRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    private Member owner;
    private TripGroup tripGroup;

    @BeforeEach
    void setUp() {
        owner = memberRepository.save(Member.builder()
                .email("place-owner@test.com")
                .password("pw")
                .name("장소테스트유저")
                .build());

        tripGroup = tripGroupRepository.save(TripGroup.builder()
                .owner(owner)
                .name("장소테스트여행")
                .region("제주")
                .nights(2)
                .joinCode("PLACE-JOIN-1")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 3))
                .build());

        tripMemberRepository.save(TripMember.builder()
                .member(owner)
                .tripGroup(tripGroup)
                .isAdmin(true)
                .build());
    }

    @Test
    @DisplayName("위시 장소를 저장하면 목록 조회에 포함된다")
    void saveAndFind() {
        TripPlaceSaveResponse saved = tripPlaceService.savePlace(
                tripGroup.getId(), "한라산", "관광", "제주 한라산로", "kakao-1", "http://map/1", owner.getId());

        assertThat(saved.name()).isEqualTo("한라산");
        assertThat(saved.category()).isEqualTo("관광");
        assertThat(saved.address()).isEqualTo("제주 한라산로");

        List<TripPlaceFindResponse> found = tripPlaceService.findWishPlaces(tripGroup.getId(), owner.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("한라산");
        assertThat(found.get(0).category()).isEqualTo("관광");
        assertThat(found.get(0).createdBy()).isEqualTo(owner.getName());
    }

    @Test
    @DisplayName("같은 카카오 장소 ID로 같은 모임에 중복 저장하면 예외가 발생한다")
    void duplicateSaveThrows() {
        tripPlaceService.savePlace(tripGroup.getId(), "한라산", "관광", "주소", "kakao-dup", "url", owner.getId());

        assertThatThrownBy(() ->
                tripPlaceService.savePlace(tripGroup.getId(), "한라산2", "관광2", "주소2", "kakao-dup", "url2", owner.getId())
        ).isInstanceOf(DuplicateTripPlaceException.class);
    }

    @Test
    @DisplayName("모임 멤버가 아닌 유저가 위시 장소를 조회하면 예외가 발생한다")
    void findByNonMemberThrows() {
        Member outsider = memberRepository.save(Member.builder()
                .email("outsider@test.com").password("pw").name("외부인").build());

        assertThatThrownBy(() -> tripPlaceService.findWishPlaces(tripGroup.getId(), outsider.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("모임 멤버가 아닌 유저가 위시 장소를 저장하면 예외가 발생한다")
    void saveByNonMemberThrows() {
        Member outsider = memberRepository.save(Member.builder()
                .email("outsider2@test.com").password("pw").name("외부인2").build());

        assertThatThrownBy(() ->
                tripPlaceService.savePlace(tripGroup.getId(), "한라산", "관광", "주소", "kakao-x", "url", outsider.getId())
        ).isInstanceOf(RuntimeException.class);
    }
}
