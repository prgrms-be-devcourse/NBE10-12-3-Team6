# Node.js 20 Alpine(경량 Linux) 이미지 사용
FROM node:20-alpine

# pnpm 패키지 매니저 전역 설치
RUN npm install -g pnpm

# 작업 디렉토리 설정
WORKDIR /front

# 의존성 파일만 먼저 복사
# → 소스코드 변경 없이 재빌드할 때 pnpm install 레이어를 캐시에서 재사용
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# 잠금 파일 기준으로 정확한 버전의 의존성 설치
# --frozen-lockfile: lockfile 변경 불허 (팀원 간 동일한 의존성 보장)
RUN pnpm install --frozen-lockfile

# 나머지 소스코드 전체 복사
COPY . .

# Next.js 프로덕션 빌드 실행 → .next 폴더 생성
RUN pnpm build

# Next.js 기본 포트 3000 오픈
EXPOSE 3000

# 프로덕션 모드로 Next.js 서버 실행
CMD ["pnpm", "start"]