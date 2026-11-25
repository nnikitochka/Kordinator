FROM eclipse-temurin:25-jre-noble

LABEL description="Kordinator music server"
ENV KORDINATOR_PORT=4040 KORDINATOR_DIR=/var PUID=0 PGID=0

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gosu \
    && rm -rf /var/lib/apt/lists/*

COPY --chmod=755 run.sh entry.sh /usr/local/bin/
COPY build/dependency/kordinator.jar /app/kordinator.jar

EXPOSE ${KORDINATOR_PORT}
VOLUME ["/var/kordinator", "/var/music"]
HEALTHCHECK --interval=15s --timeout=3s CMD curl -fsS "http://127.0.0.1:${KORDINATOR_PORT}/api/v1/health" || exit 1
ENTRYPOINT ["entry.sh"]
