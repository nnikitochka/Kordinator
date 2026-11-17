package org.airsonic.player.filter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Framework-neutral, immutable set of response headers for an HTTP policy. */
public final class StaticResponseHeaders {
    private final Map<String, String> values;

    public StaticResponseHeaders(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Map<String, String> values() {
        return values;
    }

    public void apply(BiConsumer<String, String> headerWriter) {
        values.forEach(headerWriter);
    }
}
