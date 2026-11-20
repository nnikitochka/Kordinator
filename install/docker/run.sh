#!/bin/bash

set -e

if [[ ${1:-} == java ]]; then
    exec "$@"
fi

java_opts=()
if [[ -n ${JAVA_OPTS:-} ]]; then
    while IFS= read -r -d '' option; do
        java_opts+=("$option")
    done < <(xargs -r printf '%s\0' <<<"$JAVA_OPTS")
fi

exec java -Djava.awt.headless=true \
    "-Dairsonic.home=$AIRSONIC_DIR/airsonic" \
    "-Dairsonic.http.port=$AIRSONIC_PORT" \
    "${java_opts[@]}" -jar /app/kordinator.jar "$@"
