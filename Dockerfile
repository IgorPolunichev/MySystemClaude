# syntax=docker/dockerfile:1.7
# Единый Dockerfile для всех Java-сервисов. Сервис выбирается аргументом SERVICE:
#   docker build --build-arg SERVICE=act-service -t acts/act-service .

FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY pom.xml .
COPY platform platform
COPY services services
# Кэш ~/.m2 общий для всех сборок, зависимости скачиваются один раз.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl services/${SERVICE} -am package -DskipTests \
 && cp services/${SERVICE}/target/${SERVICE}.jar /workspace/app.jar

FROM eclipse-temurin:21.0.5_11-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC -Dfile.encoding=UTF-8"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
