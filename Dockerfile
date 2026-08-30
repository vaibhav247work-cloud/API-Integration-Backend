FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring \
    && mkdir -p /app/logs/archive /app/tmp

COPY --from=build /build/target/*.jar /app/app.jar

RUN chown -R spring:spring /app

USER spring

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
