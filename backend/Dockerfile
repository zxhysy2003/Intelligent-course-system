# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace/backend

COPY backend/pom.xml ./

RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

COPY backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package \
    && JAR_FILE="$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1)" \
    && cp "$JAR_FILE" /tmp/course-system.jar

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN mkdir -p /app/videos

COPY --from=build /tmp/course-system.jar /app/course-system.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/course-system.jar"]
