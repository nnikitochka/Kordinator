package org.airsonic.player.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.primitives.Ints;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.util.stream.Collectors.toList;

/**
 * Miscellaneous general utility methods.
 */
public final class Util {

    private static final Logger LOG = LoggerFactory.getLogger(Util.class);
    private static final String URL_SENSITIVE_REPLACEMENT_STRING = "<hidden>";

    /**
     * Disallow external instantiation.
     */
    private Util() {
    }

    public static String getDefaultMusicFolder() {
        String def = isWindows() ? "c:\\music" : "/var/music";
        return System.getProperty("airsonic.defaultMusicFolder", def);
    }

    public static String getDefaultPlaylistFolder() {
        String def = isWindows() ? "c:\\playlists" : "/var/playlists";
        return System.getProperty("airsonic.defaultPlaylistFolder", def);
    }

    public static boolean isWindows() {
        return SystemUtils.IS_OS_WINDOWS;
    }

    public static <T> List<T> subList(List<T> list, long offset, long max) {
        if (list.size() == Integer.MAX_VALUE) {
            return list.stream().skip(offset).limit(max).collect(toList());
        }
        return list.subList(Math.min(list.size(), Ints.saturatedCast(offset)), Math.min(list.size(), Ints.saturatedCast(offset + max)));
    }

    public static int[] toIntArray(List<Integer> values) {
        if (values == null) {
            return new int[0];
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static String debugObject(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            LOG.warn("Cant output debug object", e);
            return "";
        }
    }

    /**
     * Return a URL with sensitive authentication parameters anonymized.
     *
     * @param url A complete URL, including an optional query string
     * @return The associated anonymized URL
     */
    public static String anonymizeUrl(String url) {
        int fragmentStart = url.indexOf('#');
        String fragment = fragmentStart < 0 ? "" : url.substring(fragmentStart);
        String urlWithoutFragment = fragmentStart < 0 ? url : url.substring(0, fragmentStart);
        int queryStart = urlWithoutFragment.indexOf('?');
        if (queryStart < 0) {
            // Normalize an incomplete IPv6 authority to an empty address literal.
            if (urlWithoutFragment.matches("(?i)^https?://\\[[^]]*$")) return urlWithoutFragment.substring(0, urlWithoutFragment.indexOf('[')) + "[]" + fragment;
            return url;
        }

        String query = urlWithoutFragment.substring(queryStart + 1);
        if (query.isEmpty()) return urlWithoutFragment.substring(0, queryStart) + fragment;
        Map<String, List<String>> queryParameters = new LinkedHashMap<>();
        for (String parameter : query.split("&")) {
            if (parameter.isEmpty()) continue;
            int equals = parameter.indexOf('=');
            String name = equals < 0 ? parameter : parameter.substring(0, equals);
            String value = equals < 0 ? "" : parameter.substring(equals + 1);
            queryParameters.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }

        // replaceQueryParam removes all values and appends one replacement at the end.
        for (String sensitiveName : List.of("p", "t", "s", "u")) {
            if (queryParameters.containsKey(sensitiveName)) {
                queryParameters.remove(sensitiveName);
                queryParameters.put(sensitiveName, List.of(URL_SENSITIVE_REPLACEMENT_STRING));
            }
        }

        StringBuilder anonymized = new StringBuilder(urlWithoutFragment.substring(0, queryStart)).append('?');
        boolean first = true;
        for (Map.Entry<String, List<String>> parameter : queryParameters.entrySet()) {
            for (String value : parameter.getValue()) {
                if (!first) anonymized.append('&');
                first = false;
                anonymized.append(parameter.getKey()).append('=').append(value);
            }
        }
        return anonymized.append(fragment).toString();
    }

    /**
     * Return true if the given object is an instance of the class name in argument.
     * If the class doesn't exist, returns false.
     */
    public static boolean isInstanceOfClassName(Object o, String className) {
        try {
            return Class.forName(className).isInstance(o);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(Object object, SerializationFeature feature) {
        try {
            return objectMapper.writer(feature).writeValueAsString(object);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, String> objectToStringMap(Object object) {
        return objectMapper.convertValue(object, new TypeReference<>() {
        });
    }

    public static <T> T stringMapToObject(Class<T> clazz, Map<String, String> data) {
        return objectMapper.convertValue(data, clazz);
    }

    private static final class BeanValidation {
        private static final Validator VALIDATOR;

        static {
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                VALIDATOR = factory.getValidator();
            }
        }
    }

    public static <T> T stringMapToValidObject(Class<T> clazz, Map<String, String> data) {
        T object = stringMapToObject(clazz, data);
        Set<ConstraintViolation<T>> validate = BeanValidation.VALIDATOR.validate(object);
        if (validate.isEmpty()) {
            return object;
        } else {
            throw new IllegalArgumentException("Created object was not valid");
        }
    }

    public static ThreadFactory getDaemonThreadfactory(String prefixName) {
        return r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            t.setName(prefixName + "-" + t.getName());
            return t;
        };
    }
}
