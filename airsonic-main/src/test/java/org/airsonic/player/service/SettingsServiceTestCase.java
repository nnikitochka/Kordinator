package org.airsonic.player.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.RateLimiter;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.AvatarDao;
import org.airsonic.player.dao.UserDao;
import org.airsonic.player.util.HomeRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit test of {@link SettingsService}.
 *
 * @author Sindre Mehus
 */

public class SettingsServiceTestCase {

    @ClassRule
    public static HomeRule home = new HomeRule();

    private SettingsService settingsService;

    @Before
    public void setUp() throws IOException {
        TestCaseUtils.cleanAirsonicHomeForTest();
        ConfigurationPropertiesService.reset();

        settingsService = newSettingsService();
    }

    private SettingsService newSettingsService() {
        SettingsService settingsService = new SettingsService(mock(UserDao.class), mock(AvatarDao.class),
                new org.airsonic.player.runtime.RuntimeConfiguration(Collections.emptyMap(),
                        this::configurationProperty, null), null, ConfigurationPropertiesService.getInstance());
        return settingsService;
    }

    private String configurationProperty(String name) {
        return ConfigurationPropertiesService.getInstance().getConfiguration().getString(name);
    }

    @Test
    public void testAirsonicHome() {
        assertEquals("Wrong Airsonic home.", TestCaseUtils.airsonicHomePathForTest(), SettingsService.getAirsonicHome().toString());
    }

    @Test
    public void testDefaultValues() {
        assertEquals("Wrong default language.", "en", settingsService.getLocale().getLanguage());
        assertEquals("Wrong default index creation interval.", 1, settingsService.getIndexCreationInterval());
        assertEquals("Wrong default index creation hour.", 3, settingsService.getIndexCreationHour());
        assertTrue("Wrong default playlist folder.", settingsService.getPlaylistFolder().endsWith("playlists"));
        assertEquals("Wrong default theme.", "default", settingsService.getThemeId());
        assertEquals("Wrong default LDAP enabled.", false, settingsService.isLdapEnabled());
        assertEquals("Wrong default LDAP URL.", "ldap://host.domain.com:389/cn=Users,dc=domain,dc=com", settingsService.getLdapUrl());
        assertNull("Wrong default LDAP manager DN.", settingsService.getLdapManagerDn());
        assertNull("Wrong default LDAP manager password.", settingsService.getLdapManagerPassword());
        assertEquals("Wrong default LDAP search filter.", "(sAMAccountName={0})", settingsService.getLdapSearchFilter());
        assertEquals("Wrong default LDAP auto-shadowing.", false, settingsService.isLdapAutoShadowing());
        assertNull("Wrong default SMTP server.", settingsService.getSmtpServer());
        assertEquals("Wrong default SMTP port.", "25", settingsService.getSmtpPort());
        assertEquals("Wrong default SMTP encryption.", "None", settingsService.getSmtpEncryption());
        assertEquals("Wrong default SMTP from address.", "airsonic@airsonic.org", settingsService.getSmtpFrom());
        assertFalse("Wrong default CAPTCHA setting.", settingsService.isCaptchaEnabled());
        assertEquals("30m", settingsService.getSessionDuration());
    }

    @Test
    public void contextualUploadPathResolvesContextKeysAndListIndexes() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("USER_NAME", "alice");
        context.put("USER_MUSIC_FOLDERS", List.of("/music/alice", "/shared"));

