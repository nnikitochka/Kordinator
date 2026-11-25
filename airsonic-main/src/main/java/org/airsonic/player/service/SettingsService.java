package org.airsonic.player.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import org.airsonic.player.dao.AvatarDao;
import org.airsonic.player.dao.UserDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.settings.*;
import org.airsonic.player.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.airsonic.player.runtime.RuntimeConfiguration;

import javax.cache.Cache;
import javax.cache.CacheManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Provides persistent storage of application settings and preferences.
 */
public class SettingsService {
    // Airsonic home directory.
    private static final Path AIRSONIC_HOME_WINDOWS = Paths.get("c:/airsonic");
    private static final Path AIRSONIC_HOME_OTHER = Paths.get("/var/airsonic");

    // Global settings.
    private static final String KEY_DOWNSAMPLING_COMMAND = "DownsamplingCommand4";
    private static final String KEY_HLS_COMMAND = "HlsCommand4";
    private static final String KEY_JUKEBOX_COMMAND = "JukeboxCommand2";
    private static final String KEY_VIDEO_IMAGE_COMMAND = "VideoImageCommand";
    private static final String KEY_SUBTITLES_EXTRACTION_COMMAND = "SubtitlesExtractionCommand";
    private static final String KEY_DLNA_ENABLED = "DlnaEnabled";
    private static final String KEY_DLNA_SERVER_NAME = "DlnaServerName";
    private static final String KEY_DLNA_SERVER_ID = "DlnaServerId";
    private static final String KEY_DLNA_BASE_LAN_URL = "DlnaBaseLANURL";
    private static final String KEY_UPNP_PORT = "UPnpPort";
    private static final String KEY_JWT_KEY = "JWTKey";
    private static final String KEY_REMEMBER_ME_KEY = "RememberMeKey";
    private static final String KEY_ENCRYPTION_PASSWORD = "EncryptionKeyPassword";
    private static final String KEY_ENCRYPTION_SALT = "EncryptionKeySalt";
    private static final String KEY_PREFERRED_DECODABLE_PASSWORD_ENCODER = "DecodablePasswordEncoder";
    private static final String KEY_PREFERRED_NONDECODABLE_PASSWORD_ENCODER = "NonDecodablePasswordEncoder";
    private static final String KEY_PREFER_NONDECODABLE_PASSWORDS = "PreferNonDecodablePasswords";

    private static final String KEY_EXPORT_PLAYLIST_FORMAT = "PlaylistExportFormat";

    // Database Settings
    private static final String KEY_DATABASE_DRIVER = RuntimeConfiguration.DATABASE_DRIVER_KEY;
    public static final String KEY_DATABASE_URL = RuntimeConfiguration.DATABASE_URL_KEY;
    public static final String KEY_DATABASE_USERNAME = RuntimeConfiguration.DATABASE_USERNAME_KEY;
    public static final String KEY_DATABASE_PASSWORD = RuntimeConfiguration.DATABASE_PASSWORD_KEY;
    public static final String KEY_DATABASE_JNDI_NAME = RuntimeConfiguration.DATABASE_JNDI_NAME_KEY;
    private static final String KEY_DATABASE_MIGRATION_ROLLBACK_FILE = RuntimeConfiguration.DATABASE_MIGRATION_ROLLBACK_FILE_KEY;
    private static final String KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH = RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY;
    private static final String KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER = RuntimeConfiguration.DATABASE_MIGRATION_DEFAULT_MUSIC_FOLDER_KEY;
    public static final String KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS = "PropertiesFileRetainObsoleteKeys";

    // Default values.
    private static final String DEFAULT_JWT_KEY = null;
    private static final String DEFAULT_DOWNSAMPLING_COMMAND = "ffmpeg -i %s -map 0:0 -b:a %bk -v 0 -f mp3 -";
    private static final String DEFAULT_HLS_COMMAND = "ffmpeg -ss %o -i %s -s %wx%h -async 1 -c:v libx264 -flags +cgop -b:v %vk -maxrate %bk -preset superfast -copyts -b:a %rk -bufsize 256k -map 0:0 -map 0:%i -ac 2 -ar 44100 -v 0 -threads 0 -force_key_frames expr:gte(t,n_forced*10) -start_number %j -hls_time %d -hls_list_size 0 -hls_segment_filename %n %p";
    private static final String DEFAULT_JUKEBOX_COMMAND = "ffmpeg -ss %o -i %s -map 0:0 -v 0 -ar 44100 -ac 2 -f s16be -";
    private static final String DEFAULT_VIDEO_IMAGE_COMMAND = "ffmpeg -r 1 -ss %o -t 1 -i %s -s %wx%h -v 0 -f mjpeg -";
    private static final String DEFAULT_SUBTITLES_EXTRACTION_COMMAND = "ffmpeg -i %s -map 0:%i -f %f -";
    private static final boolean DEFAULT_DLNA_ENABLED = false;
    private static final String DEFAULT_DLNA_SERVER_NAME = "Airsonic";
    private static final String DEFAULT_DLNA_SERVER_ID = null;
    private static final String DEFAULT_DLNA_BASE_LAN_URL = null;
    private static final int DEFAULT_UPNP_PORT = 4041;
    private static final String DEFAULT_EXPORT_PLAYLIST_FORMAT = "m3u";
    private static final String DEFAULT_PREFERRED_NONDECODABLE_PASSWORD_ENCODER = "bcrypt";
    private static final String DEFAULT_PREFERRED_DECODABLE_PASSWORD_ENCODER = "encrypted-AES-GCM";
    private static final boolean DEFAULT_PREFER_NONDECODABLE_PASSWORDS = true;

