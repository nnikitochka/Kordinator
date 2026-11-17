package org.airsonic.player.health;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-neutral health snapshot with status and diagnostic details.
 */
public final class Health {
    private final Status status;
    private final Map<String, Object> details;

    private Health(Builder builder) {
        this.status = Objects.requireNonNull(builder.status, "status");
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(builder.details));
    }

    public Status getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static Builder status(Status status) {
        return new Builder(status);
    }

    public static Builder status(String statusCode) {
        return new Builder(new Status(statusCode));
    }

    public static Builder up() {
        return status(Status.UP);
    }

    public static Builder down() {
        return status(Status.DOWN);
    }

    public static Builder down(Throwable ex) {
        return down().withException(ex);
    }

    public static Builder outOfService() {
        return status(Status.OUT_OF_SERVICE);
    }

    public static Builder unknown() {
        return status(Status.UNKNOWN);
    }

    public static final class Builder {
        private Status status;
        private final Map<String, Object> details = new LinkedHashMap<>();

        public Builder() {
            this(Status.UNKNOWN);
        }

        public Builder(Status status) {
            this.status = status;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder status(String statusCode) {
            this.status = new Status(statusCode);
            return this;
        }

        public Builder up() {
            return status(Status.UP);
        }

        public Builder down() {
            return status(Status.DOWN);
        }

        public Builder outOfService() {
            return status(Status.OUT_OF_SERVICE);
        }

        public Builder withDetail(String key, Object value) {
            this.details.put(Objects.requireNonNull(key, "key"), value);
            return this;
        }

        public Builder withDetails(Map<String, ?> details) {
            if (details != null) {
                this.details.putAll(details);
            }
            return this;
        }

        public Builder withException(Throwable ex) {
            if (ex != null) {
                String message = ex.getMessage();
                String error = (message != null && !message.isBlank())
                        ? ex.getClass().getName() + ": " + message
                        : ex.getClass().getName();
                this.details.put("error", error);
            }
            return this;
        }

        public Health build() {
            return new Health(this);
        }
    }
}
