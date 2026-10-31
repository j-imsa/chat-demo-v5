FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY --from=proto . /proto
COPY pom.xml .
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dproto.dir=/proto -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app && mkdir -p /app /data && chown app /data
COPY --from=build /build/target/core.jar /app/core.jar
USER app
ENV CORE_DB_PATH=/data/core \
    AI_SERVICE_TARGET=ai-service:50051
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/core.jar"]
