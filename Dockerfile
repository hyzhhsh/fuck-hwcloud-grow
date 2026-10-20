# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM mcr.microsoft.com/playwright/java:v1.63.0-jammy

ENV TZ=Asia/Shanghai \
    DISPLAY=:99 \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    GROW_NO_SANDBOX=true

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        -o Dpkg::Options::="--force-confdef" \
        -o Dpkg::Options::="--force-confold" \
        xvfb x11vnc fluxbox novnc websockify tzdata fonts-noto-cjk curl \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/fuck-hwcloud-grow-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh \
    && mkdir -p /app/data \
    && chmod 700 /app/data

EXPOSE 8080 6080
VOLUME ["/app/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/fuck-hwcloud-grow/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
