FROM eclipse-temurin:25-jdk AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/main src/main
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 trackr \
    && useradd --uid 10001 --gid trackr --create-home --shell /usr/sbin/nologin trackr
WORKDIR /app
COPY --from=build --chown=trackr:trackr /build/target/trackr.jar app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
