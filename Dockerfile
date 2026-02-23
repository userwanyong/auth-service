# Multi-stage build for auth-service
# Stage 1: Build with Maven
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom files first for better Docker layer caching
COPY pom.xml .
COPY auth-service-api/pom.xml auth-service-api/
COPY auth-service-core/pom.xml auth-service-core/

# Download dependencies (will be cached if pom files don't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY auth-service-api/src auth-service-api/src
COPY auth-service-core/src auth-service-core/src

# Build the application
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="wanyj"
LABEL description="Authentication Service - Microservice with REST API and Dubbo RPC"

# Install tzdata for timezone support and add a non-root user
RUN apk add --no-cache tzdata && \
    addgroup -S app && \
    adduser -S app -G app

# Set timezone to Asia/Shanghai
ENV TZ=Asia/Shanghai

WORKDIR /app

# Copy the jar file from builder stage
COPY --from=builder /build/auth-service-core/target/auth-service-core-*.jar app.jar

# Change ownership to non-root user
RUN chown -R app:app /app

# Expose ports
# 8123 - REST API port
# 20880 - Dubbo RPC port
EXPOSE 8123 20880

# Switch to non-root user
USER app

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8123/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dfile.encoding=UTF-8", \
    "-Duser.timezone=Asia/Shanghai", \
    "-jar", \
    "app.jar"]

# Optional JVM arguments for production:
# -XX:+UseContainerSupport: Enable container-aware memory management
# -XX:MaxRAMPercentage=75.0: Use 75% of container memory for heap
# -Djava.security.egd: Use /dev/urandom for faster random number generation
# -Dfile.encoding: Set UTF-8 encoding
# -Duser.timezone: Set timezone
