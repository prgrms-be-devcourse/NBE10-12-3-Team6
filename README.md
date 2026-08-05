# 📝 TripLog 2차

> 
기준: 1차 `NBE10-12-2-Team6@dev` ↔︎ 2차 `NBE10-12-3-Team6@dev`
대상: 1차 MVP 완료 이후 ~ 2차 MVP 종료 시점까지
>

---

## 1. 배경 — 2차 MVP는 이미 절반 이상 진행되었다

1차 MVP는 **“여행 계획을 함께 세우고 사진으로 기록한다”는 최소 흐름**을 완성하는 데 집중했다.
이후 2차 MVP 개발이 계속 진행되어, 현재 **인증 구조 재설계 · 실시간 채팅 · 푸시 알림 · 사진 파이프라인 고도화 · 동시성 방어**가 이미 들어와 있다.

따라서 이 문서는 “앞으로 만들 것”만 담은 기획서가 아니라,

> **① 2차 MVP에서 이미 완료한 것을 확정 기록하고
② 사진 도메인을 “한 장”에서 “여행 앨범”으로 확장하고
③ 계획 과정의 소통 공백(댓글·메모)을 채우고
④ 지금까지 쌓인 기능을 실서비스 수준으로 굳히는(안정성·운영)**
>

2차 MVP의 **중간 점검 + 잔여 계획서**로 작성했다.

### 1.1 가장 큰 변화 — Java → Kotlin 전면 전환

1차 레포는 `src/main/java` 기반(Lombok + `@Builder`)이었고, 2차는 **`src/main/kotlin`으로 전면 재작성**되었다.
단순 문법 변환이 아니라 도메인 모델의 표현 방식이 바뀌었다.

| 관점 | 1차 (Java) | 2차 (Kotlin) |
| --- | --- | --- |
| 엔티티 | Lombok `@Getter` + `@Builder` + `@NoArgsConstructor` | 주 생성자 + `protected set`으로 캡슐화 |
| Null 처리 | 런타임 `if (x == null) throw` | 타입 시스템(`?`) + `requireNotNull` |
| 검증 | 수동 if-throw 나열 | `require { }` 기반 선언적 검증 |
| 상수/정책 | `private static final` | `companion object` |

---

## 2. 1차 MVP 최종 산출물

> 여행 서비스가 성립하기 위한 최소 골격. 여기까지가 1차 범위다.
>

### 2.1 회원 / 인증

| 기능 | 상태 | 비고 |
| --- | --- | --- |
| 이메일 회원가입 | ✅ | 이메일 중복 체크(`ExistingMemberException`) |
| 로그인 / 로그아웃 | ✅ | `POST /auth/login`, `/auth/logout` |
| JWT 발급 및 쿠키 전달 | ✅ | accessToken 30분 / refreshToken 7일, `httpOnly` + `SameSite=Lax` |
| Authorization 헤더 하이브리드 | ✅ | v1 프론트 호환용으로 쿠키와 동일 토큰을 헤더로도 전달 |
| accessToken 자동 재발급 | ✅ | `JwtAuthenticationFilter`가 만료 감지 시 재발급 |

**1차 인증 흐름 (쿠키 구조)**

```
로그인
 └ MemberService.login()
     ├ accessToken  : JWT (subject=email, claim=memberId, 30분)
     └ refreshToken : Member.refreshToken 컬럼의 UUID 값 (JWT 아님, opaque)
         → 두 값을 각각 httpOnly 쿠키로 Set-Cookie
         → 동시에 Authorization 헤더로도 전달 (v1 호환)

요청 시
 └ JwtAuthenticationFilter
     ├ accessToken 유효  → SecurityContext 인증 세팅 (DB 조회 없음)
     └ accessToken 만료  → refreshToken UUID로 DB 조회 후 accessToken 재발급

로그아웃
 └ Member.invalidateRefreshToken() : UUID를 새 값으로 교체
     → 기존 refreshToken 즉시 무효화
     → accessToken은 즉시 무효화되지 않고 최대 30분 후 자연 만료
```

> ⚠️ **1차 구조의 한계** — `refreshToken`이 `Member` 엔티티의 **단일 컬럼**이라 회원당 세션이 1개뿐이다.
따라서 로그아웃하면 **모든 기기가 함께 로그아웃**되고, “이 기기만 유지” 같은 요구를 구조적으로 지원할 수 없었다.
이 제약이 2차 인증 재설계의 직접적인 출발점이 된다. (→ 3.1)
>

