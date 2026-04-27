# syntax=docker/dockerfile:1.6

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package \
    && mv target/*.jar target/app.jar


FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S wallet && adduser -S wallet -G wallet
USER wallet

COPY --from=build /build/target/app.jar /app/app.jar

ENV SERVER_PORT=10000 \
    JAVA_OPTS=""

EXPOSE 10000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
