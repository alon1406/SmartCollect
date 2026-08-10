# =============================================================================
# Multi-stage build.
#
# Java 25 is newer than most platform buildpacks support, so the image is built
# explicitly rather than relying on a buildpack to guess the JDK.
# =============================================================================

# ---- build stage ------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy the wrapper and build script first. As long as build.gradle does not
# change, Docker reuses the cached dependency layer instead of downloading the
# whole Gradle distribution and every dependency again on each build.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src src

# Tests are skipped here on purpose: they use TestContainers, which needs a
# Docker daemon that is not available inside the build container.
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime stage ----------------------------------------------------------
FROM eclipse-temurin:25-jre

WORKDIR /app

# Run as an unprivileged user rather than root.
RUN useradd --system --uid 1001 --create-home spring

COPY --from=build /app/build/libs/*.jar app.jar
RUN chown spring:spring app.jar

USER spring

EXPOSE 8084

# The JVM defaults to a fraction of host memory, which is wrong inside a
# container. MaxRAMPercentage makes the heap follow the container limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