### 2.2 여행 모임 / 멤버

| 기능 | 상태 | 비고 |
| --- | --- | --- |
| 모임방 생성 / 조회 / 수정 | ✅ | 지역·기간·박수, 키워드/시작일 검색, 무한 스크롤 |
| 초대 코드 참여 | ✅ | `joinCode` 7자리 랜덤. 로그인 시 `joinCode` 동봉하면 로그인과 동시에 참여 |
| 방장 권한 구분 | ✅ | `TripMember.isAdmin` |

### 2.3 계획 / 타임라인 / 투표

| 기능 | 상태 | 비고 |
| --- | --- | --- |
| 타임라인 단건 / 일괄 생성, 수정, 삭제 | ✅ | 일차별 시간 구간 관리 |
| 타임라인 변경 SSE 알림 (기본형) | ✅ | `TimeLineEventService` — `TIMELINE_UPDATED` **단일 이벤트**, 고정 문구 “새로운 변경 사항이 있습니다.” |
| 자유시간 표시 (온디맨드 계산) | ✅ | **DB 저장 없음.** 조회 시점에 기존 타임라인 사이 공백을 1시간 단위로 계산해 표시 |
| 위시 장소 등록 / 조회 | ✅ | 카카오맵 연동. `uk_wish_place` 유니크 제약 + `DuplicateTripPlaceException` |
| 투표 생성 / 참여 / 결과 조회 | ✅ | 만료 시간 지정, 최대 2회 재투표 |
| 투표 만료 자동 집계 | ✅ | **Spring Batch** + 스케줄러 (`VoteExpireBatchConfig`) |
| 동률 시 랜덤 확정 | ✅ | `ThreadLocalRandom`으로 최다 득표 동률 후보 중 1개 선택 |

> 1차 시점에는 **위시 장소 삭제 기능이 없었고**(`TripPlaceService`에 `deletePlace` 부재), 투표는 익명/실명 구분 없이 단일 모드였다.
>

### 2.4 게시글 / 사진

| 기능 | 상태 | 비고 |
| --- | --- | --- |
| 사진 게시글 생성 / 조회 / 수정 / 삭제 | ✅ | 커서 기반 10건 페이징 |
| 시간 구간당 1장 제한 | ✅ | `GET /posts/is-taken` |
| S3 이미지 업로드 | ✅ | 원본 단일 저장 |

### 2.5 인프라

| 기능 | 상태 | 비고 |
| --- | --- | --- |
| PWA | ✅ | standalone 설치 |
| CI/CD | ✅ | GitHub Actions + Docker |
| 모니터링 | ✅ | Actuator + Prometheus |

---

## 3. 2차 MVP 완료 산출물 (현재까지)

> 2차 MVP 기간에 실제로 개발되어 `dev`에 병합된 기능. 1차 기획서의 2차 항목 번호를 `#N`으로 병기했다.
>

### 3.1 A. 회원 / 인증 — 세션 구조 재설계

2차 인증 작업의 핵심은 기능 추가가 아니라 **“회원당 세션 1개” → “기기당 세션 1개”로의 구조 변경**이다. 나머지 기능 대부분이 이 변경 위에 얹혀 있다.

**변경된 세션 구조**

| 관점 | 1차 | 2차 |
| --- | --- | --- |
| refreshToken 저장 위치 | `Member.refreshToken` **단일 컬럼** | **`RefreshToken` 독립 엔티티** (`member_id` + `device_id` 유니크) |
| 동시 로그인 | 사실상 1기기 | 기기별 독립 토큰 → **멀티 디바이스** |
| 기기 식별 | 없음 | `DeviceIdFilter` — `device_id` 쿠키(maxAge 10년), 로그아웃해도 유지되어 재로그인 시 동일 기기로 인식 |
| 토큰 갱신 | UUID 재사용 | **Rotation** — 갱신 시 새 토큰 발급 + 구 토큰 무효화 |
| 만료 관리 | 쿠키 maxAge에만 의존 | `RefreshToken.expiresAt`(7일)로 **쿠키·DB 만료 시점 일치** |
| 선택적 로그아웃 | 불가능 | 가능 (기기 단위) |

**이 구조 위에 올라간 기능**

