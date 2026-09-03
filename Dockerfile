# ---------- build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 캐시한다. 소스만 바뀌면 이 레이어는 재사용된다.
COPY gradle gradle
COPY gradlew settings.gradle build.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root로 돌리지 않는다.
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080

# 컨테이너 메모리 한도를 힙에 반영한다. 고정 -Xmx는 배포처마다 다시 손봐야 한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
