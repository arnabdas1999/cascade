# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy pom first for dependency layer caching
COPY pom.xml ./
RUN mvn dependency:go-offline -q

# Copy source and build (skip integration tests that need Docker)
COPY src/ src/
RUN mvn package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S cascade && adduser -S cascade -G cascade
USER cascade

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