| 기능 | 항목 | 비고 |
| --- | --- | --- |
| 이메일 인증코드 발송 / 검증 | — | `MailService` + `EmailCooldownGuard`(재발송 쿨다운) |
| 카카오 소셜 로그인 | `#1` | `provider` + `providerId` 조합 식별. 이메일에 의존하지 않아 로컬 회원과 충돌 없음 |
| 비밀번호 변경 (로그인 상태) | — | **현재 기기만 남기고 다른 기기 전부 로그아웃** — 1차 단일 UUID 구조로는 불가능했던 기능 |
| 비밀번호 재설정 (비로그인) | — | 가입 시 1회 노출되는 **recovery code** 기반. 서버는 BCrypt 해시만 보관해 원본을 모름 |
| 로그인 브루트포스 방어 | — | `failedLoginCount` / `lockedUntil` 락아웃. 계정 열거 방지를 위해 실패 메시지 통일
**5회 실패 → 5분 잠금** |
| 새 기기 로그인 알림 | — | `RefreshToken.userAgent` + `deviceId`로 신규 기기 판별 후 알림 |
| 401 / 403 응답 계약 분리 | — | 아래 표 참고 |

**401 / 403 응답 계약**

| 상황 | HTTP | 응답 |
| --- | --- | --- |
| 로그인 안 됨 (토큰 없음/만료) | **401** | `{"statusCode":401,"message":"인증이 필요합니다."}` |
| 로그인은 됐으나 방 멤버 아님 | **403** | `{"statusCode":403,"message":"해당 모임의 멤버가 아닙니다."}` |
| 모임 자체가 없음 | 404 | `{"statusCode":404,"message":"존재하지 않는 모임입니다."}` |

> 분리 전에는 `authenticationEntryPoint`가 미인증에도 403을 반환해 **미인증과 비회원을 프론트에서 구분할 수 없었고**, 프론트 `apiFetch`의 401 처리 코드가 실행될 일이 없는 죽은 코드였다.
함께 Next.js `middleware.ts`를 도입해 페이지 진입 자체를 서버사이드에서 차단하고, `apiFetch`는 응답 기반 2차 방어를 담당하도록 역할을 나눴다.
>

### 3.2 B. 모임방 / 멤버

| 기능 | 항목 | 비고 |
| --- | --- | --- |
| 모임방 삭제 (단건 / 다중) | `#16` 부분 | 방장만, 여행 시작 전날까지. Soft delete(`@SQLDelete` + `@SQLRestriction`) |
| 지난 여행 메이트 조회 + 초대 | `#2` | `GET /trips/past-members` 무한 스크롤(slice pagnation 방식) + 이름 검색, 방장 전용 초대 |
| 접속 상태(Presence) | `#11` 연계 | SSE 기반 온/오프라인 실시간 표시, 로그아웃 시 즉시 오프라인 |

> **`#2` 친구 관리 방향 확정** — 별도 친구 도메인(친구 요청·수락·목록)은 만들지 않는다.
TripLog의 관계는 **“함께 여행한 사이”** 로 정의하고, 새 모임방을 만들 때 지난 여행 메이트를 조회해 초대하는 흐름으로 대체한다.
친구 요청/수락 단계가 없어 진입 마찰이 적고 별도 관계 데이터를 관리할 필요도 없으므로 이 방식으로 **완료 처리**한다.
>

### 3.3 C. 계획 / 타임라인 / 투표

이 파트는 신규 기능보다 **1차 구현의 구조적 한계를 걷어낸 작업**이 중심이다.

#### ① 타임라인 변경 SSE 알림 고도화

| 관점 | 1차 | 2차 |
| --- | --- | --- |
| 이벤트 종류 | `TIMELINE_UPDATED` 1종 | **`TripEventType` 13종** (타임라인·투표·장소·멤버·설정) |
| 메시지 | 고정 문구 “새로운 변경 사항이 있습니다.” | 이벤트별 상세 메시지 (예: “2일차 14시 성산일출봉 확정!”) |
| 채널 | 타임라인 전용 | 타임라인 + **여행방 공통 이벤트 채널** 신설 |

**알림 출력 규칙 (규칙 기반 알고리즘)**

- **1건일 때** — 해당 이벤트의 **상세 메시지를 그대로 출력**
- **다건일 때** — 여러 건을 **취합하여 “x건의 변경사항이 발생했습니다”** 로 출력

