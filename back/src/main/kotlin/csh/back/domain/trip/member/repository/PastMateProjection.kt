package csh.back.domain.trip.member.repository

import java.time.LocalDate

// 지난 메이트 조회용 Spring Data 인터페이스 프로젝션.
// Repository가 dto.response에 의존하지 않도록 여기서 쿼리 결과 shape을 정의하고,
// DTO 변환은 서비스 계층에서 수행한다. 게터 이름은 JPQL SELECT 알리아스와 정확히 일치해야 매핑된다.
interface PastMateProjection {
    fun getId(): Long
    fun getName(): String
    fun getTravelCount(): Long
    fun getLatestTravelDate(): LocalDate

    // 가장 최근 함께한 여행방 이름.
    // 상관 서브쿼리로 조회하며, 같은 날짜 tie는 tripGroup.id MAX로 결정 → 결정적.
    fun getLatestGroupName(): String?
}
