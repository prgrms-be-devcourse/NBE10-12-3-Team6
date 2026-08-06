package csh.back.domain.trip.group.exception

// 여행 시작이 임박한(또는 이미 시작된/완료된) 방은 삭제 불가.
// 정책: startDate 기준 5일 이상 남았을 때만 삭제 허용 (TripGroupService.deleteGroup 참조).
class TripDeletionNotAllowedException(message: String) : RuntimeException(message)
