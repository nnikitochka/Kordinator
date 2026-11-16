package org.airsonic.player.cache;

import javax.cache.Cache;
import java.util.function.Function;

/**
 * Framework-neutral cache helper for safe compute-if-absent and type-safe cache operations.
 */
public final class CacheHelper {

    private CacheHelper() {
    }

    public static <K, V> V getOrCompute(Cache<K, V> cache, K key, Function<K, V> loader) {
        if (key == null) {
            return loader != null ? loader.apply(null) : null;
        }
        if (cache == null) {
            return loader != null ? loader.apply(key) : null;
        }

        V value = cache.get(key);
        if (value == null && loader != null) {
            value = loader.apply(key);
            if (value != null) {
                cache.put(key, value);
            }
        }
        return value;
    }
}
