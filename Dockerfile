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
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chown spring:spring app.jar && chmod +x /app/docker-entrypoint.sh

USER spring

EXPOSE 8084

# The entrypoint translates Heroku's DATABASE_URL into a JDBC URL when present,
# then starts the JVM. MaxRAMPercentage is set there: the JVM otherwise sizes
# the heap from host memory, which is the wrong number inside a container.
ENTRYPOINT ["/app/docker-entrypoint.sh"]
