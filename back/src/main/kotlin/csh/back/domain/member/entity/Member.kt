package csh.back.domain.member.entity

import csh.back.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "members")
// @JvmOverloads: provider/providerId에 기본값이 있어 Kotlin끼리는 생략 가능하지만,
// Java는 기본값을 인식 못해 3인자 생성자가 없어 컴파일 에러 발생 → Java 테스트 코드 호환용
class Member @JvmOverloads constructor(
    val email: String,
    val password: String,
    val name: String,
    // 소셜 로그인 식별자 (A-lite 방식): 이메일 대신 provider+providerId 조합으로 소셜 회원 조회
    // email/password 컬럼을 nullable로 전환하지 않고, 카카오 가입 시 placeholder 값으로 채움
    val provider: String = "LOCAL",   // "LOCAL" | "KAKAO"
    val providerId: String? = null,   // 카카오 회원번호 (소셜 로그인만 사용, 일반 회원은 null)
) : BaseEntity()