> 이벤트마다 알림을 그대로 띄우면 일괄 생성·연속 수정 시 알림이 쏟아진다. 그렇다고 항상 요약하면 단일 변경의 맥락이 사라진다.
그래서 **건수에 따라 상세/요약을 전환하는 규칙 기반 알고리즘**을 적용했다. 모든 이벤트는 트랜잭션 커밋 이후에 발행되어, 롤백된 변경이 알림으로 새어나가지 않는다.
>

#### ② 자유시간 로직 — 계산 방식에서 저장 방식으로

| 관점 | 1차 | 2차 |
| --- | --- | --- |
| 저장 여부 | **DB에 저장하지 않음** | **DB에 자유시간 타임라인으로 저장** (`Timeline.isFreeTime`) |
| 생성 시점 | 조회할 때마다 다른 타임라인을 가져와 공백을 계산 | **계획용 타임라인 생성 시점**에 공백을 나눠 저장 |
| 단위 | 1시간 고정 | **1시간 ~ 3시간, 사용자 설정** (30분 단위) |
| 설정 저장소 | 없음 | `TripGroupSettings` — 일차별 개별 설정 + 전체 일괄 설정 |

> 조회 시마다 계산하는 방식은 자유시간이 실체가 없어 **투표나 사진을 자유시간에 붙일 수 없었다.** 저장 방식으로 바꾸면서 자유시간도 다른 타임라인과 동일하게 다룰 수 있게 됐다.
일차별 설정은 `TripGroupSettings.freeTimeMinutesByDay`(Map)로, 미설정 일차는 기본값 `freeTimeMinutes`로 폴백한다.
>

#### ③ 위시 장소 등록 — 동시성 3중 방어

여러 멤버가 같은 장소를 동시에 등록하는 상황을 **프론트엔드 · 백엔드 · DB 세 계층에서 방어**한다.

| 계층 | 방어 방식 |
| --- | --- |
| 프론트엔드 | 이미 등록된 장소는 등록 버튼 비활성화 |
| 백엔드 | `existsByKakaoPlaceIdAndTripGroupId` 사전 체크 |
| DB | `uk_wish_place`(kakao_place_id + trip_group_id) 유니크 제약 |

사전 체크를 뚫고 들어온 경합은 `saveAndFlush` 시점의 `DataIntegrityViolationException`을 잡아 `DuplicateTripPlaceException`으로 변환한다. **사전 체크만으로는 두 요청이 동시에 통과할 수 있으므로 DB 제약이 최종 방어선**이다.

#### ④ 위시 장소 삭제 — 신규 + 동시성 방어

1차에는 삭제 기능 자체가 없었다. 2차에서 추가하며 검증 단계를 뒀다.

1. 작성자 본인 확인
2. 여행 시작 전인지 확인
3. 이미 투표에 사용된 장소인지 확인 (`voteItemRepository.existsByTripPlaceId`)

삭제 직전에 다른 멤버가 그 장소로 투표를 생성하는 경합은 `delete` + `flush` 시점의 `DataIntegrityViolationException`을 잡아 `WishPlaceInUseException`으로 변환해 **백엔드에서 방어**한다.

> 프론트엔드에서 **등록한 사람만 삭제 가능**하도록 제한하므로 실제 경합 발생 가능성은 낮지만, 방어 코드는 두었다.
>

#### ⑤ 투표 참여 — 동시성 버그 수정

| 관점 | 1차 | 2차 |
| --- | --- | --- |
| 동시에 같은 장소 투표 | **에러 반환** (유니크 제약 위반이 그대로 노출) | **2표가 정상 처리** |

**수정 방식** — 경합에서 진 요청이 예외로 끝나지 않고, 이긴 쪽이 만든 row를 재조회해 정상 흐름으로 이어간다.

```
insert 시도
 ├ 성공         → 그대로 반환
 └ 실패(경합)    → 이긴 쪽의 row 재조회
     ├ 다른 장소에 투표했던 경우  → 재투표로 처리 (updateCount 증가)
     └ 같은 장소였던 경우        → 중복 요청일 뿐이므로 카운트 증가 안 함
```

> 같은 장소인지 구분하지 않으면 **단순 중복 클릭이 재투표 횟수를 깎아먹는다.** 재투표는 최대 2회로 제한되므로 이 구분이 필요하다.
>