        assertEquals("/incoming/alice/from-/music/alice",
                settingsService.resolveContextualString("/incoming/%{['USER_NAME']}/from-%{['USER_MUSIC_FOLDERS'][0]}",
                        () -> context));
    }

    @Test
    public void bitrateLimitsUpdateCachedLimitersAndZeroMeansUnlimited() {
        RateLimiter downloadLimiter = settingsService.getDownloadBitrateLimiter();
        RateLimiter uploadLimiter = settingsService.getUploadBitrateLimiter();

        settingsService.setDownloadBitrateLimit(3);
        settingsService.setUploadBitrateLimit(5);
        assertSame(downloadLimiter, settingsService.getDownloadBitrateLimiter());
        assertSame(uploadLimiter, settingsService.getUploadBitrateLimiter());
        assertEquals(3 * 1024.0, downloadLimiter.getRate(), 0.01);
        assertEquals(5 * 1024.0, uploadLimiter.getRate(), 0.01);

        settingsService.setDownloadBitrateLimit(0);
        settingsService.setUploadBitrateLimit(0);
        assertTrue(Double.isInfinite(downloadLimiter.getRate()));
        assertTrue(Double.isInfinite(uploadLimiter.getRate()));
    }

    @Test
    public void testChangeSettings() {
        settingsService.setIndexString("indexString");
        settingsService.setIgnoredArticles("a the foo bar");
        settingsService.setShortcuts("new incoming \"rock 'n' roll\"");
        settingsService.setPlaylistFolder("playlistFolder");
        settingsService.setMusicFileTypes("mp3 ogg  aac");
        settingsService.setCoverArtFileTypes("jpeg gif  png");
        settingsService.setWelcomeMessage("welcomeMessage");
        settingsService.setLoginMessage("loginMessage");
        settingsService.setSessionDuration("60m");
        settingsService.setLocale(Locale.CANADA_FRENCH);
        settingsService.setThemeId("dark");
        settingsService.setIndexCreationInterval(4);
        settingsService.setIndexCreationHour(9);
        settingsService.setLdapEnabled(true);
        settingsService.setLdapUrl("newLdapUrl");
        settingsService.setLdapManagerDn("admin");
        settingsService.setLdapManagerPassword("secret");
        settingsService.setLdapSearchFilter("newLdapSearchFilter");
        settingsService.setLdapAutoShadowing(true);
        settingsService.setSmtpServer("smtp.example.test");
        settingsService.setSmtpPort("587");
        settingsService.setSmtpEncryption("STARTTLS");
        settingsService.setSmtpUser("mailer");
        settingsService.setSmtpPassword("smtp-secret");
        settingsService.setSmtpFrom("music@example.test");
        settingsService.setCaptchaEnabled(true);
        settingsService.setRecaptchaSiteKey("site-key");
        settingsService.setRecaptchaSecretKey("secret-key");

        verifySettings(settingsService);

        settingsService.save();
        verifySettings(settingsService);

        verifySettings(newSettingsService());
    }

    private void verifySettings(SettingsService ss) {
        assertEquals("Wrong index string.", "indexString", ss.getIndexString());
        assertEquals("Wrong ignored articles.", "a the foo bar", ss.getIgnoredArticles());
        assertEquals("Wrong shortcuts.", "new incoming \"rock 'n' roll\"", ss.getShortcuts());
        assertTrue("Wrong ignored articles array.", Arrays.equals(new String[] {"a", "the", "foo", "bar"}, ss.getIgnoredArticlesAsArray()));
        assertTrue("Wrong shortcut array.", Arrays.equals(new String[] {"new", "incoming", "rock 'n' roll"}, ss.getShortcutsAsArray()));
        assertEquals("Wrong playlist folder.", "playlistFolder", ss.getPlaylistFolder());
        assertEquals("Wrong music mask.", "mp3 ogg  aac", ss.getMusicFileTypes());
        assertThat(ss.getMusicFileTypesSet()).containsOnly("mp3", "ogg", "aac");
        assertEquals("Wrong cover art mask.", "jpeg gif  png", ss.getCoverArtFileTypes());
        assertThat(ss.getCoverArtFileTypesSet()).containsOnly("jpeg", "gif", "png");
        assertEquals("Wrong welcome message.", "welcomeMessage", ss.getWelcomeMessage());
        assertEquals("Wrong login message.", "loginMessage", ss.getLoginMessage());
        assertEquals("Wrong session duration.", "60m", settingsService.getSessionDuration());
        assertEquals("Wrong locale.", Locale.CANADA_FRENCH, ss.getLocale());
        assertEquals("Wrong theme.", "dark", ss.getThemeId());
        assertEquals("Wrong index creation interval.", 4, ss.getIndexCreationInterval());
        assertEquals("Wrong index creation hour.", 9, ss.getIndexCreationHour());
        assertTrue("Wrong LDAP enabled.", settingsService.isLdapEnabled());
        assertEquals("Wrong LDAP URL.", "newLdapUrl", settingsService.getLdapUrl());
        assertEquals("Wrong LDAP manager DN.", "admin", settingsService.getLdapManagerDn());
        assertEquals("Wrong LDAP manager password.", "secret", settingsService.getLdapManagerPassword());
        assertEquals("Wrong LDAP search filter.", "newLdapSearchFilter", settingsService.getLdapSearchFilter());
        assertTrue("Wrong LDAP auto-shadowing.", settingsService.isLdapAutoShadowing());
        assertEquals("Wrong SMTP server.", "smtp.example.test", ss.getSmtpServer());
        assertEquals("Wrong SMTP port.", "587", ss.getSmtpPort());
        assertEquals("Wrong SMTP encryption.", "STARTTLS", ss.getSmtpEncryption());
        assertEquals("Wrong SMTP user.", "mailer", ss.getSmtpUser());
        assertEquals("Wrong SMTP password.", "smtp-secret", ss.getSmtpPassword());
        assertEquals("Wrong SMTP from address.", "music@example.test", ss.getSmtpFrom());
        assertTrue("Wrong CAPTCHA setting.", ss.isCaptchaEnabled());
        assertEquals("Wrong reCAPTCHA site key.", "site-key", ss.getRecaptchaSiteKey());
        assertEquals("Wrong reCAPTCHA secret key.", "secret-key", ss.getRecaptchaSecretKey());
    }

    @Test
    public void migratePropFileKeys_noKeys() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertNull(configurationProperty("bla"));
        assertNull(configurationProperty("bla2"));
        assertNull(configurationProperty("bla3"));
    }

    @Test
    public void migratePropFileKeys_deleteKeys_BackwardsCompatibilitySet() {
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", null);
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertEquals("hello", configurationProperty("bla"));
        assertEquals("hello", configurationProperty("bla2"));
        assertEquals("hello", configurationProperty("bla3"));
    }

    @Test
    public void migratePropFileKeys_deleteKeys_NonBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty(SettingsService.KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS, "false");
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", null);
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertNull(configurationProperty("bla"));
        assertNull(configurationProperty("bla2"));
        assertNull(configurationProperty("bla3"));
    }

    @Test
    public void migratePropFileKeys_withKeys_ExplicitlyBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty(SettingsService.KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS, "true");
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");
        ConfigurationPropertiesService.getInstance().setProperty("bla3", "hello2");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");
        keyMaps.put("bla4", "bla5");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertEquals("hello", configurationProperty("bla"));
        assertEquals("hello", configurationProperty("bla2"));
        assertEquals("hello2", configurationProperty("bla3"));
        assertEquals("hello2", configurationProperty("bla4"));
        assertEquals("hello2", configurationProperty("bla5"));
    }

    @Test
    public void migratePropFileKeys_withKeys_ImplicitlyBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");
        ConfigurationPropertiesService.getInstance().setProperty("bla3", "hello2");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");
        keyMaps.put("bla4", "bla5");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertEquals("hello", configurationProperty("bla"));
        assertEquals("hello", configurationProperty("bla2"));
        assertEquals("hello2", configurationProperty("bla3"));
        assertEquals("hello2", configurationProperty("bla4"));
        assertEquals("hello2", configurationProperty("bla5"));
    }

    @Test
    public void migratePropFileKeys_withKeys_NonBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty(SettingsService.KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS, "false");
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");
        ConfigurationPropertiesService.getInstance().setProperty("bla3", "hello2");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");
        keyMaps.put("bla4", "bla5");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertNull(configurationProperty("bla"));
        assertNull(configurationProperty("bla2"));
        assertNull(configurationProperty("bla3"));
        assertNull(configurationProperty("bla4"));
        assertEquals("hello2", configurationProperty("bla5"));
    }

    @Test
    public void migrateEnvKeys_noKeys() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        Map<String, Object> migrated = new LinkedHashMap<>();
        migratePropertySourceKeys(keyMaps, Collections.emptyMap(), migrated);

        assertThat(migrated).isEmpty();
    }

    @Test
    public void migrateEnvKeys_keyChainPrecedence1() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");

        Map<String, Object> migrated = new LinkedHashMap<>();
        // higher precedence starts earlier in the chain order
        migratePropertySourceKeys(keyMaps,
                ImmutableMap.of("bla", "1"), migrated);
        migratePropertySourceKeys(keyMaps,
                ImmutableMap.of("bla3", "3"), migrated);

        assertThat(migrated).containsOnly(entry("bla2", "1"), entry("bla3", "1"), entry("bla4", "1"));
    }

    @Test
    public void migrateEnvKeys_keyChainPrecedence2() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");

        Map<String, Object> migrated = new LinkedHashMap<>();
        // higher precedence starts later in the chain order
        migratePropertySourceKeys(keyMaps,
                ImmutableMap.of("bla3", "1"), migrated);
        migratePropertySourceKeys(keyMaps,
                ImmutableMap.of("bla", "3"), migrated);

        assertThat(migrated).containsOnly(entry("bla2", "3"), entry("bla3", "3"), entry("bla4", "1"));
    }

    @Test
    public void migrateEnvKeys_deleteKeys() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", null);

        Map<String, Object> migrated = new LinkedHashMap<>();
        // higher precedence starts later in the chain order
        migratePropertySourceKeys(keyMaps,
                ImmutableMap.of("bla3", "1"), migrated);
        migratePropertySourceKeys(keyMaps,
                ImmutableMap.of("bla", "3"), migrated);

        assertThat(migrated).containsOnly(entry("bla2", "3"), entry("bla3", "3"));
    }

    private static void migratePropertySourceKeys(Map<String, String> keyMaps,
            Map<String, ?> source, Map<String, Object> migrated) {
        SettingsService.migrateKeys(keyMaps, source::get, "test properties", migrated);
    }
}
