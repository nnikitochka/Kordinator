package org.airsonic.player.spring;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.CompositeConfiguration;
import org.airsonic.player.service.SettingsService;
import org.junit.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CommonsConfigurationPropertySourceTest {

    @Test
    public void adaptsScalarArrayMissingAndDefaultValuesAndEnumeratesKeys() {
        BaseConfiguration explicit = new BaseConfiguration();
        explicit.setProperty("scalar", "value");
        explicit.setProperty("multiple", Arrays.asList("first", "second"));
        explicit.setProperty("empty", "");
        BaseConfiguration defaults = new BaseConfiguration();
        defaults.setProperty("from.defaults", "default-value");
        CompositeConfiguration configuration = new CompositeConfiguration();
        configuration.addConfiguration(explicit);
        configuration.addConfiguration(defaults);

        CommonsConfigurationPropertySource source = new CommonsConfigurationPropertySource("airsonic-properties", configuration);

        assertEquals("value", source.getProperty("scalar"));
        assertArrayEquals(new String[]{"first", "second"}, (String[]) source.getProperty("multiple"));
        assertEquals("", source.getProperty("empty"));
        assertEquals("default-value", source.getProperty("from.defaults"));
        assertNull(source.getProperty("missing"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("scalar", "multiple", "empty", "from.defaults")),
                new LinkedHashSet<>(Arrays.asList(source.getPropertyNames())));
    }

    @Test
    public void environmentSourcesKeepOverridesAndDefaultsAheadOfCommonsConfiguration() {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.setProperty("shared", "file-value");
        configuration.setProperty("file.only", "file");
        configuration.setProperty("overlap", "file-value");
        CommonsConfigurationPropertySource file = new CommonsConfigurationPropertySource("airsonic-properties", configuration);
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(file);
        sources.addFirst(new MapPropertySource("defaults", Map.of("shared", "default", "default.only", "default", "overlap", "default-value")));
        sources.addFirst(new MapPropertySource("environment", Map.of("shared", "environment")));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);

        assertEquals("environment", resolver.getProperty("shared"));
        assertEquals("file", resolver.getProperty("file.only"));
        assertEquals("default", resolver.getProperty("default.only"));
        assertEquals("default-value", resolver.getProperty("overlap"));
    }

    @Test
    public void propertyNamesSupportMigrationAliasEnumeration() {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.setProperty("legacy.name", "legacy-value");
        CommonsConfigurationPropertySource source = new CommonsConfigurationPropertySource("airsonic-properties", configuration);
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("legacy.name", "current.name");
        Map<String, Object> migrated = new LinkedHashMap<>();

        SettingsService.migrateKeys(aliases, source::getProperty, source.getName(), migrated);

        assertEquals("legacy-value", migrated.get("current.name"));
    }
}