#### ⑥ 익명 / 실명 투표 선택 기능

| 범위 | API | 설명 |
| --- | --- | --- |
| 방 전체 기본값 | `PATCH /trips/{id}/settings` | 방장이 변경 시 진행중(PENDING) 투표 전체에 일괄 반영 |
| 투표 1건 단위 | `PATCH /votes/{voteId}/anonymous` | 진행중인 투표만 개별 변경 가능 |

### 3.4 D. 사진 / 기록

| 기능 | 항목 | 비고 |
| --- | --- | --- |
| 이미지 3단 변환 저장 | `#3` 연계 | original / normal / data-saver(WebP) + `dominantColor` 추출 |
| 데이터 절약 모드 | — | 품질우선 / 균형 / 절약 3단계. 절약 모드는 확대 시에도 압축본 사용 |
| 사진 좋아요 | `#4` | `PostLike` 엔티티 + 등록 / 취소 / 상태 조회 |
| 과거여행 
리마인드 푸시 | `#11` | `PostReminderScheduler` + FCM, 중복 발송 방지 |

### 3.5 E. 실시간 / UX

| 기능 | 항목 | 비고 |
| --- | --- | --- |
| 실시간 채팅 | `#13` | STOMP over WebSocket, 커서 히스토리, 읽음 처리, 안읽음 배지, 유실 메시지 복구, 시스템 메시지 |
| 채팅 rate limit | `#15` 부분 | `StompChannelInterceptor` 사용자별 슬라이딩 윈도우 + 유휴 엔트리 정리 |
| 웹 푸시 | `#11` | Firebase Admin SDK, 디바이스 토큰 등록 / 해제 |
| 테마 선택 | `#9` | 시스템 / 라이트 고정 / 다크 고정 |

### 3.6 진행률 요약

| 구분 | 개수 |
| --- | --- |
| 1차 기획서의 2차 MVP 17개 항목 중 완료 | **7** (`#2`, `#4`, `#9`, `#11`, `#13`, `#17`, `#6`) |
| 1차 기획서의 2차 MVP 17개 항목 중 부분 완료 | **5** (`#1`, `#7`, `#10`, `#15`, `#16`) |
| 1차 기획서의 2차 MVP 17개 항목 중 미착수 | **5** (`#3`, `#5`, `#8`, `#12`, `#14`) |
| 기획서에 없던 추가 구현 | **15건 이상** (Kotlin 전환, 세션 재설계, 동시성 방어, 자유시간 저장 방식 전환 등) |

---

## 4. 2차 MVP 잔여 목표

### 4.1 한 줄 정의

> **“여행을 기록할 수 있는 서비스”에서 “여행을 다시 보고 싶은 서비스”로**
>

### 4.2 3대 축

| 축 | 목표 | 대표 기능 |
| --- | --- | --- |
| **A. 기록 깊이 확장** | 사진 1장 → 여행 앨범 | 다중 미디어, 위치 정보, 사진 모아보기, 다운로드 |
| **B. 계획 소통 보완** | 투표 찬반 외에 의견을 남길 곳 | 계획 카드 댓글, 개인 메모, 랜덤 투표 보완 |
| **C. 서비스 신뢰도** | 발표/시연에서 안 터지게 | 예외처리 전략, 멤버 관리 고도화, 캘린더 연동, 구글/애플 로그인 |

### 4.3 성공 기준 (Definition of Done)

- 신규 도메인 기능은 **MockMvc 기반 컨트롤러 통합 테스트**를 포함한다. (팀 테스트 컨벤션)
- 모든 신규 API는 Swagger(`@Operation`)에 등록되고 `docs/QA_CHECKLIST.md`에 검증 항목이 추가된다.
- 프론트 연동까지 완료되어야 “완료”로 본다. (API만 개발된 기능은 `API only`로 표기)

---

## 5. 2차 MVP 기능 현황표

> 1차 기획서의 2차 MVP 항목 번호를 그대로 유지했다.
>

