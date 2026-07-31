# 타임라인 자유시간 DB 저장

## **📄 작업 요청 내용**

여행 당일의 일반 시간 구간을 기준으로 비어 있는 시간을 찾아 각 여행 일차의 `freeTimeMinutes` 단위로 나누고, 마지막 자투리까지 `자유시간` 타임라인으로 DB에 한 번만 저장한다.

## **✅ 작업 상세 리스트**

- [x] 타임라인 엔티티에 자유시간 식별값 `isFreeTime` 추가
- [x] 일반 시간 구간과 자유시간의 생성 메서드 분리
- [x] 오늘이 실제 여행 기간에 포함되는지 검증
- [x] 해당 일차의 일반 시간 구간을 시작 시각 순서로 조회
- [x] 1일차에 계획이 있으면 첫 계획 이전을 제외하고 계획 사이와 마지막 계획 이후를 계산
- [x] 2일차부터 계획이 있으면 첫 계획 이전·계획 사이·마지막 계획 이후를 모두 계산
- [x] 계획이 하나도 없는 일차는 00:00부터 23:59까지 계산
- [x] `freeTimeMinutes`를 30분 단위·최소 30분·최대 3시간으로 검증
- [x] 모든 빈 구간을 설정 단위로 나누어 저장
- [x] 설정 단위보다 짧은 마지막 자투리 구간도 남은 시간만큼 저장
- [x] 겹치는 일반 시간 구간은 가장 늦은 종료 시각을 기준으로 병합해 자유시간 중복 방지
- [x] 여행방 단위 비관적 잠금과 생성 완료 일차 기록으로 중복 저장 방지
- [x] 여행방 설정이 없으면 기본 설정 `freeTimeMinutes=60` 생성
- [x] 매일 00:00 KST에 진행 중인 여행방의 당일 자유시간 생성
- [x] 스케줄 실행을 놓친 경우 타임라인 목록·개수 조회 시 당일 자유시간 생성
- [x] 자유시간은 투표를 만들지 않고 응답에서 `자유시간`, `isFreeTime=true`, `voteId=null` 반환
- [x] 새 여행방 생성 시 기본 자유시간 범위 60분 설정 생성
- [x] 여행 일차별 자유시간 범위를 별도 테이블에 저장하고 기본값 60분 적용
- [x] 여행방 설정 조회 및 일차별 자유시간 범위 수정 API 추가
- [x] 모든 일차의 자유시간 범위를 한 트랜잭션으로 수정하는 일괄 PATCH API 추가
- [x] 여행 기간에 없는 일차의 설정 변경 차단
- [x] 여행방 멤버는 설정을 조회하고 방장만 수정하도록 권한 분리
- [x] 홈 하단 내비게이션에 설정 아이콘 추가
- [x] 홈 하단 내비게이션의 사용자 아이콘을 플로팅 바 오른쪽 독립 버튼으로 분리
- [x] 여행 상세의 각 일차 카드 아래에 방장 전용 `±30분` 설정 UI 추가
- [x] `일차별 계획` 오른쪽에 방장 전용 일괄 설정 아이콘과 하단 시트 추가
- [x] 하단 시트에서 30분 단위로 선택하고 `모두 적용` 성공 후 시트 닫기
- [x] 방장이 아닌 멤버에게는 자유시간 설정 UI를 표시하지 않음
- [x] 각 일차의 `자유시간 범위` 아래에 방장 권한과 여행 시작일부터 변경 제한 안내 표시
- [x] 여행 시작일부터 설정 UI를 숨기고 서버에서도 변경 요청 거부
- [x] 자유시간 범위 변경 SSE 이벤트를 분리하고 다건 알림을 `자유시간 범위 변경 N건`으로 요약
- [x] 설정 페이지에 시스템 설정·라이트 모드·다크 모드 선택 추가
- [x] 선택한 테마를 기기에 저장하고 시스템 테마 변경 자동 반영
- [x] 운영체제 다크모드 CSS가 사용자의 라이트모드 고정값을 덮어쓰지 않도록 테마 선택 우선순위 수정
- [x] 자유시간 분할·기본값·여행 기간·중복 방지·설정값 검증 통합 테스트 작성
- [x] 여행방 설정 조회·수정·권한·유효성 통합 테스트 작성
- [ ] 실제 운영 DB에서 Hibernate 스키마 갱신 결과 확인

## 변경 파일

- `back/src/main/kotlin/csh/back/domain/trip/timeline/entity/Timeline.kt`
  - `isFreeTime` 필드와 `createFreeTime` 팩터리 추가
- `back/src/main/kotlin/csh/back/domain/trip/timeline/repository/TimelineRepository.kt`
  - 자유시간 존재 여부, 시간순 일반 시간 구간, 진행 중 여행방 조회 쿼리 추가
- `back/src/main/kotlin/csh/back/domain/trip/timeline/service/TimelineFreeTimeService.kt`
  - 여행 일차 계산, 빈 구간 탐색, 일차별 설정 조회, 자유시간 분할·저장, 중복 방지 로직 구현
- `back/src/main/kotlin/csh/back/domain/trip/timeline/scheduler/TimelineFreeTimeScheduler.kt`
  - 매일 당일 자유시간을 생성하는 스케줄러 추가
