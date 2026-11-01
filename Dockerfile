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

RUN ./gradlew bootJar -x test --no-daemon \
    && java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination extracted

# Stage 3: Final Image
FROM eclipse-temurin:17-jre

ENV TZ=Asia/Seoul
RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ >/etc/timezone \
    && useradd --system --uid 10001 app

WORKDIR /app

COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

USER 10001

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
