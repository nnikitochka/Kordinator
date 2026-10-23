package org.airsonic.player.domain;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Represents a list of genres.
 */
public class Genres {

    private final Map<String, Genre> genres = new ConcurrentHashMap<>();

    // genre names can be ([genre] --> [split to])
    // - abc --> ['abc']
    // - abc; --> ['abc', '']
    // - abc;xyz --> ['abc', 'xyz']
    // - abc; xyz --> ['abc', ' xyz']

    public void incrementAlbumCount(String genreName, String separators) {
        String[] splitGenres = StringUtils.split(genreName, separators);
        if (splitGenres.length > 1) { // otherwise it's the same genre as the original
            Stream.of(splitGenres)
                    .map(StringUtils::trim)
                    .filter(StringUtils::isNotBlank)
                    .forEach(s -> genres.computeIfAbsent(s, Genre::new).incrementAlbumCount());
        }
        genres.computeIfAbsent(genreName, Genre::new).incrementAlbumCount();
    }

    public void incrementSongCount(String genreName, String separators) {
        String[] splitGenres = StringUtils.split(genreName, separators);
        if (splitGenres.length > 1) { // otherwise it's the same genre as the original
            Stream.of(splitGenres)
                    .map(StringUtils::trim)
                    .filter(StringUtils::isNotBlank)
                    .forEach(s -> genres.computeIfAbsent(s, Genre::new).incrementSongCount());
        }
        genres.computeIfAbsent(genreName, Genre::new).incrementSongCount();
    }

    public List<Genre> getGenres() {
        return new ArrayList<>(genres.values());
    }
}
