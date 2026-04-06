FROM gradle:8.5-jdk21 AS builder
WORKDIR /home/gradle/project
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ ./gradle/
RUN gradle dependencies --no-daemon
COPY src/ ./src/
# RUN gradle build --no-daemon -x test
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /home/gradle/project/build/install/Anagram/ ./
EXPOSE 8080
ENTRYPOINT ["./bin/Anagram"]