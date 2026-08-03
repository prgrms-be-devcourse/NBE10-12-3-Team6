package csh.back.domain.member.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import csh.back.domain.member.entity.Member

// @JsonInclude(NON_NULL): recoveryCode가 null인 경우(=signup 응답이 아닌 케이스) JSON에서 필드 자체가 생략됨.
// 기존 응답 형태 유지하면서 signup 응답에만 추가 필드가 노출되도록.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberResponseDto(
    val id: Long,
    val email: String,
    val name: String,
    // recovery code raw 값은 회원가입 응답에서 딱 한 번 유저에게 노출된 뒤 서버는 재현 불가 (해시만 보관).
    // 다른 조회 API에서는 사용하지 않으므로 nullable + NON_NULL 조합으로 안전 처리.
    val recoveryCode: String? = null,
) {
    companion object {
        // 일반 조회용 — recoveryCode 미포함
        fun from(member: Member) = MemberResponseDto(
            id = member.id!!,
            email = member.email,
            name = member.name,
        )

        // 회원가입 완료 직후 1회 응답용 — raw recovery code를 클라이언트에 전달.
        // 이 시점 이후에는 서버 어디에도 raw 코드가 남지 않으므로 유저가 반드시 저장해야 함.
        fun fromSignup(member: Member, recoveryCode: String) = MemberResponseDto(
            id = member.id!!,
            email = member.email,
            name = member.name,
            recoveryCode = recoveryCode,
        )
    }
}
