# ✈️ TripLog — 그룹 여행 계획 & 기록 서비스

친구들과 함께 여행 일정을 계획하고, 여행 중 순간을 사진과 글로 남기는 그룹 여행 다이어리 플랫폼입니다.

---

## 🌟 주요 기능

| 기능 | 설명 |
|------|------|
| 🔐 회원 인증 | 회원가입 / 로그인 / 로그아웃 (JWT + httpOnly 쿠키) |
| 🏠 여행 모임방 | 모임방 생성 및 초대 코드를 통한 멤버 참여 |
| 🗓️ 타임라인 | 일차별 시간 구간(시작~종료) 생성·수정·삭제, SSE 실시간 동기화 |
| 🗳️ 장소 투표 | 타임라인 시간 구간에 대한 장소 후보 투표 및 확정, Batch 만료 처리 |
| 📸 게시글 | 사진(이미지) 첨부, 위치·내용 기록, 일차별 조회 |
| 📱 PWA | 홈 화면 설치 가능, 오프라인 대응 서비스 워커 |

---

## 🛠️ 기술 스택

### ☕ Backend
- **Java 25 / Spring Boot 4.0.7**
- Spring Security (JWT — Access 30분, Refresh 7일, httpOnly 쿠키)
- Spring Data JPA + QueryDSL 5.1
- Spring Batch (투표 만료 스케줄러)
- MySQL (운영) / H2 (개발)
- Swagger / SpringDoc OpenAPI 3

### 🖥️ Frontend
- **Next.js 16 / React 19 / TypeScript**
- Tailwind CSS 4
- Zustand (전역 상태)
- PWA (Manifest + Service Worker)

### ⚙️ 인프라
- Docker / Docker Compose
- GitHub Actions (deploy-version-*.yml)

---

## 📁 프로젝트 구조

```
NBE10-12-2-Team6/
├── back/                    # Spring Boot 백엔드
│   ├── src/main/java/csh/back/
│   │   ├── domain/
│   │   │   ├── member/      # 회원 (가입·로그인·로그아웃)
│   │   │   ├── trip/
│   │   │   │   ├── group/   # 여행 모임방
│   │   │   │   ├── member/  # 모임 멤버
│   │   │   │   ├── place/   # 장소
│   │   │   │   ├── timeline/# 타임라인 (일정 시간 구간, SSE)
│   │   │   │   └── post/    # 게시글 (사진 포함)
│   │   │   └── vote/        # 투표 (vote / item / user)
│   │   └── global/          # 공통 설정, JWT, 예외 처리
│   └── build.gradle.kts
└── front/                   # Next.js 프론트엔드
    └── src/app/
        ├── page.tsx         # 로그인 / 회원가입 랜딩
        ├── home/            # 모임방 목록
        ├── trip/[id]/       # 모임방 상세 및 타임라인
        ├── capture/         # 사진 촬영 / 게시글 작성
        ├── plan/            # 일정 계획
        └── invite/[code]/   # 초대 링크 참여
```

---

## 🚀 로컬 실행 방법

### 🔧 백엔드

```bash
cd back

# 개발 환경 (H2 인메모리 DB)
./gradlew bootRun

# Docker Compose (MySQL 포함)
docker-compose up --build
```

기본 포트: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 💻 프론트엔드

```bash
cd front

pnpm install
pnpm dev
```

기본 포트: `http://localhost:3000`

---

## 📡 API 개요

모든 API는 `/api/v1` 접두사를 사용합니다.

| 도메인 | 경로 | 주요 메서드 |
|--------|------|------------|
| 🔐 인증 | `/api/v1/auth` | 회원가입 `POST /signup`, 로그인 `POST /login`, 로그아웃 `POST /logout` |
| 🏠 여행 모임방 | `/api/v1/trips` | 목록 조회·생성·상세 조회·수정 |
| 🗓️ 타임라인 | `/api/v1/trips/{tripId}/timelines` | 단건·일괄 생성, 일차별 조회, 수정·삭제, SSE 구독 |
| 🗳️ 투표 | `/api/v1/trips/{tripId}/votes` | 목록 조회, 생성, 항목별 투표 수, 확정 |
| 📸 게시글 | `/api/v1/trips/{tripId}/posts` | 생성(이미지 포함)·단건/전체 조회·수정·삭제 |

---

## 🔑 인증 방식

로그인 성공 시 Access Token(30분)과 Refresh Token(7일)을 **httpOnly 쿠키**로 발급합니다.  
Access Token 만료 시 `JwtAuthenticationFilter`가 Refresh Token을 검증하여 자동 재발급합니다.  
로그아웃 시 DB의 Refresh Token을 새 값으로 교체하여 즉시 무효화합니다.

---

## 🌐 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 (`dev` / `prod`) | `dev` |
| `CORS_ALLOWED_ORIGINS` | 허용 Origin 목록 | `http://localhost:3000` |
| `FILE_UPLOAD_BASE_URL` | 업로드 파일 기본 URL | `http://localhost:8080` |
| `NEXT_PUBLIC_API_BASE` | 프론트에서 사용하는 API 서버 주소 | - |