- `back/src/main/kotlin/csh/back/domain/trip/timeline/service/TimelineService.kt`
  - 목록·개수 조회 전에 누락된 당일 자유시간 생성
- `back/src/main/kotlin/csh/back/domain/trip/timeline/dto/response/TimelineResponse.kt`
  - `isFreeTime` 응답 필드 추가
- `back/src/main/kotlin/csh/back/domain/trip/timeline/dto/response/TimelineWithVoteIdResponse.kt`
  - 자유시간 이름과 자유시간 여부 반환
- `back/src/main/kotlin/csh/back/domain/trip/group/settings/entity/TripGroupSettings.kt`
  - 일차별 자유시간 범위 맵, 개별·전체 수정, 마지막 생성 완료 일차, 30분~3시간 설정값 검증
  - `trip_day_free_time_settings` 컬렉션 테이블 매핑
- `back/src/main/kotlin/csh/back/domain/trip/group/settings/controller/TripGroupSettingsV1Controller.kt`
  - 여행방 설정 조회와 `dayNumber`별 개별·모든 일차 일괄 자유시간 범위 수정 API 제공
- `back/src/main/kotlin/csh/back/domain/trip/group/settings/service/TripGroupSettingsService.kt`
  - 설정 생성·조회·개별·일괄 수정, 여행 일차, 멤버/방장 권한 검증
- `back/src/main/kotlin/csh/back/domain/trip/group/settings/dto/**`
  - 자유시간 범위 수정 요청과 설정 응답 정의
- `back/src/main/kotlin/csh/back/domain/trip/group/service/TripGroupService.kt`
  - 새 여행방 생성 시 기본 설정 함께 저장
- `back/src/test/kotlin/csh/back/domain/trip/group/settings/controller/TripGroupSettingsV1ControllerTest.kt`
  - 일차별 설정 API, 30분 단위, 최대 3시간, 여행 일차 및 방장 권한 검증
- `back/src/test/kotlin/csh/back/domain/trip/timeline/service/TimelineFreeTimeServiceTest.kt`
  - 1일차·이후 일차의 앞·중간·뒤 빈 구간, 무계획 일차, 자투리, 일차별 설정 및 중복 방지 검증
- `front/src/app/components/HomeBottomNavigation.tsx`
  - 홈·내 정보·설정 공통 하단 내비게이션 추가
- `front/src/app/settings/page.tsx`
  - 시스템 설정·라이트 모드·다크 모드 선택 화면 추가
- `front/src/app/layout.tsx`
  - 저장된 테마를 앱 시작 전에 적용하고 시스템 테마 변경 구독
- `front/src/app/home/page.tsx`
  - 기존 하단 내비게이션을 공통 컴포넌트로 교체
- `front/src/app/trip/[id]/page.tsx`
  - 각 일차 카드 하단의 개별 조절 UI와 모든 일차 일괄 설정 하단 시트 추가
- `front/src/app/trip/[id]/TripEventProvider.tsx`
  - 자유시간 범위 변경 이벤트의 단건 메시지 및 다건 묶음 알림 처리
- `front/src/app/globals.css`
  - 2개 탭 인디케이터, 독립 사용자 버튼과 설정 페이지 스크롤 레이아웃 추가

## **📍 기타**

- 자유시간 생성 가능 시간대는 해당 일차의 `00:00~23:59`이다.
- 1일차에 계획이 있으면 여행 시작 전으로 보는 `00:00~첫 계획 시작 시각`은 자유시간으로 만들지 않는다.
- 2일차부터는 첫 계획 이전도 자유시간으로 만들며, 모든 일차에서 계획 사이와 마지막 계획 이후를 채운다.
- 계획이 없는 일차는 `00:00~23:59` 전체를 자유시간으로 채운다.
- 30분 설정에서 빈 구간이 70분이면 30분·30분·10분 자유시간으로 나누어 저장한다.
- 자유시간 범위 변경값은 이미 DB에 생성된 자유시간을 다시 나누지 않고, 아직 자유시간이 생성되지 않은 여행 일차부터 적용한다.
- 여행방의 공유 일정에 영향을 주는 값이므로 방장에게만 일차별 설정 UI를 표시한다.
- 화면 모드 설정은 계정이나 서버가 아니라 현재 기기의 `localStorage`에 저장한다.
- Post 도메인의 운영 코드는 이번 작업에서 변경하지 않았다. 저장된 자유시간은 확정 장소가 없으므로 기존 Post 표시 흐름에서 자유시간으로 취급할 수 있다.

## 검증 결과

- [x] `./gradlew compileKotlin`
- [x] `./gradlew compileTestKotlin`
- [x] 자유시간 생성 및 여행방 설정 통합 테스트 16건
  - 자유시간 통합 테스트 7건
  - 여행방 설정 컨트롤러 통합 테스트 9건
- [x] `pnpm build`
- [x] 테스트 프로필 전체 백엔드 테스트 119건
  - `SPRING_PROFILES_ACTIVE=test ./gradlew test`
  - 실패 0건
- 기본 dev 프로필로 전체 테스트를 실행하면 기존 `BackApplicationTests.contextLoads()`가 `MAIL_USERNAME` 환경 변수를 요구하므로, 로컬 전체 테스트에는 test 프로필이 필요하다.
