# ---- Build stage: compile the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependencies first so they are cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI; the image build only packages the app
RUN mvn -B -q package -DskipTests

# ---- Run stage: small JRE image with just the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 dlq
COPY --from=build /app/target/*.jar app.jar
USER dlq

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
