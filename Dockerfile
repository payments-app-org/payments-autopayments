# Контекст сборки — корень репозитория: сначала общая библиотека libs/platform, потом сервис.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY libs/platform libs/platform
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -f libs/platform/pom.xml install -DskipTests
COPY services/autopayments/pom.xml services/autopayments/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -f services/autopayments/pom.xml dependency:go-offline
COPY services/autopayments/src services/autopayments/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -f services/autopayments/pom.xml package && cp services/autopayments/target/autopayments-*.jar /app.jar

FROM eclipse-temurin:21-jre
# Не root: у процесса в контейнере нет лишних прав
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /app.jar /app.jar
# Куча — доля лимита памяти контейнера; при нехватке памяти процесс падает, и его перезапускают
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app.jar"]
