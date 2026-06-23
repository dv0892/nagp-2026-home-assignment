# --- Stage 1: Build the application ---
FROM amazoncorretto:26 AS builder
WORKDIR /app

# Copy gradle files first for dependency caching
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

# Copy source and compile
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Create the lightweight runtime image ---
# Using the minimal Amazon Linux 2023 base provided by Corretto
FROM amazoncorretto:26-al2023-headless
WORKDIR /app

# Run as a safe, unprivileged numeric user ID for security (bypasses missing groupadd/useradd)
USER 1000:1000

# Copy the built jar from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]