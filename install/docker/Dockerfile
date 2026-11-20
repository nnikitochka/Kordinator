FROM eclipse-temurin:25-jre-noble

LABEL description="Kordinator music server"
ENV AIRSONIC_PORT=4040 AIRSONIC_DIR=/var PUID=0 PGID=0

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gosu \
    && rm -rf /var/lib/apt/lists/*

COPY --chmod=755 run.sh entry.sh /usr/local/bin/
COPY target/dependency/airsonic-main.jar /app/kordinator.jar

EXPOSE ${AIRSONIC_PORT}
VOLUME ["/var/airsonic", "/var/music"]
HEALTHCHECK --interval=15s --timeout=3s CMD curl -fsS "http://127.0.0.1:${AIRSONIC_PORT}/api/v1/health" || exit 1
ENTRYPOINT ["entry.sh"]
