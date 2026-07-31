# 익명 투표 프론트 연결 가이드

## 현재 구현

- `일차별 투표` 화면의 방장 메뉴에서 여행방의 `익명 투표` 기본값 토글을 표시한다.
- 기본값 메뉴에는 `투표 확정` 버튼을 표시하지 않고 `투표 기본값` 설명을 표시한다.
- 방장 메뉴의 `투표 확정` 버튼 위에 `익명 투표` 토글을 표시한다.
- 방장이 아닌 사용자는 기존과 동일하게 해당 메뉴를 볼 수 없다.
- 현재는 프론트 로컬 상태만 변경하므로 새로고침하면 기본값인 익명 투표 상태로 돌아간다.
- 백엔드 API 요청은 포함하지 않았다.

## 백엔드 연결 지점

- 여행방 기본값
  - 대상 파일: `front/src/app/trip/[id]/page.tsx`
  - 여행방 설정 조회 응답의 `isAnonymousVote`로 `isAnonymousVoteDefault`를 초기화한다.
  - `toggleAnonymousVoteDefault`에서 여행방의 기본 투표 설정 변경 API를 호출한다.
- 대상 파일: `front/src/app/trip/[id]/day/[dayNumber]/block/[blockId]/page.tsx`
- `fetchVoteDetails`에서 투표 설정 조회값을 토글 상태에 반영한다.
  - 백엔드 필드 `isAnonymousVote`를 `setIsAnonymousVote(isAnonymousVote)`로 적용한다.
- `toggleAnonymousVote`에서 설정 변경 API를 호출한다.
  - 프론트의 `nextIsAnonymousVote`를 백엔드의 `isAnonymousVote`로 그대로 저장한다.
  - 요청 실패 시 이전 토글 상태로 복원하고 오류 안내를 표시한다.
- `투표 진행중`에는 토글을 조작할 수 있다.
- `투표 확정` 또는 `투표 기한 만료` 상태에서만 토글이 어두운 색으로 비활성화된다.
