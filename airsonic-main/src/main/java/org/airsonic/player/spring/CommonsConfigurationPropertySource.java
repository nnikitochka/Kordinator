package org.airsonic.player.spring;

import org.apache.commons.configuration2.Configuration;
import org.springframework.core.env.EnumerablePropertySource;

import java.util.ArrayList;
import java.util.List;

/** Adapts Apache Commons Configuration to Spring's property source contract. */
final class CommonsConfigurationPropertySource extends EnumerablePropertySource<Configuration> {

    CommonsConfigurationPropertySource(String name, Configuration source) {
        super(name, source);
    }

    @Override
    public Object getProperty(String name) {
        if (source.getProperty(name) == null) {
            return null;
        }
        String[] values = source.getStringArray(name);
        if (values == null || values.length == 0) {
            return "";
        }
        if (values.length == 1) {
            return values[0];
        }
        return values;
    }

    @Override
    public String[] getPropertyNames() {
        List<String> keys = new ArrayList<>();
        source.getKeys().forEachRemaining(keys::add);
        return keys.toArray(new String[0]);
    }
}