    private static final String DEFAULT_DATABASE_DRIVER = null;
    private static final String DEFAULT_DATABASE_URL = null;
    private static final String DEFAULT_DATABASE_USERNAME = null;
    private static final String DEFAULT_DATABASE_PASSWORD = null;
    private static final String DEFAULT_DATABASE_JNDI_NAME = null;
    private static final Integer DEFAULT_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH = 384;

    private static final Logger LOG = LoggerFactory.getLogger(SettingsService.class);
    private static final String LOG_FILE_NAME_PROPERTY = "logging.file.name";

    // Array of obsolete properties. Used to clean property file.
    private static final List<String> OBSOLETE_KEYS = Arrays.asList("PortForwardingPublicPort", "PortForwardingLocalPort",
            "DownsamplingCommand", "DownsamplingCommand2", "DownsamplingCommand3", "AutoCoverBatch", "MusicMask",
            "VideoMask", "CoverArtMask", "HlsCommand", "HlsCommand2", "HlsCommand3", "JukeboxCommand",
            "CoverArtFileTypes", "UrlRedirectCustomHost", "CoverArtLimit", "StreamPort",
            "PortForwardingEnabled", "RewriteUrl", "UrlRedirectCustomUrl", "UrlRedirectContextPath",
            "UrlRedirectFrom", "UrlRedirectionEnabled", "UrlRedirectType", "Port", "HttpsPort",
            "MediaLibraryStatistics", "LastScanned", "database.config.type", "DatabaseConfigType",
            "database.usertable.quote", "DatabaseUsertableQuote", "spring.liquibase.parameters.userTableQuote"
            );

    private final SettingsUserPreferences userPreferences;
    private final SettingsPresentationPreferences presentationPreferences;
    private final SettingsCatalogPreferences catalogPreferences;
    private final SettingsOperationalPreferences operationalPreferences;
    private final SettingsRateLimitPreferences rateLimitPreferences;
    private final SettingsLdapPreferences ldapPreferences;
    private final SettingsMailPreferences mailPreferences;
    private final ConfigurationPropertiesService properties;

    public SettingsService(UserDao userDao, AvatarDao avatarDao, RuntimeConfiguration configuration,
                           CacheManager cacheManager, ConfigurationPropertiesService properties) {
        this(userDao, avatarDao, configuration::getProperty, cacheManager, properties);
    }

    private SettingsService(UserDao userDao, AvatarDao avatarDao, Function<String, String> propertyLookup,
                            CacheManager cacheManager, ConfigurationPropertiesService properties) {
        this.properties = Objects.requireNonNull(properties);
        this.valueStore = new SettingsValueStore(properties, Objects.requireNonNull(propertyLookup));
        this.presentationPreferences = new SettingsPresentationPreferences(valueStore);
        this.catalogPreferences = new SettingsCatalogPreferences(valueStore);
        this.operationalPreferences = new SettingsOperationalPreferences(valueStore);
        this.rateLimitPreferences = new SettingsRateLimitPreferences(valueStore);
        this.ldapPreferences = new SettingsLdapPreferences(valueStore);
        this.mailPreferences = new SettingsMailPreferences(valueStore);
        Cache<String, UserSettings> userSettingsCache = cacheManager != null
                ? cacheManager.getCache("userSettingsCache", String.class, UserSettings.class) : null;
        this.userPreferences = new SettingsUserPreferences(userDao, avatarDao, userSettingsCache);
    }

    public static Map<String, String> getMigratedPropertyKeys() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        OBSOLETE_KEYS.forEach(x -> keyMaps.put(x, null));

        keyMaps.put("database.config.embed.driver", "DatabaseConfigEmbedDriver");
        keyMaps.put("DatabaseConfigEmbedDriver", KEY_DATABASE_DRIVER);
        keyMaps.put("spring.datasource.driver-class-name", KEY_DATABASE_DRIVER);

        keyMaps.put("database.config.embed.url", "DatabaseConfigEmbedUrl");
        keyMaps.put("DatabaseConfigEmbedUrl", KEY_DATABASE_URL);
        keyMaps.put("spring.datasource.url", KEY_DATABASE_URL);

        keyMaps.put("database.config.embed.username", "DatabaseConfigEmbedUsername");
        keyMaps.put("DatabaseConfigEmbedUsername", KEY_DATABASE_USERNAME);
        keyMaps.put("spring.datasource.username", KEY_DATABASE_USERNAME);

        keyMaps.put("database.config.embed.password", "DatabaseConfigEmbedPassword");
        keyMaps.put("DatabaseConfigEmbedPassword", KEY_DATABASE_PASSWORD);
        keyMaps.put("spring.datasource.password", KEY_DATABASE_PASSWORD);

        keyMaps.put("database.config.jndi.name", "DatabaseConfigJNDIName");
        keyMaps.put("DatabaseConfigJNDIName", KEY_DATABASE_JNDI_NAME);
        keyMaps.put("spring.datasource.jndi-name", KEY_DATABASE_JNDI_NAME);

        keyMaps.put("spring.liquibase.rollback-file", KEY_DATABASE_MIGRATION_ROLLBACK_FILE);
        keyMaps.put("spring.liquibase.parameters.mysqlVarcharLimit", KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH);
        keyMaps.put("spring.liquibase.parameters.defaultMusicFolder", KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER);
        keyMaps.put("spring.liquibase.enabled", RuntimeConfiguration.DATABASE_MIGRATION_ENABLED_KEY);
        keyMaps.put("spring.liquibase.change-log", RuntimeConfiguration.DATABASE_MIGRATION_CHANGELOG_KEY);
        keyMaps.put("spring.liquibase.contexts", RuntimeConfiguration.DATABASE_MIGRATION_CONTEXTS_KEY);
        keyMaps.put("spring.liquibase.label-filter", RuntimeConfiguration.DATABASE_MIGRATION_LABEL_FILTER_KEY);

