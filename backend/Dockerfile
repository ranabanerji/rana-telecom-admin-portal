# syntax=docker/dockerfile:1
# Backend image for Cloudflare Containers. Build from the repository root:
#   docker build -f backend/Dockerfile .
# The image ships a migrated and seeded SQLite database, so each container start begins with fresh demo data.

ARG JDBC_URL="jdbc:sqlite:/data/app.db?foreign_keys=true&date_class=INTEGER&date_precision=MILLISECONDS"

FROM eclipse-temurin:21-jdk AS build
ARG JDBC_URL
WORKDIR /src/backend
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY backend/config config
COPY backend/src src
RUN ./gradlew --no-daemon bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \; \
    && mkdir /data \
    && TELECOM_JDBC_URL="$JDBC_URL" ./gradlew --no-daemon migrateDatabase

FROM python:3.13-slim AS seed
WORKDIR /src
COPY scripts/requirements.txt scripts/requirements.txt
RUN pip install --no-cache-dir -r scripts/requirements.txt
COPY scripts/seed-database.py scripts/seed-database.py
COPY --from=build /data/app.db /data/app.db
RUN TELECOM_DB_PATH=/data/app.db python scripts/seed-database.py

FROM eclipse-temurin:21-jre
ARG JDBC_URL
RUN useradd --system --uid 10001 --home-dir /data app \
    && mkdir /data \
    && chown app /data
COPY --from=build /app.jar /app/app.jar
COPY --from=seed --chown=app /data/app.db /data/app.db
USER app
WORKDIR /data
ENV TELECOM_JDBC_URL="$JDBC_URL" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