| # | 기능 | 상태 | 판단 근거 |
| --- | --- | --- | --- |
| 1 | 소셜 로그인 | 🟡 부분 | **카카오 완료(2차)**. 구글/애플 미구현 |
| 2 | 친구 관리 | ✅ 완료 (2차) | **지난 여행 메이트 방식으로 확정.** 별도 친구 도메인은 만들지 않음 |
| 3 | 사진 위치 정보 / 다중 미디어 | 🔴 미구현 | `Post`에 좌표 컬럼 없음, `contentUrl` 단일 |
| 4 | 사진 공감(좋아요) | ✅ 완료 (2차) | `PostLike` + 3개 API |
| 5 | 사진 다운로드 | 🔴 미구현 | 프론트/백 모두 다운로드 경로 없음 |
| 6 | 타임라인 | ✅ 완료 | 1차 완료. 2차에서 자유시간 저장 방식·SSE 고도화 |
| 7 | 사진 모아보기 | 🟡 부분 | 일차별 사진 페이지만 존재. 여행 전체 앨범 없음 |
| 8 | 캘린더 연동 | 🔴 미구현 | 관련 코드 없음 |
| 9 | 테마 선택 | ✅ 완료 (2차) | 시스템 / 라이트 / 다크 |
| 10 | 랜덤 선택 | ✅ 완료 | **동률 시 랜덤 확정은 1차 완료.** 
의견없을 시 미확정 |
| 11 | 실시간 알림 | ✅ 완료 (2차) | SSE 3종(Presence/TripEvent/Timeline) + FCM 푸시 |
| 12 | 개인 메모장 | 🔴 미구현 | 관련 코드 없음 |
| 13 | 채팅 | ✅ 완료 (2차) | STOMP, 히스토리, 읽음, rate limit |
| 14 | 계획 카드 댓글 | 🔴 미구현 | 관련 코드 없음 |
| 15 | 예외처리(서킷브레이커·rate limit·재시도) | 🟡 부분 | 채팅 rate limit + 장소/투표 동시성 방어. resilience4j 미도입 |
| 16 | 모임방 멤버 관리 | 🟡 부분 | 초대/방장 구분/방 삭제만. 강퇴·탈퇴·방장 위임 없음 |
| 17 | 투표 설정 | ✅ 완료 (2차) | 익명/실명(방 단위 + 투표 단위) |

**정리: 완료 8 / 부분 5 / 미구현 4**

---

## 6. 잔여 개발 스펙

### 6.1 [P0] 다중 미디어 업로드 + 위치 정보 — #3

**현재 제약**

`Post`는 `contentUrl` / `normalContentUrl` / `dataSaverContentUrl` 각 1개씩만 가진다. 시간 구간당 사진 1장 정책도 여기에 묶여 있다.

**변경 설계**

```
Post (1) ──< PostMedia (N)
                ├ mediaType: IMAGE | VIDEO
                ├ originalUrl / normalUrl / dataSaverUrl
                ├ dominantColor
                ├ sortOrder
                └ latitude / longitude / placeName / takenAt   ← 위치·촬영시각
```

- `Post`의 URL 컬럼은 **당분간 유지**하고 첫 번째 미디어를 미러링 → 기존 프론트 무중단
- 이미지 3단 변환 파이프라인(2차 완료분)을 `PostMedia` 단위로 재사용
- 업로드 상한: 게시글당 최대 10장, 영상 1개(최대 30초)
- 위치 정보 출처 우선순위: ① 프론트 전달 좌표 → ② EXIF GPS → ③ 확정된 타임라인 장소 좌표
- **개인정보 고려**: 위치 첨부는 opt-in, 설정에서 기본값 지정 가능

**난이도** Lv.4 — 스키마 마이그레이션 + S3 업로드 병렬화 + 기존 API 호환
**예상 공수** 백엔드 4일 + 프론트 3일

---

### 6.2 [P1] 사진 모아보기 & 다운로드 — #5, #7

**모아보기**

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/trips/{tripGroupId}/album` | 여행 전체 사진 그리드 (일차별 그룹핑, 커서 페이징) |
| `GET` | `/trips/{tripGroupId}/album/members/{tripMemberId}` | 작성자별 필터 |

**다운로드**

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/posts/{postId}/media/{mediaId}/download` | 단건 원본 다운로드 (presigned URL 발급) |
| `POST` | `/trips/{tripGroupId}/album/download` | 선택 다중 다운로드 → ZIP 비동기 생성 |
- 단건은 **S3 presigned URL**로 서버 트래픽 우회
- 다중 ZIP은 비동기 처리 후 완료 시 푸시 알림 → 링크 제공 (TTL 24h)
- 방 멤버만 다운로드 가능 (`TripMemberValidator` 재사용)

