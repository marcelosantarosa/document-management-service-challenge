# Build stage
FROM maven:3.8.4-openjdk-17-slim AS builder
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests clean package

# Runtime stage
FROM amazoncorretto:17-alpine-jdk
WORKDIR /app

COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8080

# Uses JAVA_OPTS from docker-compose
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
