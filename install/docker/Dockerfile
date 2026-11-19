FROM eclipse-temurin:25-jre

LABEL description="Kordinator standalone music server" \
      url="https://github.com/airsonic-advanced/airsonic-advanced"

ENV AIRSONIC_HOME=/var/lib/kordinator \
    AIRSONIC_HTTP_HOST=0.0.0.0 \
    AIRSONIC_HTTP_PORT=8080

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/lib/kordinator \
    && chown -R 1000:1000 /var/lib/kordinator

COPY target/dependency/kordinator-server-11.0.0-SNAPSHOT.jar /app/kordinator-server.jar
COPY target/dependency/lib/*.jar /app/lib/

EXPOSE 8080
VOLUME ["/var/lib/kordinator", "/music"]
USER 1000:1000

HEALTHCHECK --interval=15s --timeout=3s CMD-SHELL curl --fail --silent "http://localhost:${AIRSONIC_HTTP_PORT}/api/v1/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/kordinator-server.jar"]
