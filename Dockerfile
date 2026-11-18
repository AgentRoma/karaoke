# Multi-stage build for security and smaller image
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY karaoke/pom.xml karaoke/mvnw karaoke/mvnw.cmd ./
COPY karaoke/.mvn .mvn
COPY karaoke/src ./src
RUN ./mvnw -q -DskipTests package

# Runtime stage - minimal image
FROM eclipse-temurin:21-jre-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the jar
COPY --from=builder /app/karaoke/target/karaoke-*.jar app.jar

# Security: read-only filesystem (except /tmp)
# Note: Spring Boot needs write access for temp files, so we don't use read-only root

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]