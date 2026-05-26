FROM maven:3.9.11-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
