# =============================================
# 스테이지 1: 빌드 스테이지
# JDK 25 + Ubuntu 22.04(Jammy) 이미지로 JAR 파일 생성
# =============================================
FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /workspace

# Gradle Wrapper와 빌드 설정 파일을 먼저 복사
# → 소스코드 변경 없이 재빌드할 때 아래 레이어를 Docker 캐시에서 재사용 가능
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradlew ./

# 의존성만 미리 다운로드해서 캐시 레이어로 저장
# → 소스만 바뀐 경우 이 단계를 건너뛰어 빌드 속도 향상
RUN ./gradlew dependencies --no-daemon

# 소스코드 복사 후 bootJar 실행 (테스트 제외, 데몬 비활성화)
# --no-daemon: 컨테이너 환경에서 백그라운드 Gradle 데몬 실행을 방지
# -x test: 도커 빌드 시 단위 테스트는 건너뜀 (CI에서 별도 실행 권장)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# =============================================
# 스테이지 2: 런타임 스테이지
# JRE 25 이미지로 실행 (JDK 대비 이미지 크기 절반 이하, 보안 공격 면적 감소)
# =============================================
FROM eclipse-temurin:25-jre-jammy

WORKDIR /back

# 빌드 스테이지에서 생성된 JAR 파일만 복사
# → 소스코드, 빌드 도구, Gradle 캐시 등은 최종 이미지에 포함되지 않음
COPY --from=builder /workspace/build/libs/*.jar back.jar

# 컨테이너가 사용할 포트를 외부에 선언 (실제 포트 바인딩은 docker-compose에서 처리)
EXPOSE 8080

# JAR 실행 (ENTRYPOINT + CMD 분리 방식보다 단순한 단일 실행 방식)
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "back.jar"]