        keyMaps.put("database.varchar.maxlength", "DatabaseMysqlMaxlength");
        keyMaps.put("DatabaseMysqlMaxlength", KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH);

        keyMaps.put("airsonic.rememberMeKey", KEY_REMEMBER_ME_KEY);
        keyMaps.put("IgnoreFileTimestamps", "FullScan");

        return keyMaps;
    }

    public static void migrateKeys(Map<String, String> keyMaps, Function<String, Object> propertyLookup, String sourceName, Map<String, Object> migrated) {
        Map<String, Object> temp = new HashMap<>();
        // needs to be processed serially
        keyMaps.entrySet().stream()
                // we're not migrating to null, i.e. trying to delete the property
                .filter(e -> e.getValue() != null)
                // ps has the property we're trying to migrate (either directly or migrated earlier within the chain)
                .filter(e -> StringUtils.isNotBlank(Optional.ofNullable(propertyLookup.apply(e.getKey())).map(Object::toString).orElse(temp.getOrDefault(e.getKey(), "").toString())))
                // we're not migrating to a property that is already occupied
                .filter(e -> StringUtils.isBlank(Optional.ofNullable(propertyLookup.apply(e.getValue())).map(Object::toString).orElse(temp.getOrDefault(e.getValue(), "").toString())))
                .forEach(e -> {
                    Object val = Optional.ofNullable(propertyLookup.apply(e.getKey())).orElse(temp.get(e.getKey()));
                    temp.put(e.getValue(), val);
                    if (migrated.containsKey(e.getValue())) {
                        LOG.info("Skipping migrating property [{}] to [{}] in {} (already migrated)", e.getKey(), e.getValue(), sourceName);
                    } else {
                        LOG.info("Migrating property [{}] to [{}] in {}", e.getKey(), e.getValue(), sourceName);
                        migrated.put(e.getValue(), val);
                    }
                });
    }

    public static void migratePropFileKeys(Map<String, String> keyMaps, ConfigurationPropertiesService cps) {
        Boolean retainObsoleteKeys = Optional.ofNullable(cps.getProperty(KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS)).map(x -> Boolean.valueOf((String) x)).orElse(true);

        // needs to be processed serially
        keyMaps.entrySet().stream().filter(e -> cps.containsKey(e.getKey())).forEach(e -> {
            if (e.getValue() != null && !cps.containsKey(e.getValue())) {
                LOG.info("Migrating property [{}] to [{}] in properties file", e.getKey(), e.getValue());
                cps.setProperty(e.getValue(), cps.getProperty(e.getKey()));
            }

            // delete old property
            if (!retainObsoleteKeys) {
                LOG.info("Removing obsolete property [{}] in properties file", e.getKey());
                cps.clearProperty(e.getKey());
            }
        });

        cps.save();
    }

    public static void setDefaultConstants(Function<String, String> propertyLookup,
                                           Map<String, Object> defaultConstants) {
        // if jndi is set, everything datasource-related is ignored
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_URL))) {
            defaultConstants.put(KEY_DATABASE_URL, getDefaultJDBCUrl());
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_USERNAME))) {
            defaultConstants.put(KEY_DATABASE_USERNAME, getDefaultJDBCUsername());
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_PASSWORD))) {
            defaultConstants.put(KEY_DATABASE_PASSWORD, getDefaultJDBCPassword());
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_MIGRATION_ROLLBACK_FILE))) {
            defaultConstants.put(
                    KEY_DATABASE_MIGRATION_ROLLBACK_FILE,
                    getAirsonicHome().toAbsolutePath().resolve("rollback.sql").toFile());
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH))) {
            defaultConstants.put(
                    KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH,
                    DEFAULT_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH.toString());
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER))) {
            defaultConstants.put(KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER, Util.getDefaultMusicFolder());
        }
        if (StringUtils.isBlank(propertyLookup.apply(LOG_FILE_NAME_PROPERTY))) {
            defaultConstants.put(LOG_FILE_NAME_PROPERTY, getDefaultLogFile());
        }
    }

    public static Path getAirsonicHome() {
        Path home;

        String overrideHome = System.getProperty("airsonic.home");
        String oldHome = System.getProperty("libresonic.home");
        if (overrideHome != null) {
            home = Paths.get(overrideHome);
        } else if (oldHome != null) {
            home = Paths.get(oldHome);
        } else {
            home = Util.isWindows() ? AIRSONIC_HOME_WINDOWS : AIRSONIC_HOME_OTHER;
        }
        ensureDirectoryPresent(home);

        return home;
    }

    /**
     * Returns the directory in which all transcoders are installed.
     */
    public static Path getTranscodeDirectory() {
        Path dir = getAirsonicHome().resolve("transcode");
        if (!Files.exists(dir)) {
            try {
                dir = Files.createDirectory(dir);
                LOG.info("Created directory {}", dir);
            } catch (Exception e) {
                LOG.warn("Failed to create directory {}", dir);
            }
        }
        return dir;
    }

    private static boolean isExecutableInstalled(String executable) {
        Process process = null;
        try {
            process = new ProcessBuilder(executable).start();
            LOG.debug("{} exists and can be executed. Last executed {}", executable, process.info().startInstant());
            return true;
        } catch (IOException e) {
            LOG.debug("{} cannot be executed", executable, e);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static final LoadingCache<String, String> transcodeExecutableCache = CacheBuilder.newBuilder()
        .expireAfterAccess(8, TimeUnit.HOURS)
        .build(new CacheLoader<>() {
            @Override
            public String load(@NotNull String executable) {
                    return Stream.of(getTranscodeDirectory().resolve(executable).toString(), executable)
                        .filter(SettingsService::isExecutableInstalled)
                        .findFirst()
                        .orElseThrow();
            }
        });

    public static String resolveTranscodeExecutable(String executable, String defaultValue) {
        try {
            return transcodeExecutableCache.get(executable);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String getFileSystemAppName() {
        String home = getAirsonicHome().toString();
        return home.contains("libresonic") ? "libresonic" : "airsonic";
    }

    public static String getDefaultJDBCUrl() {
        return "jdbc:hsqldb:file:" + getAirsonicHome().resolve("db").resolve(getFileSystemAppName()) + ";hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false;sql.pad_space=false;hsqldb.defrag_limit=50;shutdown=true";
    }

    public static String getDefaultJDBCUsername() {
        return "sa";
    }

    public static String getDefaultJDBCPassword() {
        return "";
    }

    public int getUPnpPort() {
        return getInt(KEY_UPNP_PORT, DEFAULT_UPNP_PORT);
    }

    public static String getDefaultLogFile() {
        return SettingsService.getAirsonicHome().resolve(getFileSystemAppName() + ".log").toString();
    }

    public String getLogFile() {
        return getProperty(LOG_FILE_NAME_PROPERTY, getDefaultLogFile());
    }

    /**
     * Register in service locator so that non-Spring objects can access me.
     * Explicit startup initialization for the server instance.
     */
    public void init() {
        logServerInfo();
    }

    private static void logServerInfo() {
        LOG.info("Java: {}, OS: {}, Memory (max bytes): {}", Runtime.version(), System.getProperty("os.name"), Runtime.getRuntime().maxMemory());
    }

    public void save() {
        operationalPreferences.setSettingsChanged(System.currentTimeMillis());
        properties.save();
    }

    private static void ensureDirectoryPresent(Path home) {
        // Attempt to create home directory if it doesn't exist.
        if (!Files.exists(home) || !Files.isDirectory(home)) {
            try {
                Files.createDirectories(home);
            } catch (Exception e) {
                LOG.error("Could not create or see home directory {}", home, e);
                String message = "The directory " + home + " does not exist. Please create it and make it writable. " +
                        "(Set -Dairsonic.home=... or --airsonic.home=... when starting the server.)";
                throw new RuntimeException(message);
            }
        }
    }

    static Path getPropertyFile() {
        return getAirsonicHome().resolve(getFileSystemAppName() + ".properties");
    }

    private final SettingsValueStore valueStore;

    private void setProperty(String key, Object value) {
        valueStore.setProperty(key, value);
    }

    private int getInt(String key, int defaultValue) {
        return valueStore.getInt(key, defaultValue);
    }

    private void setInt(String key, Integer value) {
        setProperty(key, value);
    }

    private long getLong(String key, long defaultValue) {
        return valueStore.getLong(key, defaultValue);
    }

    private void setLong(String key, Long value) {
        setProperty(key, value);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        return valueStore.getBoolean(key, defaultValue);
    }

    private void setBoolean(String key, Boolean value) {
        setProperty(key, value);
    }

    private String getString(String key, String defaultValue) {
        return getProperty(key, defaultValue);
    }

    private void setString(String key, String value) {
        setProperty(key, value);
    }

    private String getProperty(String key, String defaultValue) {
        return valueStore.getString(key, defaultValue);
    }

    public String getIndexString() {
        return catalogPreferences.getIndexString();
    }

    public void setIndexString(String indexString) {
        catalogPreferences.setIndexString(indexString);
    }

    public String getIgnoredArticles() {
        return catalogPreferences.getIgnoredArticles();
    }

    String[] getIgnoredArticlesAsArray() {
        return catalogPreferences.getIgnoredArticlesAsArray();
    }

    public void setIgnoredArticles(String ignoredArticles) {
        catalogPreferences.setIgnoredArticles(ignoredArticles);
    }

    public String getUploadsFolder() {
        return catalogPreferences.getUploadsFolder();
    }

    public void setUploadsFolder(String uploadsFolder) {
        catalogPreferences.setUploadsFolder(uploadsFolder);
    }

    public Map<String, Object> buildSpelContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("AIRSONIC_HOME", getAirsonicHome());
        context.put("DEFAULT_PLAYLIST_FOLDER", getPlaylistFolder());
        context.put("DEFAULT_MUSIC_FOLDER", Util.getDefaultMusicFolder());
        return context;
    }

    public String resolveContextualString(String s, Supplier<Map<String, Object>> contextSupplier) {
        return SettingsContextExpressionResolver.resolve(s, contextSupplier);
    }

    public String getGenreSeparators() {
        return catalogPreferences.getGenreSeparators();
    }

    public void setGenreSeparators(String genreSeparators) {
        catalogPreferences.setGenreSeparators(genreSeparators);
    }

    public String getShortcuts() {
        return catalogPreferences.getShortcuts();
    }

    String[] getShortcutsAsArray() {
        return catalogPreferences.getShortcutsAsArray();
    }

    public void setShortcuts(String shortcuts) {
        catalogPreferences.setShortcuts(shortcuts);
    }

    public String getPlaylistFolder() {
        return catalogPreferences.getPlaylistFolder();
    }

    public void setPlaylistFolder(String playlistFolder) {
        catalogPreferences.setPlaylistFolder(playlistFolder);
    }

    public String getMusicFileTypes() {
        return catalogPreferences.getMusicFileTypes();
    }

    public void setMusicFileTypes(String fileTypes) {
        catalogPreferences.setMusicFileTypes(fileTypes);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getMusicFileTypesSet() {
        return catalogPreferences.getMusicFileTypesSet();
    }

    public String getVideoFileTypes() {
        return catalogPreferences.getVideoFileTypes();
    }

    public void setVideoFileTypes(String fileTypes) {
        catalogPreferences.setVideoFileTypes(fileTypes);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getVideoFileTypesSet() {
        return catalogPreferences.getVideoFileTypesSet();
    }

    public String getCoverArtFileTypes() {
        return catalogPreferences.getCoverArtFileTypes();
    }

    public void setCoverArtFileTypes(String fileTypes) {
        catalogPreferences.setCoverArtFileTypes(fileTypes);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getCoverArtFileTypesSet() {
        return catalogPreferences.getCoverArtFileTypesSet();
    }

    public CoverArtSource getCoverArtSource() {
        return catalogPreferences.getCoverArtSource();
    }

    public void setCoverArtSource(CoverArtSource source) {
        catalogPreferences.setCoverArtSource(source);
    }

    public int getCoverArtConcurrency() {
        return catalogPreferences.getCoverArtConcurrency();
    }

    public void setCoverArtConcurrency(Integer concurrency) {
        catalogPreferences.setCoverArtConcurrency(concurrency);
    }

    public int getCoverArtQuality() {
        return catalogPreferences.getCoverArtQuality();
    }

    public void setCoverArtQuality(Integer quality) {
        catalogPreferences.setCoverArtQuality(quality);
    }

    public String getWelcomeTitle() {
        return operationalPreferences.getWelcomeTitle();
    }

    public void setWelcomeTitle(String title) {
        operationalPreferences.setWelcomeTitle(title);
    }

    public String getWelcomeSubtitle() {
        return operationalPreferences.getWelcomeSubtitle();
    }

    public void setWelcomeSubtitle(String subtitle) {
        operationalPreferences.setWelcomeSubtitle(subtitle);
    }

    public String getWelcomeMessage() {
        return operationalPreferences.getWelcomeMessage();
    }

    public void setWelcomeMessage(String message) {
        operationalPreferences.setWelcomeMessage(message);
    }

    public String getLoginMessage() {
        return operationalPreferences.getLoginMessage();
    }

    public void setLoginMessage(String message) {
        operationalPreferences.setLoginMessage(message);
    }

    public String getSessionDuration() {
        return operationalPreferences.getSessionDuration();
    }

    public void setSessionDuration(String sessionTimeout) {
        operationalPreferences.setSessionDuration(sessionTimeout);
    }

    /**
     * Returns the number of days between automatic index creation, of -1 if automatic index
     * creation is disabled.
     */
    public int getIndexCreationInterval() {
        return operationalPreferences.getIndexCreationInterval();
    }

    /**
     * Sets the number of days between automatic index creation, of -1 if automatic index
     * creation is disabled.
     */
    public void setIndexCreationInterval(int days) {
        operationalPreferences.setIndexCreationInterval(days);
    }

    /**
     * Returns the hour of day (0 - 23) when automatic index creation should run.
     */
    public int getIndexCreationHour() {
        return operationalPreferences.getIndexCreationHour();
    }

    /**
     * Sets the hour of day (0 - 23) when automatic index creation should run.
     */
    public void setIndexCreationHour(int hour) {
        operationalPreferences.setIndexCreationHour(hour);
    }

    public boolean isFastCacheEnabled() {
        return operationalPreferences.isFastCacheEnabled();
    }

    public void setFastCacheEnabled(boolean enabled) {
        operationalPreferences.setFastCacheEnabled(enabled);
    }

    public boolean getFullScan() {
        return operationalPreferences.getFullScan();
    }

    public void setFullScan(Boolean fullscan) {
        operationalPreferences.setFullScan(fullscan);
    }

    public boolean getClearFullScanSettingAfterScan() {
        return operationalPreferences.getClearFullScanSettingAfterScan();
    }

    public void setClearFullScanSettingAfterScan(Boolean clear) {
        operationalPreferences.setClearFullScanSettingAfterScan(clear);
    }

    public long getTranscodeEstimateTimePadding() {
        return operationalPreferences.getTranscodeEstimateTimePadding();
    }

    public void setTranscodeEstimateTimePadding(Long timeInMillis) {
        operationalPreferences.setTranscodeEstimateTimePadding(timeInMillis);
    }

    public long getTranscodeEstimateBytePadding() {
        return operationalPreferences.getTranscodeEstimateBytePadding();
    }

    public void setTranscodeEstimateBytePadding(Long bytes) {
        operationalPreferences.setTranscodeEstimateBytePadding(bytes);
    }

    public int getDbBackupInterval() {
        return operationalPreferences.getDbBackupInterval();
    }

    public void setDbBackupInterval(int hours) {
        operationalPreferences.setDbBackupInterval(hours);
    }

    public int getDbBackupRetentionCount() {
        return operationalPreferences.getDbBackupRetentionCount();
    }

    public void setDbBackupRetentionCount(int count) {
        operationalPreferences.setDbBackupRetentionCount(count);
    }

    /**
     * @return The download bitrate limit in Kbit/s. Zero if unlimited.
     */
    public long getDownloadBitrateLimit() {
        return rateLimitPreferences.getDownloadBitrateLimit();
    }

    public RateLimiter getDownloadBitrateLimiter() {
        return rateLimitPreferences.getDownloadBitrateLimiter();
    }

    /**
     * @param limit The download bitrate limit in Kbit/s. Zero if unlimited.
     */
    public void setDownloadBitrateLimit(long limit) {
        rateLimitPreferences.setDownloadBitrateLimit(limit);
    }

    /**
     * @return The upload bitrate limit in Kbit/s. Zero if unlimited.
     */
    public long getUploadBitrateLimit() {
        return rateLimitPreferences.getUploadBitrateLimit();
    }

    public RateLimiter getUploadBitrateLimiter() {
        return rateLimitPreferences.getUploadBitrateLimiter();
    }

    /**
     * @param limit The upload bitrate limit in Kbit/s. Zero if unlimited.
     */
    public void setUploadBitrateLimit(long limit) {
        rateLimitPreferences.setUploadBitrateLimit(limit);
    }

    public String getDownsamplingCommand() {
        return getProperty(KEY_DOWNSAMPLING_COMMAND, DEFAULT_DOWNSAMPLING_COMMAND);
    }

    public void setDownsamplingCommand(String command) {
        setProperty(KEY_DOWNSAMPLING_COMMAND, command);
    }

    public String getHlsCommand() {
        return getProperty(KEY_HLS_COMMAND, DEFAULT_HLS_COMMAND);
    }

    public void setHlsCommand(String command) {
        setProperty(KEY_HLS_COMMAND, command);
    }

    public String getJukeboxCommand() {
        return getProperty(KEY_JUKEBOX_COMMAND, DEFAULT_JUKEBOX_COMMAND);
    }

    public void setJukeboxCommand(String command) {
        setProperty(KEY_JUKEBOX_COMMAND, command);
    }

    public String getVideoImageCommand() {
        return getProperty(KEY_VIDEO_IMAGE_COMMAND, DEFAULT_VIDEO_IMAGE_COMMAND);
    }

    public void setVideoImageCommand(String command) {
        setProperty(KEY_VIDEO_IMAGE_COMMAND, command);
    }

    public String getSubtitlesExtractionCommand() {
        return getProperty(KEY_SUBTITLES_EXTRACTION_COMMAND, DEFAULT_SUBTITLES_EXTRACTION_COMMAND);
    }

    public void setSubtitlesExtractionCommand(String command) {
        setProperty(KEY_SUBTITLES_EXTRACTION_COMMAND, command);
    }

    public boolean isLdapEnabled() {
        return ldapPreferences.isLdapEnabled();
    }

    public void setLdapEnabled(boolean ldapEnabled) {
        ldapPreferences.setLdapEnabled(ldapEnabled);
    }

    public String getLdapUrl() {
        return ldapPreferences.getLdapUrl();
    }

    public void setLdapUrl(String ldapUrl) {
        ldapPreferences.setLdapUrl(ldapUrl);
    }

    public String getLdapSearchFilter() {
        return ldapPreferences.getLdapSearchFilter();
    }

    public void setLdapSearchFilter(String ldapSearchFilter) {
        ldapPreferences.setLdapSearchFilter(ldapSearchFilter);
    }

    public String getLdapManagerDn() {
        return ldapPreferences.getLdapManagerDn();
    }

    public void setLdapManagerDn(String ldapManagerDn) {
        ldapPreferences.setLdapManagerDn(ldapManagerDn);
    }

    public String getLdapManagerPassword() {
        return ldapPreferences.getLdapManagerPassword();
    }

    public void setLdapManagerPassword(String ldapManagerPassword) {
        ldapPreferences.setLdapManagerPassword(ldapManagerPassword);
    }

    public boolean isLdapAutoShadowing() {
        return ldapPreferences.isLdapAutoShadowing();
    }

    public void setLdapAutoShadowing(boolean ldapAutoShadowing) {
        ldapPreferences.setLdapAutoShadowing(ldapAutoShadowing);
    }

    public boolean isGettingStartedEnabled() {
        return operationalPreferences.isGettingStartedEnabled();
    }

    public void setGettingStartedEnabled(boolean isGettingStartedEnabled) {
        operationalPreferences.setGettingStartedEnabled(isGettingStartedEnabled);
    }

    public long getSettingsChanged() {
        return operationalPreferences.getSettingsChanged();
    }

    public boolean isOrganizeByFolderStructure() {
        return operationalPreferences.isOrganizeByFolderStructure();
    }

    public void setOrganizeByFolderStructure(boolean b) {
        operationalPreferences.setOrganizeByFolderStructure(b);
    }

    public boolean isSortAlbumsByYear() {
        return operationalPreferences.isSortAlbumsByYear();
    }

    public void setSortAlbumsByYear(boolean b) {
        operationalPreferences.setSortAlbumsByYear(b);
    }

    public boolean getIgnoreSymLinks() {
        return operationalPreferences.getIgnoreSymLinks();
    }

    public void setIgnoreSymLinks(boolean b) {
        operationalPreferences.setIgnoreSymLinks(b);
    }

    public String getExcludePatternString() {
        return operationalPreferences.getExcludePatternString();
    }

    public void setExcludePatternString(String s) {
        operationalPreferences.setExcludePatternString(s);
    }

    public Pattern getExcludePattern() {
        return operationalPreferences.getExcludePattern();
    }

    /**
     * Returns whether we are running in Development mode.
     *
     * @return true if we are in Development mode.
     */
    public static boolean isDevelopmentMode() {
        return System.getProperty("airsonic.development") != null;
    }

    /**
     * Returns the custom 'remember me' key used for generating authentication tokens.
     *
     * @return The 'remember me' key.
     */
    public String getRememberMeKey() {
        return getString(KEY_REMEMBER_ME_KEY, null);
    }

    /**
     * Returns the locale (for language, date format etc).
     *
     * @return The locale.
     */
    public Locale getLocale() {
        return presentationPreferences.getLocale();
    }

    /**
     * Sets the locale (for language, date format etc.)
     *
     * @param locale The locale.
     */
    public void setLocale(Locale locale) {
        presentationPreferences.setLocale(locale);
    }

    /**
     * Returns the ID of the theme to use.
     *
     * @return The theme ID.
     */
    public String getThemeId() {
        return presentationPreferences.getThemeId();
    }

    /**
     * Sets the ID of the theme to use.
     *
     * @param themeId The theme ID
     */
    public void setThemeId(String themeId) {
        presentationPreferences.setThemeId(themeId);
    }

    /**
     * Returns a list of available themes.
     *
     * @return A list of available themes.
     */
    public synchronized Theme[] getAvailableThemes() {
        return presentationPreferences.getAvailableThemes();
    }

    /**
     * Returns a list of available locales.
     *
     * @return A list of available locales.
     */
    public synchronized Locale[] getAvailableLocales() {
        return presentationPreferences.getAvailableLocales();
    }

    /**
     * Returns the "brand" name. Normally, this is just "Airsonic".
     *
     * @return The brand name.
     */
    public String getBrand() {
        return presentationPreferences.getBrand();
    }

    /**
     * Returns settings for the given user.
     *
     * @param username The username.
     * @return User-specific settings. Never <code>null</code>.
     */
    public UserSettings getUserSettings(String username) {
        return userPreferences.getUserSettings(username);
    }

    /**
     * Updates settings for the given username.
     *
     * @param settings The user-specific settings.
     */
    public void updateUserSettings(UserSettings settings) {
        userPreferences.updateUserSettings(settings);
    }

    /**
     * Returns all system avatars.
     *
     * @return All system avatars.
     */
    public List<Avatar> getAllSystemAvatars() {
        return userPreferences.getAllSystemAvatars();
    }

    /**
     * Returns the system avatar with the given ID.
     *
     * @param id The system avatar ID.
     * @return The avatar or <code>null</code> if not found.
     */
    public Avatar getSystemAvatar(int id) {
        return userPreferences.getSystemAvatar(id);
    }

    /**
     * Returns the custom avatar for the given user.
     *
     * @param username The username.
     * @return The avatar or <code>null</code> if not found.
     */
    public Avatar getCustomAvatar(String username) {
        return userPreferences.getCustomAvatar(username);
    }

    /**
     * Sets the custom avatar for the given user.
     *
     * @param avatar   The avatar, or <code>null</code> to remove the avatar.
     * @param username The username.
     */
    public void setCustomAvatar(Avatar avatar, String username) {
        userPreferences.setCustomAvatar(avatar, username);
    }

    public boolean isDlnaEnabled() {
        return getBoolean(KEY_DLNA_ENABLED, DEFAULT_DLNA_ENABLED);
    }

    public void setDlnaEnabled(boolean dlnaEnabled) {
        setBoolean(KEY_DLNA_ENABLED, dlnaEnabled);
    }

    public String getDlnaServerName() {
        return getString(KEY_DLNA_SERVER_NAME, DEFAULT_DLNA_SERVER_NAME);
    }

    public void setDlnaServerName(String dlnaServerName) {
        setString(KEY_DLNA_SERVER_NAME, dlnaServerName);
    }

    public String getDlnaServerId() {
        return getString(KEY_DLNA_SERVER_ID, DEFAULT_DLNA_SERVER_ID); // default is null
    }

    public void setDlnaServerId(String dlnaServerId) {
        setString(KEY_DLNA_SERVER_ID, dlnaServerId);
    }

    public String getDlnaBaseLANURL() {
        return getString(KEY_DLNA_BASE_LAN_URL, DEFAULT_DLNA_BASE_LAN_URL);
    }

    public void setDlnaBaseLANURL(String dlnaBaseLANURL) {
        setString(KEY_DLNA_BASE_LAN_URL, dlnaBaseLANURL);
    }

    public void setUserDao(UserDao userDao) {
        userPreferences.setUserDao(userDao);
    }

    public void setAvatarDao(AvatarDao avatarDao) {
        userPreferences.setAvatarDao(avatarDao);
    }

    public String getSmtpServer() {
        return mailPreferences.getSmtpServer();
    }

    public void setSmtpServer(String smtpServer) {
        mailPreferences.setSmtpServer(smtpServer);
    }

    public String getSmtpPort() {
        return mailPreferences.getSmtpPort();
    }

    public void setSmtpPort(String smtpPort) {
        mailPreferences.setSmtpPort(smtpPort);
    }

    public String getSmtpEncryption() {
        return mailPreferences.getSmtpEncryption();
    }

    public void setSmtpEncryption(String encryptionMethod) {
        mailPreferences.setSmtpEncryption(encryptionMethod);
    }

    public String getSmtpUser() {
        return mailPreferences.getSmtpUser();
    }

    public void setSmtpUser(String smtpUser) {
        mailPreferences.setSmtpUser(smtpUser);
    }

    public String getSmtpPassword() {
        return mailPreferences.getSmtpPassword();
    }

    public void setSmtpPassword(String smtpPassword) {
        mailPreferences.setSmtpPassword(smtpPassword);
    }

    public String getSmtpFrom() {
        return mailPreferences.getSmtpFrom();
    }

    public void setSmtpFrom(String smtpFrom) {
        mailPreferences.setSmtpFrom(smtpFrom);
    }

    public boolean isCaptchaEnabled() {
        return mailPreferences.isCaptchaEnabled();
    }

    public void setCaptchaEnabled(boolean captchaEnabled) {
        mailPreferences.setCaptchaEnabled(captchaEnabled);
    }

    public String getRecaptchaSiteKey() {
        return mailPreferences.getRecaptchaSiteKey();
    }

    public void setRecaptchaSiteKey(String recaptchaSiteKey) {
        mailPreferences.setRecaptchaSiteKey(recaptchaSiteKey);
    }

    public String getRecaptchaSecretKey() {
        return mailPreferences.getRecaptchaSecretKey();
    }

    public void setRecaptchaSecretKey(String recaptchaSecretKey) {
        mailPreferences.setRecaptchaSecretKey(recaptchaSecretKey);
    }

    public String getDatabaseDriver() {
        return getString(KEY_DATABASE_DRIVER, DEFAULT_DATABASE_DRIVER);
    }

    public void setDatabaseDriver(String driver) {
        setString(KEY_DATABASE_DRIVER, driver);
    }

    public String getDatabaseUrl() {
        return getString(KEY_DATABASE_URL, DEFAULT_DATABASE_URL);
    }

    public void setDatabaseUrl(String url) {
        setString(KEY_DATABASE_URL, url);
    }

    public String getDatabaseUsername() {
        return getString(KEY_DATABASE_USERNAME, DEFAULT_DATABASE_USERNAME);
    }

    public void setDatabaseUsername(String username) {
        setString(KEY_DATABASE_USERNAME, username);
    }

    public String getDatabasePassword() {
        return getString(KEY_DATABASE_PASSWORD, DEFAULT_DATABASE_PASSWORD);
    }

    public void setDatabasePassword(String password) {
        setString(KEY_DATABASE_PASSWORD, password);
    }

    public String getDatabaseJNDIName() {
        return getString(KEY_DATABASE_JNDI_NAME, DEFAULT_DATABASE_JNDI_NAME);
    }

    public void setDatabaseJNDIName(String jndiName) {
        setString(KEY_DATABASE_JNDI_NAME, jndiName);
    }

    public Integer getDatabaseMysqlVarcharMaxlength() {
        return getInt(KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH, DEFAULT_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH);
    }

    public void setDatabaseMysqlVarcharMaxlength(int maxlength) {
        setInt(KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH, maxlength);
    }

    public String getJWTKey() {
        return getString(KEY_JWT_KEY, DEFAULT_JWT_KEY);
    }

    public void setJWTKey(String jwtKey) {
        setString(KEY_JWT_KEY, jwtKey);
    }

    public String getEncryptionPassword() {
        return getString(KEY_ENCRYPTION_PASSWORD, null);
    }

    public void setEncryptionPassword(String key) {
        setString(KEY_ENCRYPTION_PASSWORD, key);
    }

    public String getEncryptionSalt() {
        return getString(KEY_ENCRYPTION_SALT, null);
    }

    public void setEncryptionSalt(String salt) {
        setString(KEY_ENCRYPTION_SALT, salt);
    }

    public String getDecodablePasswordEncoder() {
        return getString(KEY_PREFERRED_DECODABLE_PASSWORD_ENCODER, DEFAULT_PREFERRED_DECODABLE_PASSWORD_ENCODER);
    }

    public void setDecodablePasswordEncoder(String encoder) {
        setString(KEY_PREFERRED_DECODABLE_PASSWORD_ENCODER, encoder);
    }

    public String getNonDecodablePasswordEncoder() {
        return getString(KEY_PREFERRED_NONDECODABLE_PASSWORD_ENCODER, DEFAULT_PREFERRED_NONDECODABLE_PASSWORD_ENCODER);
    }

    public void setNonDecodablePasswordEncoder(String encoder) {
        setString(KEY_PREFERRED_NONDECODABLE_PASSWORD_ENCODER, encoder);
    }

    public boolean getPreferNonDecodablePasswords() {
        return getBoolean(KEY_PREFER_NONDECODABLE_PASSWORDS, DEFAULT_PREFER_NONDECODABLE_PASSWORDS);
    }

    public void setPreferNonDecodablePasswords(boolean preference) {
        setBoolean(KEY_PREFER_NONDECODABLE_PASSWORDS, preference);
    }

    public void setPropertyLookup(Function<String, String> propertyLookup) {
        valueStore.setPropertyLookup(Objects.requireNonNull(propertyLookup));
    }

    public void resetDatabaseToDefault() {
        setDatabaseDriver(DEFAULT_DATABASE_DRIVER);
        setDatabasePassword(DEFAULT_DATABASE_PASSWORD);
        setDatabaseUrl(DEFAULT_DATABASE_URL);
        setDatabaseUsername(DEFAULT_DATABASE_USERNAME);
        setDatabaseJNDIName(DEFAULT_DATABASE_JNDI_NAME);
        setDatabaseMysqlVarcharMaxlength(DEFAULT_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH);
    }

    String getPlaylistExportFormat() {
        return getProperty(KEY_EXPORT_PLAYLIST_FORMAT, DEFAULT_EXPORT_PLAYLIST_FORMAT);
    }
}
