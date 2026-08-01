# syntax=docker/dockerfile:1

# ------------------------------------------------------------
# 1단계: 빌드 (Gradle Wrapper로 bootJar 생성, 테스트는 스킵)
#   - 테스트는 TestContainers로 Docker-in-Docker가 필요해 이미지 빌드 단계와 맞지 않는다.
#     테스트는 CI(.github/workflows/ci.yml)에서 별도로 실행된다.
# ------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 소스 코드 레이어보다 먼저 캐싱해 재빌드 속도를 높인다.
COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ------------------------------------------------------------
# 2단계: 실행 (JRE만 포함하는 경량 이미지)
# ------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
