package org.airsonic.player.service;

import com.google.common.util.concurrent.RateLimiter;
import org.airsonic.player.dao.AvatarDao;
import org.airsonic.player.dao.UserDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.settings.*;
import org.airsonic.player.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.airsonic.player.runtime.RuntimeConfiguration;

import javax.cache.Cache;
import javax.cache.CacheManager;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Provides persistent storage of application settings and preferences.
 */
public class SettingsService {
    // Global settings.
    private static final String KEY_REMEMBER_ME_KEY = "RememberMeKey";

    private static final String KEY_EXPORT_PLAYLIST_FORMAT = "PlaylistExportFormat";

    // Database Settings
    public static final String KEY_DATABASE_URL = RuntimeConfiguration.DATABASE_URL_KEY;
    public static final String KEY_DATABASE_USERNAME = RuntimeConfiguration.DATABASE_USERNAME_KEY;
    public static final String KEY_DATABASE_PASSWORD = RuntimeConfiguration.DATABASE_PASSWORD_KEY;
    public static final String KEY_DATABASE_JNDI_NAME = RuntimeConfiguration.DATABASE_JNDI_NAME_KEY;
    public static final String KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS = "PropertiesFileRetainObsoleteKeys";

    // Default values.
    private static final String DEFAULT_EXPORT_PLAYLIST_FORMAT = "m3u";

    private static final String DEFAULT_DATABASE_DRIVER = null;
    private static final String DEFAULT_DATABASE_URL = null;
    private static final String DEFAULT_DATABASE_USERNAME = null;
    private static final String DEFAULT_DATABASE_PASSWORD = null;
    private static final String DEFAULT_DATABASE_JNDI_NAME = null;
    private static final Integer DEFAULT_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH = 384;

    private static final Logger LOG = LoggerFactory.getLogger(SettingsService.class);
    private static final String LOG_FILE_NAME_PROPERTY = "logging.file.name";

