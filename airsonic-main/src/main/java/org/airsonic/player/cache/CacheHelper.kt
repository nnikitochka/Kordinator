package org.airsonic.player.cache

import java.util.function.Function
import javax.cache.Cache

/** Framework-neutral cache helper for safe compute-if-absent and type-safe cache operations. */
class CacheHelper private constructor() {
    companion object {
        @JvmStatic
        fun <K : Any?, V : Any?> getOrCompute(cache: Cache<K, V>?, key: K?, loader: Function<K?, V?>?): V? {
            if (key == null) return loader?.apply(null)
            if (cache == null) return loader?.apply(key)

            var value = cache.get(key)
            if (value == null && loader != null) {
                value = loader.apply(key)
                if (value != null) cache.put(key, value)
            }
            return value
        }
    }
}
