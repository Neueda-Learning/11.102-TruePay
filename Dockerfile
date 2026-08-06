FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cache dependencies first to speed up incremental image builds.
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

# Build the Spring Boot executable JAR.
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root for better container security.
RUN addgroup -S spring && adduser -S spring -G spring

# Copy only the built artifact into the runtime image.
COPY --from=builder /workspace/target/*.jar /app/app.jar
RUN chown -R spring:spring /app

USER spring

ENV SERVER_PORT=8082

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]

