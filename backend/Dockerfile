# syntax=docker/dockerfile:1

# ---- Build -------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

# Resolve dependencies first so they are cached unless pom.xml changes.
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests && \
    cp target/khedma-backend-*.jar /src/app.jar

# ---- Runtime -----------------------------------------------------------------------------
FROM eclipse-temurin:17.0.13_11-jre-alpine AS runtime
LABEL org.opencontainers.image.source="https://github.com/michaelsaad/khedma-e3dady"
WORKDIR /app

RUN addgroup --system --gid 1001 app && \
    adduser --system --uid 1001 --ingroup app app

COPY --link --from=build --chown=1001:1001 /src/app.jar /app/app.jar

USER 1001:1001
EXPOSE 8080

# Tuned for small free-tier containers (~512 MB RAM, shared CPU): cap heap to the container,
# use the low-overhead serial GC, and stop at C1 for a faster cold start.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
