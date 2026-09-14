# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY common common
COPY movie-service movie-service
COPY user-service user-service
COPY rating-service rating-service
COPY recommendation-service recommendation-service
RUN --mount=type=cache,id=neo4flix-maven,target=/root/.m2,sharing=locked --mount=type=secret,id=build_ca \
    if [ -s /run/secrets/build_ca ]; then \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias local-build-ca -file /run/secrets/build_ca; \
    fi && mvn -B -ntp verify

FROM eclipse-temurin:21-jre
RUN groupadd --system neo4flix && useradd --system --gid neo4flix --home-dir /app neo4flix
WORKDIR /app
ARG SERVICE
COPY --from=build /src/${SERVICE}/target/${SERVICE}-1.0.0.jar /app/app.jar
COPY --from=build /src/common/target/classes/io/neo4flix/common/Healthcheck.class /app/health/io/neo4flix/common/Healthcheck.class
USER neo4flix
ENV SERVICE=${SERVICE}
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
