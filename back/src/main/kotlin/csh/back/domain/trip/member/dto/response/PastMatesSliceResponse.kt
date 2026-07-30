package csh.back.domain.trip.member.dto.response

import org.springframework.data.domain.Slice

data class PastMatesSliceResponse(
    val items: List<PastMateResponse>,
    val hasNext: Boolean,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun from(slice: Slice<PastMateResponse>) = PastMatesSliceResponse(
            items = slice.content,
            hasNext = slice.hasNext(),
            page = slice.number,
            size = slice.size,
        )
    }
}
