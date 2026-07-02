# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# Tectonic renders LaTeX resumes to PDF (Phase 3). It self-downloads TeX packages on first
# run into TECTONIC_CACHE_DIR, so the image stays small.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://drop-sh.fullyjustified.net | sh \
    && mv tectonic /usr/local/bin/tectonic \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*
ENV TECTONIC_CACHE_DIR=/app/.tectonic-cache
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