**난이도** Lv.3
**예상 공수** 백엔드 2일 + 프론트 2일

---

### 6.3 [P1] 계획 카드 댓글 — #14

투표는 “찬반”만 가능하고 **“왜 여기가 좋은지” 설명할 공간이 없다.** 채팅으로 하면 흘러가 버린다.

**설계**

- 대상: 타임라인 카드 + 위시 장소
- 엔티티: `PlanComment(targetType, targetId, tripMember, content, parentId)` — 1단계 대댓글까지
- 삭제는 soft delete (`deletedAt`), “삭제된 댓글입니다” 표시
- 댓글 작성 시 `TripEventType.PLAN_COMMENT_ADDED` 추가 → 기존 SSE 취합 규칙(3.3 ①)에 그대로 편입

| Method | Path |
| --- | --- |
| `GET` | `/trips/{tripGroupId}/comments?targetType=&targetId=` |
| `POST` | `/trips/{tripGroupId}/comments` |
| `PATCH` | `/trips/{tripGroupId}/comments/{commentId}` |
| `DELETE` | `/trips/{tripGroupId}/comments/{commentId}` |

**난이도** Lv.2 — 기존 SSE/권한 검증 인프라를 그대로 태울 수 있음
**예상 공수** 백엔드 2일 + 프론트 2일

---

### 6.4 [P1] 개인 메모장 — #12

여행 준비물, 예약번호, 환전 메모 등 **공유하고 싶지 않은 기록**을 담는 공간.

- 엔티티: `TripMemo(tripGroup, member, title, content, isShared)`
- `isShared = true`면 방 전체 공개(공동 메모), `false`면 본인만
- 조회 시 반드시 `member` 조건 필터 — **다른 사람 개인 메모가 노출되면 치명적**이므로 테스트 필수

| Method | Path |
| --- | --- |
| `GET` | `/trips/{tripGroupId}/memos` |
| `POST` | `/trips/{tripGroupId}/memos` |
| `PATCH` | `/trips/{tripGroupId}/memos/{memoId}` |
| `DELETE` | `/trips/{tripGroupId}/memos/{memoId}` |

**난이도** Lv.2
**예상 공수** 백엔드 1.5일 + 프론트 1.5일

---

### 6.5 [P1] 모임방 멤버 관리 고도화 — #16

