# ===== Stage 1: Build =====
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# 1. Copy pom.xml only — dependency layer is cached until pom.xml changes
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# 2. Copy source and build
COPY src/ ./src/
RUN mvn package -DskipTests -B

# ===== Stage 2: Run =====
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
