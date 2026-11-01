# Stage 1: Dependencies
FROM eclipse-temurin:17-jdk AS dependencies
WORKDIR /build

COPY gradlew .
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
COPY build.gradle settings.gradle ./

RUN ./gradlew dependencies --no-daemon

# Stage 2: Build
FROM dependencies AS builder

COPY src src
COPY config config

RUN ./gradlew bootJar -x test --no-daemon

# Stage 3: Final Image
FROM eclipse-temurin:17-jre

ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ >/etc/timezone

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
