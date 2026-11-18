package org.airsonic.player.spring;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

public class CacheConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfiguration.class);

    public CacheManager createCacheManager() {
        CachingProvider provider = Caching.getCachingProvider("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");
        CacheManager manager = provider.getCacheManager();

        createCache(manager, "userCache", String.class, User.class, TimeUnit.DAYS.toNanos(2), 1000L);
        createCache(manager, "userSettingsCache", String.class, UserSettings.class, TimeUnit.DAYS.toNanos(2), 1000L);
        createCache(manager, "mediaFilePathCache", String.class, MediaFile.class, TimeUnit.HOURS.toNanos(2), 1000L);
        createCache(manager, "mediaFileIdCache", Integer.class, MediaFile.class, TimeUnit.HOURS.toNanos(2), 1000L);
        createCache(manager, "coverArtCache", String.class, CoverArt.class, TimeUnit.DAYS.toNanos(2), 1000L);
        createCache(manager, "playlistCache", Integer.class, Playlist.class, TimeUnit.DAYS.toNanos(10), 1000L);
        createCache(manager, "playlistUsersCache", Integer.class, Object.class, TimeUnit.DAYS.toNanos(10), 1000L);

        LOG.info("Initialized Caffeine JCache manager with caches: {}", manager.getCacheNames());
        return manager;
    }

    private <K, V> void createCache(CacheManager manager, String name, Class<K> keyType, Class<V> valueType, long expireAfterWriteNanos, long maxSize) {
        if (manager.getCache(name) != null) {
            return;
        }
        CaffeineConfiguration<K, V> config = new CaffeineConfiguration<>();
        config.setTypes(keyType, valueType);
        config.setStoreByValue(false);
        config.setStatisticsEnabled(true);
        config.setNativeStatisticsEnabled(true);
        config.setMaximumSize(OptionalLong.of(maxSize));
        config.setExpireAfterWrite(OptionalLong.of(expireAfterWriteNanos));
        manager.createCache(name, config);
    }
}