방 삭제(단건/다중)는 2차에서 완료했다. 아직 없는 것은 **탈퇴 / 강퇴 / 방장 위임**이다. 방장이 나가면 방이 고아가 된다.

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/trips/{tripGroupId}/members` | 멤버 목록 (방장 표시, 접속 상태 포함) |
| `DELETE` | `/trips/{tripGroupId}/members/me` | 자진 탈퇴 |
| `DELETE` | `/trips/{tripGroupId}/members/{tripMemberId}` | 강퇴 (방장 전용) |
| `PATCH` | `/trips/{tripGroupId}/members/{tripMemberId}/admin` | 방장 위임 |

**정책 결정 필요 사항**

- 탈퇴한 멤버가 올린 게시글/투표는? → **남긴다** (작성자명은 “탈퇴한 멤버”로 표시)
- 방장이 마지막 1인일 때 탈퇴 → 방 삭제로 전환할지 차단할지
- 여행 시작 이후 강퇴 허용 여부

**난이도** Lv.3 — 연관 데이터 정합성 정책이 실제 난이도
**예상 공수** 백엔드 2일 + 프론트 1.5일

---

### 6.6 [P2] 구글 / 애플 로그인 — #1

카카오(2차 완료)와 동일한 `provider` + `providerId` 구조를 그대로 확장한다.

- `spring-boot-starter-oauth2-client` 이미 의존성에 있음 → 설정 추가 위주
- `KakaoOAuth2SuccessHandler` → `OAuth2SuccessHandler`로 일반화 + provider별 `OAuth2UserInfo` 어댑터
- 소셜 로그인도 `DeviceIdFilter`를 거치므로 **기기별 세션 구조를 그대로 사용**한다
- 애플은 **private relay 이메일** 이슈 있음 → 이메일 기반 로직에 의존하지 않도록 주의
- 동일 이메일 다른 provider 가입 시 정책: **별도 계정으로 취급** (기존 카카오 정책과 동일)

**난이도** Lv.2(구글) / Lv.4(애플 — 심사·키 관리 필요)
**예상 공수** 구글 1일 / 애플 3일

---

### 6.7 [P2] 캘린더 연동 — #8

- **1단계 (앱 내부)**: 월간 캘린더 뷰에서 내가 속한 여행 기간을 색으로 표시, 클릭 시 방 이동
    - `GET /trips/calendar?year=&month=` — 백엔드는 기간 조회 하나만 추가하면 됨
- **2단계 (외부 연동)**: `.ics` 파일 내보내기 → 구글/애플 캘린더에 구독
    - `GET /trips/{tripGroupId}/calendar.ics`
    - 실시간 양방향 동기화는 **2차 범위 밖**으로 명시

**난이도** Lv.2(1단계) / Lv.3(ics)
**예상 공수** 백엔드 1.5일 + 프론트 2일

---

### 6.8 [P2] 예외처리 / 안정성 — #15

동시성 방어(장소 등록·삭제, 투표 참여)와 채팅 rate limit은 2차에서 처리했다. 남은 건 **외부 연동 장애 대응**이다. S3·FCM·카카오맵·메일에는 재시도나 차단 장치가 없다.

| 항목 | 적용 대상 | 방식 |
| --- | --- | --- |
| 서킷 브레이커 | 카카오맵 API, FCM, 메일 발송 | resilience4j `@CircuitBreaker` + fallback |
| 재시도 | S3 업로드, FCM 발송 | `@Retry` 지수 백오프 3회 |
| Rate limit | 로그인, 이메일 인증코드 발송, 게시글 업로드 | Redis 기반 (Redis 이미 도입됨) |
| 타임아웃 | 모든 외부 HTTP 호출 | connect 3s / read 5s 통일 |
- 실패 메시지를 `ErrorResponse` shape으로 통일 (2차에서 정리한 401/403 계약과 동일 포맷)
- Actuator + Prometheus가 이미 붙어 있으므로 **서킷 상태를 메트릭으로 노출**

**난이도** Lv.3
**예상 공수** 2.5일

---

## 7. 2차 MVP 범위 밖 (3차 이월)

- 캘린더 **양방향 실시간 동기화**
- AI 기반 여행 일정 추천 (트리플 유사 기능)
- 여행 기록 외부 공개/공유 링크
- 사진 자동 분류 (인물·장소 인식)
- 영상 편집 / 하이라이트 자동 생성
- 다국어(i18n)

---

## 8. 사용 기술

### 8.1 1차 스택

| 기술 | 용도 |
| --- | --- |
| Spring Boot / **Java** + Lombok | 백엔드 |
| Next.js · TypeScript | 프론트엔드 |
| MySQL(운영) · H2(개발) | 데이터베이스 |
| QueryDSL | 동적 쿼리 |
| Spring Batch | 투표 만료 일괄 집계 |
| AWS S3 | 이미지 저장소 |
| JJWT | JWT 발급·검증 |
| Actuator + Prometheus | 모니터링 |
| Docker · GitHub Actions | CI/CD |

### 8.2 2차에서 추가 도입 완료

| 기술 | 용도 |
| --- | --- |
| **Kotlin** (Java 전면 전환) | 백엔드 언어 교체 — 널 안정성, 표현력 |
| Spring WebSocket (STOMP) | 실시간 채팅 |
| Firebase Admin SDK | 웹 푸시 알림 |
| Spring OAuth2 Client | 카카오 소셜 로그인 |
| Redis | 인증 코드 / 세션성 데이터 저장 |
| Spring Mail | 이메일 인증코드 · 알림 발송 |
| MinIO | 개발 환경 S3 대체 |
| webp-imageio | 목록용 WebP 변환 |
| Grafana | 모니터링 대시보드 및 시각화 |

!system-architecture-deployment.svg

### 8.3 2차 잔여 작업에서 추가 예정

| 기술 | 용도 |
| --- | --- |
| resilience4j | 서킷 브레이커 / 재시도 |
| Redis (rate limit) | 로그인·인증코드·업로드 요청 제한 — 인프라는 이미 구축됨 |
| S3 Presigned URL | 사진 다운로드 시 서버 트래픽 우회 |
| ical4j 또는 수기 `.ics` 생성 | 캘린더 내보내기 |
| metadata-extractor | 이미지 EXIF GPS 추출 |
