package csh.back.global.dto

import org.springframework.data.domain.Slice

// Spring Data의 Slice를 API 응답용 shape으로 변환하는 공용 wrapper.
// 왜 Slice를 그대로 직렬화하지 않는가:
//   기본 직렬화는 pageable/sort/first/last/empty 등 내부 필드가 그대로 노출되어
//   프론트 계약이 지저분해지고, Spring 버전 업그레이드 시 필드가 바뀔 위험이 있음.
// 왜 필드명이 content가 아니라 items인가:
//   기존 PastMatesSliceResponse에서 이미 items로 노출 중이고 프론트도 그 이름에 의존해
//   호환성을 위해 items로 통일. Spring Data 관용어(content)와는 다름을 주의.
// T : Any bound은 Spring Data Slice<T : Any>와 정합. 없으면 nullable T가 허용돼 컴파일 실패.
data class SliceResponse<T : Any>(
    val items: List<T>,
    val hasNext: Boolean,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun <T : Any> from(slice: Slice<T>): SliceResponse<T> = SliceResponse(
            items = slice.content,
            hasNext = slice.hasNext(),
            page = slice.number,
            size = slice.size,
        )
    }
}
