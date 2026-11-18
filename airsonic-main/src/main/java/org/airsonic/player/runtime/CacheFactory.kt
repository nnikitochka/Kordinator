package org.airsonic.player.runtime

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Playlist
import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserSettings
import org.slf4j.LoggerFactory
import java.util.OptionalLong
import java.util.concurrent.TimeUnit
import javax.cache.CacheManager
import javax.cache.Caching

class CacheFactory {
    fun createCacheManager(): CacheManager {
        val manager = Caching.getCachingProvider("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider").cacheManager
        createCache(manager, "userCache", String::class.java, User::class.java, TimeUnit.DAYS.toNanos(2))
        createCache(manager, "userSettingsCache", String::class.java, UserSettings::class.java, TimeUnit.DAYS.toNanos(2))
        createCache(manager, "mediaFilePathCache", String::class.java, MediaFile::class.java, TimeUnit.HOURS.toNanos(2))
        createCache(manager, "mediaFileIdCache", Integer::class.java, MediaFile::class.java, TimeUnit.HOURS.toNanos(2))
        createCache(manager, "coverArtCache", String::class.java, CoverArt::class.java, TimeUnit.DAYS.toNanos(2))
        createCache(manager, "playlistCache", Integer::class.java, Playlist::class.java, TimeUnit.DAYS.toNanos(10))
        createCache(manager, "playlistUsersCache", Integer::class.java, Any::class.java, TimeUnit.DAYS.toNanos(10))
        LOG.info("Initialized Caffeine JCache manager with caches: {}", manager.cacheNames)
        return manager
    }

    private fun <K : Any, V : Any> createCache(manager: CacheManager, name: String, key: Class<K>, value: Class<V>, expiry: Long) {
        if (manager.getCache<Any, Any>(name) != null) return
        val config = CaffeineConfiguration<K, V>()
        config.setTypes(key, value)
        config.isStoreByValue = false
        config.isStatisticsEnabled = true
        config.setNativeStatisticsEnabled(true)
        config.maximumSize = OptionalLong.of(1000L)
        config.expireAfterWrite = OptionalLong.of(expiry)
        manager.createCache(name, config)
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(CacheFactory::class.java)
    }
}
