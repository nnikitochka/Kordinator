package org.airsonic.player.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import org.airsonic.player.dao.AvatarDao;
import org.airsonic.player.dao.UserDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.util.StringUtil;
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
    private static final String KEY_WELCOME_TITLE = "WelcomeTitle";
    private static final String KEY_WELCOME_SUBTITLE = "WelcomeSubtitle";
    private static final String KEY_WELCOME_MESSAGE = "WelcomeMessage2";
    private static final String KEY_LOGIN_MESSAGE = "LoginMessage";
    private static final String KEY_SESSION_DURATION = "server.servlet.session.timeout";
    private static final String KEY_INDEX_CREATION_INTERVAL = "IndexCreationInterval";
    private static final String KEY_INDEX_CREATION_HOUR = "IndexCreationHour";
    private static final String KEY_FAST_CACHE_ENABLED = "FastCacheEnabled";
    private static final String KEY_FULL_SCAN = "FullScan";
    private static final String KEY_CLEAR_FULL_SCAN_SETTING_AFTER_SCAN = "ClearFullScanSettingAfterScan";
    private static final String KEY_TRANSCODE_ESTIMATE_TIME_PADDING = "TranscodeEstimateTimePadding";
    private static final String KEY_TRANSCODE_ESTIMATE_BYTE_PADDING = "TranscodeEstimateBytePadding";
    private static final String KEY_DB_BACKUP_INTERVAL = "DbBackupUpdateInterval";
    private static final String KEY_DB_BACKUP_RETENTION_COUNT = "DbBackupRetentionCount";
    private static final String KEY_DOWNLOAD_BITRATE_LIMIT = "DownloadBitrateLimit";
    private static final String KEY_UPLOAD_BITRATE_LIMIT = "UploadBitrateLimit";
    private static final String KEY_DOWNSAMPLING_COMMAND = "DownsamplingCommand4";
    private static final String KEY_HLS_COMMAND = "HlsCommand4";
    private static final String KEY_JUKEBOX_COMMAND = "JukeboxCommand2";
    private static final String KEY_VIDEO_IMAGE_COMMAND = "VideoImageCommand";
    private static final String KEY_SUBTITLES_EXTRACTION_COMMAND = "SubtitlesExtractionCommand";
    private static final String KEY_LDAP_ENABLED = "LdapEnabled";
    private static final String KEY_LDAP_URL = "LdapUrl";
    private static final String KEY_LDAP_MANAGER_DN = "LdapManagerDn";
    private static final String KEY_LDAP_MANAGER_PASSWORD = "LdapManagerPassword";
    private static final String KEY_LDAP_SEARCH_FILTER = "LdapSearchFilter";
    private static final String KEY_LDAP_AUTO_SHADOWING = "LdapAutoShadowing";
    private static final String KEY_GETTING_STARTED_ENABLED = "GettingStartedEnabled";
    private static final String KEY_SETTINGS_CHANGED = "SettingsChanged";
    private static final String KEY_ORGANIZE_BY_FOLDER_STRUCTURE = "OrganizeByFolderStructure";
    private static final String KEY_SORT_ALBUMS_BY_YEAR = "SortAlbumsByYear";
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

    private static final String KEY_SMTP_SERVER = "SmtpServer";
    private static final String KEY_SMTP_ENCRYPTION = "SmtpEncryption";
    private static final String KEY_SMTP_PORT = "SmtpPort";
    private static final String KEY_SMTP_USER = "SmtpUser";
    private static final String KEY_SMTP_PASSWORD = "SmtpPassword";
    private static final String KEY_SMTP_FROM = "SmtpFrom";
    private static final String KEY_EXPORT_PLAYLIST_FORMAT = "PlaylistExportFormat";
    private static final String KEY_IGNORE_SYMLINKS = "IgnoreSymLinks";
    private static final String KEY_EXCLUDE_PATTERN_STRING = "ExcludePattern";

    private static final String KEY_CAPTCHA_ENABLED = "CaptchaEnabled";
    private static final String KEY_RECAPTCHA_SITE_KEY = "ReCaptchaSiteKey";
    private static final String KEY_RECAPTCHA_SECRET_KEY = "ReCaptchaSecretKey";

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
    private static final String DEFAULT_WELCOME_TITLE = "Welcome to Airsonic!";
    private static final String DEFAULT_WELCOME_SUBTITLE = null;
    private static final String DEFAULT_WELCOME_MESSAGE = "__Welcome to Airsonic!__\n" +
            "\\\\ \\\\\n" +
            "Airsonic is a free, web-based media streamer, providing ubiquitous access to your music. \n" +
            "\\\\ \\\\\n" +
            "Use it to share your music with friends, or to listen to your own music while at work. You can stream to multiple " +
            "players simultaneously, for instance to one player in your kitchen and another in your living room.\n" +
            "\\\\ \\\\\n" +
            "To change or remove this message, log in with administrator rights and go to <a href='settings.view'>Settings</a> > <a href='generalSettings.view'>General</a>.";
    private static final String DEFAULT_LOGIN_MESSAGE = null;
    private static final String DEFAULT_SESSION_TIMEOUT_DURATION = "30m";
    private static final int DEFAULT_INDEX_CREATION_INTERVAL = 1;
    private static final int DEFAULT_INDEX_CREATION_HOUR = 3;
    private static final boolean DEFAULT_FAST_CACHE_ENABLED = false;
    private static final boolean DEFAULT_FULL_SCAN = false;
    private static final boolean DEFAULT_CLEAR_FULL_SCAN_SETTING_AFTER_SCAN = false;
    private static final long DEFAULT_TRANSCODE_ESTIMATE_TIME_PADDING = 2000;
    private static final long DEFAULT_TRANSCODE_ESTIMATE_BYTE_PADDING = 0;
    private static final int DEFAULT_DB_BACKUP_INTERVAL = -1;
    private static final int DEFAULT_DB_BACKUP_RETENTION_COUNT = 2;
    private static final long DEFAULT_DOWNLOAD_BITRATE_LIMIT = 0;
    private static final long DEFAULT_UPLOAD_BITRATE_LIMIT = 0;
    private static final String DEFAULT_DOWNSAMPLING_COMMAND = "ffmpeg -i %s -map 0:0 -b:a %bk -v 0 -f mp3 -";
    private static final String DEFAULT_HLS_COMMAND = "ffmpeg -ss %o -i %s -s %wx%h -async 1 -c:v libx264 -flags +cgop -b:v %vk -maxrate %bk -preset superfast -copyts -b:a %rk -bufsize 256k -map 0:0 -map 0:%i -ac 2 -ar 44100 -v 0 -threads 0 -force_key_frames expr:gte(t,n_forced*10) -start_number %j -hls_time %d -hls_list_size 0 -hls_segment_filename %n %p";
    private static final String DEFAULT_JUKEBOX_COMMAND = "ffmpeg -ss %o -i %s -map 0:0 -v 0 -ar 44100 -ac 2 -f s16be -";
    private static final String DEFAULT_VIDEO_IMAGE_COMMAND = "ffmpeg -r 1 -ss %o -t 1 -i %s -s %wx%h -v 0 -f mjpeg -";
    private static final String DEFAULT_SUBTITLES_EXTRACTION_COMMAND = "ffmpeg -i %s -map 0:%i -f %f -";
    private static final boolean DEFAULT_LDAP_ENABLED = false;
    private static final String DEFAULT_LDAP_URL = "ldap://host.domain.com:389/cn=Users,dc=domain,dc=com";
    private static final String DEFAULT_LDAP_MANAGER_DN = null;
    private static final String DEFAULT_LDAP_MANAGER_PASSWORD = null;
    private static final String DEFAULT_LDAP_SEARCH_FILTER = "(sAMAccountName={0})";
    private static final boolean DEFAULT_LDAP_AUTO_SHADOWING = false;
    private static final boolean DEFAULT_GETTING_STARTED_ENABLED = true;
    private static final long DEFAULT_SETTINGS_CHANGED = 0L;
    private static final boolean DEFAULT_ORGANIZE_BY_FOLDER_STRUCTURE = true;
    private static final boolean DEFAULT_SORT_ALBUMS_BY_YEAR = true;
    private static final boolean DEFAULT_DLNA_ENABLED = false;
    private static final String DEFAULT_DLNA_SERVER_NAME = "Airsonic";
    private static final String DEFAULT_DLNA_SERVER_ID = null;
    private static final String DEFAULT_DLNA_BASE_LAN_URL = null;
    private static final int DEFAULT_UPNP_PORT = 4041;
    private static final String DEFAULT_EXPORT_PLAYLIST_FORMAT = "m3u";
    private static final boolean DEFAULT_IGNORE_SYMLINKS = false;
    private static final String DEFAULT_EXCLUDE_PATTERN_STRING = null;
    private static final String DEFAULT_PREFERRED_NONDECODABLE_PASSWORD_ENCODER = "bcrypt";
    private static final String DEFAULT_PREFERRED_DECODABLE_PASSWORD_ENCODER = "encrypted-AES-GCM";
    private static final boolean DEFAULT_PREFER_NONDECODABLE_PASSWORDS = true;

    private static final String DEFAULT_SMTP_SERVER = null;
    private static final String DEFAULT_SMTP_ENCRYPTION = "None";
    private static final String DEFAULT_SMTP_PORT = "25";
    private static final String DEFAULT_SMTP_USER = null;
    private static final String DEFAULT_SMTP_PASSWORD = null;
    private static final String DEFAULT_SMTP_FROM = "airsonic@airsonic.org";

    private static final boolean DEFAULT_CAPTCHA_ENABLED = false;
    private static final String DEFAULT_RECAPTCHA_SITE_KEY = "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI";
    private static final String DEFAULT_RECAPTCHA_SECRET_KEY = "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe";

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
        keyMaps.put("IgnoreFileTimestamps", KEY_FULL_SCAN);

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
        this.setLong(KEY_SETTINGS_CHANGED, System.currentTimeMillis());
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
    Set<String> getCoverArtFileTypesSet() {
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
        return StringUtils.trimToNull(getProperty(KEY_WELCOME_TITLE, DEFAULT_WELCOME_TITLE));
    }

    public void setWelcomeTitle(String title) {
        setProperty(KEY_WELCOME_TITLE, title);
    }

    public String getWelcomeSubtitle() {
        return StringUtils.trimToNull(getProperty(KEY_WELCOME_SUBTITLE, DEFAULT_WELCOME_SUBTITLE));
    }

    public void setWelcomeSubtitle(String subtitle) {
        setProperty(KEY_WELCOME_SUBTITLE, subtitle);
    }

    public String getWelcomeMessage() {
        return StringUtils.trimToNull(getProperty(KEY_WELCOME_MESSAGE, DEFAULT_WELCOME_MESSAGE));
    }

    public void setWelcomeMessage(String message) {
        setProperty(KEY_WELCOME_MESSAGE, message);
    }

    public String getLoginMessage() {
        return StringUtils.trimToNull(getProperty(KEY_LOGIN_MESSAGE, DEFAULT_LOGIN_MESSAGE));
    }

    public void setLoginMessage(String message) {
        setProperty(KEY_LOGIN_MESSAGE, message);
    }

    public String getSessionDuration() {
        return getProperty(KEY_SESSION_DURATION, DEFAULT_SESSION_TIMEOUT_DURATION);
    }

    public void setSessionDuration(String sessionTimeout) {
        setProperty(KEY_SESSION_DURATION, sessionTimeout);
    }

    /**
     * Returns the number of days between automatic index creation, of -1 if automatic index
     * creation is disabled.
     */
    public int getIndexCreationInterval() {
        return getInt(KEY_INDEX_CREATION_INTERVAL, DEFAULT_INDEX_CREATION_INTERVAL);
    }

    /**
     * Sets the number of days between automatic index creation, of -1 if automatic index
     * creation is disabled.
     */
    public void setIndexCreationInterval(int days) {
        setInt(KEY_INDEX_CREATION_INTERVAL, days);
    }

    /**
     * Returns the hour of day (0 - 23) when automatic index creation should run.
     */
    public int getIndexCreationHour() {
        return getInt(KEY_INDEX_CREATION_HOUR, DEFAULT_INDEX_CREATION_HOUR);
    }

    /**
     * Sets the hour of day (0 - 23) when automatic index creation should run.
     */
    public void setIndexCreationHour(int hour) {
        setInt(KEY_INDEX_CREATION_HOUR, hour);
    }

    public boolean isFastCacheEnabled() {
        return getBoolean(KEY_FAST_CACHE_ENABLED, DEFAULT_FAST_CACHE_ENABLED);
    }

    public void setFastCacheEnabled(boolean enabled) {
        setBoolean(KEY_FAST_CACHE_ENABLED, enabled);
    }

    public boolean getFullScan() {
        return getBoolean(KEY_FULL_SCAN, DEFAULT_FULL_SCAN);
    }

    public void setFullScan(Boolean fullscan) {
        setBoolean(KEY_FULL_SCAN, fullscan);
    }

    public boolean getClearFullScanSettingAfterScan() {
        return getBoolean(KEY_CLEAR_FULL_SCAN_SETTING_AFTER_SCAN, DEFAULT_CLEAR_FULL_SCAN_SETTING_AFTER_SCAN);
    }

    public void setClearFullScanSettingAfterScan(Boolean clear) {
        setBoolean(KEY_CLEAR_FULL_SCAN_SETTING_AFTER_SCAN, clear);
    }

    public long getTranscodeEstimateTimePadding() {
        return getLong(KEY_TRANSCODE_ESTIMATE_TIME_PADDING, DEFAULT_TRANSCODE_ESTIMATE_TIME_PADDING);
    }

    public void setTranscodeEstimateTimePadding(Long timeInMillis) {
        setLong(KEY_TRANSCODE_ESTIMATE_TIME_PADDING, timeInMillis);
    }

    public long getTranscodeEstimateBytePadding() {
        return getLong(KEY_TRANSCODE_ESTIMATE_BYTE_PADDING, DEFAULT_TRANSCODE_ESTIMATE_BYTE_PADDING);
    }

    public void setTranscodeEstimateBytePadding(Long bytes) {
        setLong(KEY_TRANSCODE_ESTIMATE_BYTE_PADDING, bytes);
    }

    public int getDbBackupInterval() {
        return getInt(KEY_DB_BACKUP_INTERVAL, DEFAULT_DB_BACKUP_INTERVAL);
    }

    public void setDbBackupInterval(int hours) {
        setInt(KEY_DB_BACKUP_INTERVAL, hours);
    }

    public int getDbBackupRetentionCount() {
        return getInt(KEY_DB_BACKUP_RETENTION_COUNT, DEFAULT_DB_BACKUP_RETENTION_COUNT);
    }

    public void setDbBackupRetentionCount(int count) {
        setInt(KEY_DB_BACKUP_RETENTION_COUNT, count);
    }

    /**
     * @return The download bitrate limit in Kbit/s. Zero if unlimited.
     */
    public long getDownloadBitrateLimit() {
        return getLong(KEY_DOWNLOAD_BITRATE_LIMIT, DEFAULT_DOWNLOAD_BITRATE_LIMIT);
    }

    public RateLimiter getDownloadBitrateLimiter() {
        return (RateLimiter) valueStore.derived(KEY_DOWNLOAD_BITRATE_LIMIT,
            k -> RateLimiter.create(adjustBitrateLimit(getDownloadBitrateLimit())));
    }

    /**
     * Convert rate given in KB to bytes and accounts for 0 (meaning no bitrate)
     */
    private static Double adjustBitrateLimit(double rate) {
        double rateLimitInBytes = rate * 1024.0;
        if (rate == 0) {
            rateLimitInBytes = Double.POSITIVE_INFINITY;
        }

        return rateLimitInBytes;
    }

    /**
     * @param limit The download bitrate limit in Kbit/s. Zero if unlimited.
     */
    public void setDownloadBitrateLimit(long limit) {
        setLong(KEY_DOWNLOAD_BITRATE_LIMIT, limit);
        getDownloadBitrateLimiter().setRate(adjustBitrateLimit(limit));
    }

    /**
     * @return The upload bitrate limit in Kbit/s. Zero if unlimited.
     */
    public long getUploadBitrateLimit() {
        return getLong(KEY_UPLOAD_BITRATE_LIMIT, DEFAULT_UPLOAD_BITRATE_LIMIT);
    }

    public RateLimiter getUploadBitrateLimiter() {
        return (RateLimiter) valueStore.derived(KEY_UPLOAD_BITRATE_LIMIT,
            k -> RateLimiter.create(adjustBitrateLimit(getUploadBitrateLimit())));
    }

    /**
     * @param limit The upload bitrate limit in Kbit/s. Zero if unlimited.
     */
    public void setUploadBitrateLimit(long limit) {
        setLong(KEY_UPLOAD_BITRATE_LIMIT, limit);
        getUploadBitrateLimiter().setRate(adjustBitrateLimit(limit));
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
        return getBoolean(KEY_LDAP_ENABLED, DEFAULT_LDAP_ENABLED);
    }

    public void setLdapEnabled(boolean ldapEnabled) {
        setBoolean(KEY_LDAP_ENABLED, ldapEnabled);
    }

    public String getLdapUrl() {
        return getProperty(KEY_LDAP_URL, DEFAULT_LDAP_URL);
    }

    public void setLdapUrl(String ldapUrl) {
        setProperty(KEY_LDAP_URL, ldapUrl);
    }

    public String getLdapSearchFilter() {
        return getProperty(KEY_LDAP_SEARCH_FILTER, DEFAULT_LDAP_SEARCH_FILTER);
    }

    public void setLdapSearchFilter(String ldapSearchFilter) {
        setProperty(KEY_LDAP_SEARCH_FILTER, ldapSearchFilter);
    }

    public String getLdapManagerDn() {
        return getProperty(KEY_LDAP_MANAGER_DN, DEFAULT_LDAP_MANAGER_DN);
    }

    public void setLdapManagerDn(String ldapManagerDn) {
        setProperty(KEY_LDAP_MANAGER_DN, ldapManagerDn);
    }

    public String getLdapManagerPassword() {
        String s = getProperty(KEY_LDAP_MANAGER_PASSWORD, DEFAULT_LDAP_MANAGER_PASSWORD);
        try {
            return StringUtil.utf8HexDecode(s);
        } catch (Exception x) {
            LOG.warn("Failed to decode LDAP manager password.", x);
            return s;
        }
    }

    public void setLdapManagerPassword(String ldapManagerPassword) {
        try {
            ldapManagerPassword = StringUtil.utf8HexEncode(ldapManagerPassword);
        } catch (Exception x) {
            LOG.warn("Failed to encode LDAP manager password.", x);
        }
        setProperty(KEY_LDAP_MANAGER_PASSWORD, ldapManagerPassword);
    }

    public boolean isLdapAutoShadowing() {
        return getBoolean(KEY_LDAP_AUTO_SHADOWING, DEFAULT_LDAP_AUTO_SHADOWING);
    }

    public void setLdapAutoShadowing(boolean ldapAutoShadowing) {
        setBoolean(KEY_LDAP_AUTO_SHADOWING, ldapAutoShadowing);
    }

    public boolean isGettingStartedEnabled() {
        return getBoolean(KEY_GETTING_STARTED_ENABLED, DEFAULT_GETTING_STARTED_ENABLED);
    }

    public void setGettingStartedEnabled(boolean isGettingStartedEnabled) {
        setBoolean(KEY_GETTING_STARTED_ENABLED, isGettingStartedEnabled);
    }

    public long getSettingsChanged() {
        return getLong(KEY_SETTINGS_CHANGED, DEFAULT_SETTINGS_CHANGED);
    }

    public boolean isOrganizeByFolderStructure() {
        return getBoolean(KEY_ORGANIZE_BY_FOLDER_STRUCTURE, DEFAULT_ORGANIZE_BY_FOLDER_STRUCTURE);
    }

    public void setOrganizeByFolderStructure(boolean b) {
        setBoolean(KEY_ORGANIZE_BY_FOLDER_STRUCTURE, b);
    }

    public boolean isSortAlbumsByYear() {
        return getBoolean(KEY_SORT_ALBUMS_BY_YEAR, DEFAULT_SORT_ALBUMS_BY_YEAR);
    }

    public void setSortAlbumsByYear(boolean b) {
        setBoolean(KEY_SORT_ALBUMS_BY_YEAR, b);
    }

    public boolean getIgnoreSymLinks() {
        return getBoolean(KEY_IGNORE_SYMLINKS, DEFAULT_IGNORE_SYMLINKS);
    }

    public void setIgnoreSymLinks(boolean b) {
        setBoolean(KEY_IGNORE_SYMLINKS, b);
    }

    public String getExcludePatternString() {
        return getString(KEY_EXCLUDE_PATTERN_STRING, DEFAULT_EXCLUDE_PATTERN_STRING);
    }

    public void setExcludePatternString(String s) {
        setString(KEY_EXCLUDE_PATTERN_STRING, s);
        valueStore.invalidateDerived(KEY_EXCLUDE_PATTERN_STRING);
    }

    @SuppressWarnings("unchecked")
    public Pattern getExcludePattern() {
        return ((Optional<Pattern>) valueStore.derived(KEY_EXCLUDE_PATTERN_STRING,
            k -> Optional.ofNullable(getExcludePatternString()).map(StringUtils::trimToNull).map(Pattern::compile))).orElse(null);
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
        return getProperty(KEY_SMTP_SERVER, DEFAULT_SMTP_SERVER);
    }

    public void setSmtpServer(String smtpServer) {
        setString(KEY_SMTP_SERVER, smtpServer);
    }

    public String getSmtpPort() {
        return getString(KEY_SMTP_PORT, DEFAULT_SMTP_PORT);
    }

    public void setSmtpPort(String smtpPort) {
        setString(KEY_SMTP_PORT, smtpPort);
    }

    public String getSmtpEncryption() {
        return getProperty(KEY_SMTP_ENCRYPTION, DEFAULT_SMTP_ENCRYPTION);
    }

    public void setSmtpEncryption(String encryptionMethod) {
        setString(KEY_SMTP_ENCRYPTION, encryptionMethod);
    }

    public String getSmtpUser() {
        return getProperty(KEY_SMTP_USER, DEFAULT_SMTP_USER);
    }

    public void setSmtpUser(String smtpUser) {
        setString(KEY_SMTP_USER, smtpUser);
    }

    public String getSmtpPassword() {
        String s = getProperty(KEY_SMTP_PASSWORD, DEFAULT_SMTP_PASSWORD);
        try {
            return StringUtil.utf8HexDecode(s);
        } catch (Exception x) {
            LOG.warn("Failed to decode Smtp password.", x);
            return s;
        }
    }

    public void setSmtpPassword(String smtpPassword) {
        try {
            smtpPassword = StringUtil.utf8HexEncode(smtpPassword);
        } catch (Exception x) {
            LOG.warn("Failed to encode Smtp password.", x);
        }
        setProperty(KEY_SMTP_PASSWORD, smtpPassword);
    }

    public String getSmtpFrom() {
        return getProperty(KEY_SMTP_FROM, DEFAULT_SMTP_FROM);
    }

    public void setSmtpFrom(String smtpFrom) {
        setString(KEY_SMTP_FROM, smtpFrom);
    }

    public boolean isCaptchaEnabled() {
        return getBoolean(KEY_CAPTCHA_ENABLED, DEFAULT_CAPTCHA_ENABLED);
    }

    public void setCaptchaEnabled(boolean captchaEnabled) {
        setBoolean(KEY_CAPTCHA_ENABLED, captchaEnabled);
    }

    public String getRecaptchaSiteKey() {
        return getProperty(KEY_RECAPTCHA_SITE_KEY, DEFAULT_RECAPTCHA_SITE_KEY);
    }

    public void setRecaptchaSiteKey(String recaptchaSiteKey) {
        setString(KEY_RECAPTCHA_SITE_KEY, recaptchaSiteKey);
    }

    public String getRecaptchaSecretKey() {
        return getProperty(KEY_RECAPTCHA_SECRET_KEY, DEFAULT_RECAPTCHA_SECRET_KEY);
    }

    public void setRecaptchaSecretKey(String recaptchaSecretKey) {
        setString(KEY_RECAPTCHA_SECRET_KEY, recaptchaSecretKey);
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
