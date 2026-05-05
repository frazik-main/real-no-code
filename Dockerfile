# ---- Build stage ----
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Copy Gradle wrapper and build files first for layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Pre-download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source and build the fat jar
COPY src src
RUN ./gradlew bootJar --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
