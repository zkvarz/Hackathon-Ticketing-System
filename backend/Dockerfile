# Multi-stage build: compile/package with Maven (JDK 21), run on a slim JRE.
# Build context is ./backend (see docker-compose.yml).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Integration tests need a Docker daemon (Testcontainers) which isn't available during image
# build, so tests run in CI / locally via `./mvnw verify`, not here.
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/tickets-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
