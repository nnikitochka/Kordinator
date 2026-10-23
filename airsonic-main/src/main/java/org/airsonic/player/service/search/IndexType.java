package org.airsonic.player.service.search;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enum that symbolizes the each lucene index entity.
 * This class is a division of what was once part of SearchService and added functionality.
 * @since legacy
 */
public enum IndexType {

    SONG(
        fieldNames(
            FieldNames.TITLE,
            FieldNames.ARTIST),
        boosts(
            entry(FieldNames.TITLE, 1.1F))),

    ALBUM(
        fieldNames(
            FieldNames.ALBUM,
            FieldNames.ARTIST),
            // FieldNames.FOLDER), // XXX 3.x -> 8.x : Remove folder from multi-field search condition
        boosts(
            entry(FieldNames.ALBUM, 1.1F))),

    ALBUM_ID3(
        fieldNames(
            FieldNames.ALBUM,
            FieldNames.ARTIST),
            // FieldNames.FOLDER_ID), // XXX 3.x -> 8.x : Remove folder from multi-field search condition
        boosts(
            entry(FieldNames.ALBUM, 1.1F))),

    ARTIST(
        fieldNames(
            FieldNames.ARTIST),
            // FieldNames.FOLDER), // XXX 3.x -> 8.x : Remove folder from multi-field search condition
        boosts()),

    ARTIST_ID3(
        fieldNames(
            FieldNames.ARTIST),
        boosts()),

    ;

    /**
     * Define the field's applied boost value when searching IndexType.
     *
     * @param entry {@link #entry(String, float)}.
     *              When specifying multiple values, enumerate entries.
     * @return Map of boost values ​​to be applied to the field
     */
    @SafeVarargs
    private static final Map<String, Float> boosts(SimpleEntry<String, Float>... entry) {
        Map<String, Float> m = new HashMap<>();
        Arrays.stream(entry).forEach(kv -> m.put(kv.getKey(), kv.getValue()));
        return Collections.unmodifiableMap(m);
    }

    /**
     * Create an entry representing the boost value for the field.
     *
     * @param k Field name defined by FieldNames
     * @param v Boost value
     */
    private static final SimpleEntry<String, Float> entry(String k, float v) {
        return new AbstractMap.SimpleEntry<>(k, v);
    }

    /**
     * Defines the field that the input value is to search for
     * when searching IndexType.
     * If you specify multiple values, list the field names.
     */
    private static final String[] fieldNames(String... names) {
        return Arrays.stream(names).toArray(String[]::new);
    }

    private final Map<String, Float> boosts;

    private final String[] fields;

    private IndexType(String[] fieldNames, Map<String, Float> boosts) {
        this.fields = fieldNames;
        this.boosts = boosts;
    }

    /**
     * Returns a map of fields and boost values.
     *
     * @return Map of fields and boost values
     * @since legacy
     * @see org.apache.lucene.search.BoostQuery
     */
    public Map<String, Float> getBoosts() {
        return boosts;
    }

    /**
     * Return some of the fields defined in the index.
     *
     * @return Fields mainly used in multi-field search
     * @since legacy
     */
    public String[] getFields() {
        return fields;
    }

}
