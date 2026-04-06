# ================================
# Stage 1: Build
# Gradle이 포함된 이미지에서 JAR 빌드
# ================================
FROM gradle:8.11-jdk21-alpine AS builder

WORKDIR /app

# 의존성 캐시 최적화
# build.gradle이 바뀌지 않으면 이 레이어는 캐시됨 → 빌드 속도 향상
COPY build.gradle settings.gradle ./
RUN gradle dependencies || true

# 소스 복사 후 JAR 빌드
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar -x test

# ================================
# Stage 2: Run
# JAR만 가져와서 실행 전용 경량 이미지 구성
# ================================
FROM amazoncorretto:21-alpine

WORKDIR /app

# Stage 1에서 빌드된 JAR만 복사 (소스코드, Gradle 등은 포함 안 됨)
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