    private final SettingsUserPreferences userPreferences;
    private final SettingsPresentationPreferences presentationPreferences;
    private final SettingsCatalogPreferences catalogPreferences;
    private final SettingsOperationalPreferences operationalPreferences;
    private final SettingsRateLimitPreferences rateLimitPreferences;
    private final SettingsLdapPreferences ldapPreferences;
    private final SettingsMailPreferences mailPreferences;
    private final SettingsDlnaPreferences dlnaPreferences;
    private final SettingsCredentialPreferences credentialPreferences;
    private final SettingsDatabasePreferences databasePreferences;
    private final SettingsTranscodingPreferences transcodingPreferences;
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
        this.dlnaPreferences = new SettingsDlnaPreferences(valueStore);
        this.credentialPreferences = new SettingsCredentialPreferences(valueStore);
        this.databasePreferences = new SettingsDatabasePreferences(valueStore);
        this.transcodingPreferences = new SettingsTranscodingPreferences(valueStore);
        Cache<String, UserSettings> userSettingsCache = cacheManager != null
                ? cacheManager.getCache("userSettingsCache", String.class, UserSettings.class) : null;
        this.userPreferences = new SettingsUserPreferences(userDao, avatarDao, userSettingsCache);
    }

    public static Map<String, String> getMigratedPropertyKeys() {
        return SettingsPropertyMigration.getMigratedPropertyKeys();
    }

    public static void migrateKeys(Map<String, String> keyMaps, Function<String, Object> propertyLookup, String sourceName, Map<String, Object> migrated) {
        SettingsPropertyMigration.migrateKeys(keyMaps, propertyLookup, sourceName, migrated);
    }

    public static void migratePropFileKeys(Map<String, String> keyMaps, ConfigurationPropertiesService cps) {
        SettingsPropertyMigration.migratePropertyFileKeys(keyMaps, cps);
    }

    public static void setDefaultConstants(Function<String, String> propertyLookup,
                                           Map<String, Object> defaultConstants) {
        SettingsPropertyMigration.setDefaultConstants(propertyLookup, defaultConstants);
    }

    public static Path getAirsonicHome() {
        return SettingsEnvironment.getAirsonicHome();
    }

    /**
     * Returns the directory in which all transcoders are installed.
     */
    public static Path getTranscodeDirectory() {
        return SettingsEnvironment.getTranscodeDirectory();
    }

    public static String resolveTranscodeExecutable(String executable, String defaultValue) {
        return SettingsEnvironment.resolveTranscodeExecutable(executable, defaultValue);
    }

    public static String getDefaultJDBCUrl() {
        return SettingsEnvironment.getDefaultJDBCUrl();
    }

    public static String getDefaultJDBCUsername() {
        return SettingsEnvironment.getDefaultJDBCUsername();
    }

    public static String getDefaultJDBCPassword() {
        return SettingsEnvironment.getDefaultJDBCPassword();
    }

    public int getUPnpPort() {
        return dlnaPreferences.getUpnpPort();
    }

    public static String getDefaultLogFile() {
        return SettingsEnvironment.getDefaultLogFile();
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

    static Path getPropertyFile() {
        return SettingsEnvironment.getPropertyFile();
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
        return transcodingPreferences.getDownsamplingCommand();
    }

    public void setDownsamplingCommand(String command) {
        transcodingPreferences.setDownsamplingCommand(command);
    }

    public String getHlsCommand() {
        return transcodingPreferences.getHlsCommand();
    }

    public void setHlsCommand(String command) {
        transcodingPreferences.setHlsCommand(command);
    }

    public String getJukeboxCommand() {
        return transcodingPreferences.getJukeboxCommand();
    }

    public void setJukeboxCommand(String command) {
        transcodingPreferences.setJukeboxCommand(command);
    }

    public String getVideoImageCommand() {
        return transcodingPreferences.getVideoImageCommand();
    }

    public void setVideoImageCommand(String command) {
        transcodingPreferences.setVideoImageCommand(command);
    }

    public String getSubtitlesExtractionCommand() {
        return transcodingPreferences.getSubtitlesExtractionCommand();
    }

    public void setSubtitlesExtractionCommand(String command) {
        transcodingPreferences.setSubtitlesExtractionCommand(command);
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
        return dlnaPreferences.isEnabled();
    }

    public void setDlnaEnabled(boolean dlnaEnabled) {
        dlnaPreferences.setEnabled(dlnaEnabled);
    }

    public String getDlnaServerName() {
        return dlnaPreferences.getServerName();
    }

    public void setDlnaServerName(String dlnaServerName) {
        dlnaPreferences.setServerName(dlnaServerName);
    }

    public String getDlnaServerId() {
        return dlnaPreferences.getServerId();
    }

    public void setDlnaServerId(String dlnaServerId) {
        dlnaPreferences.setServerId(dlnaServerId);
    }

    public String getDlnaBaseLANURL() {
        return dlnaPreferences.getBaseLanUrl();
    }

    public void setDlnaBaseLANURL(String dlnaBaseLANURL) {
        dlnaPreferences.setBaseLanUrl(dlnaBaseLANURL);
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
        return databasePreferences.getDriver();
    }

    public void setDatabaseDriver(String driver) {
        databasePreferences.setDriver(driver);
    }

    public String getDatabaseUrl() {
        return databasePreferences.getUrl();
    }

    public void setDatabaseUrl(String url) {
        databasePreferences.setUrl(url);
    }

    public String getDatabaseUsername() {
        return databasePreferences.getUsername();
    }

    public void setDatabaseUsername(String username) {
        databasePreferences.setUsername(username);
    }

    public String getDatabasePassword() {
        return databasePreferences.getPassword();
    }

    public void setDatabasePassword(String password) {
        databasePreferences.setPassword(password);
    }

    public String getDatabaseJNDIName() {
        return databasePreferences.getJndiName();
    }

    public void setDatabaseJNDIName(String jndiName) {
        databasePreferences.setJndiName(jndiName);
    }

    public Integer getDatabaseMysqlVarcharMaxlength() {
        return databasePreferences.getMysqlVarcharMaxlength();
    }

    public void setDatabaseMysqlVarcharMaxlength(int maxlength) {
        databasePreferences.setMysqlVarcharMaxlength(maxlength);
    }

    public String getJWTKey() {
        return credentialPreferences.getJwtKey();
    }

    public void setJWTKey(String jwtKey) {
        credentialPreferences.setJwtKey(jwtKey);
    }

    public String getEncryptionPassword() {
        return credentialPreferences.getEncryptionPassword();
    }

    public void setEncryptionPassword(String key) {
        credentialPreferences.setEncryptionPassword(key);
    }

    public String getEncryptionSalt() {
        return credentialPreferences.getEncryptionSalt();
    }

    public void setEncryptionSalt(String salt) {
        credentialPreferences.setEncryptionSalt(salt);
    }

    public String getDecodablePasswordEncoder() {
        return credentialPreferences.getDecodablePasswordEncoder();
    }

    public void setDecodablePasswordEncoder(String encoder) {
        credentialPreferences.setDecodablePasswordEncoder(encoder);
    }

    public String getNonDecodablePasswordEncoder() {
        return credentialPreferences.getNonDecodablePasswordEncoder();
    }

    public void setNonDecodablePasswordEncoder(String encoder) {
        credentialPreferences.setNonDecodablePasswordEncoder(encoder);
    }

    public boolean getPreferNonDecodablePasswords() {
        return credentialPreferences.getPreferNonDecodablePasswords();
    }

    public void setPreferNonDecodablePasswords(boolean preference) {
        credentialPreferences.setPreferNonDecodablePasswords(preference);
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
