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

# 실제로 붙는 포트는 PORT 환경변수가 정한다 (Render 등 PaaS 가 주입).
# 이 줄은 로컬에서 docker run 할 때의 기본값 문서일 뿐이다.
EXPOSE 8080

# 컨테이너 메모리 한도를 힙에 반영한다. 고정 -Xmx는 배포처마다 다시 손봐야 한다.
#
# 65%인 이유: Render 무료 플랜은 512MB다. 힙 밖에서 메타스페이스·코드캐시·
# 스레드 스택으로 150~200MB가 나가는데, 75%(=384MB)로 잡으면 합계가 한도를
# 넘어 컨테이너가 OOM으로 죽는다. 로그도 안 남고 그냥 재시작만 반복한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/app.jar"]
