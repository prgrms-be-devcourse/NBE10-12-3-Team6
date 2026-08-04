package csh.back.domain.trip.group.dto.request

import jakarta.validation.constraints.NotEmpty

// 다중 삭제 요청 body. DELETE 메서드에 body를 넣는 케이스가 프록시에 따라 잘려서
// 컨트롤러는 POST /trips/bulk-delete로 노출한다.
data class TripGroupBulkDeleteRequest(
    @field:NotEmpty(message = "삭제할 모임방을 하나 이상 선택해주세요.")
    val ids: List<Long>,
)
