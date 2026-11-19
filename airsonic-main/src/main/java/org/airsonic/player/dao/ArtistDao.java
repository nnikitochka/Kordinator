package org.airsonic.player.dao;

import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MusicFolder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * Provides database services for artists.
 */
public class ArtistDao extends AbstractDao {
    public ArtistDao(JdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    private static final String INSERT_COLUMNS = "name, album_count, last_scanned, present, folder_id";
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;

    private final ArtistMapper rowMapper = new ArtistMapper();

    /**
     * Returns the artist with the given name.
     *
     * @param artistName The artist name.
     * @return The artist or null.
     */
    public Artist getArtist(String artistName) {
        return queryOne("select " + QUERY_COLUMNS + " from artist where name=?", rowMapper, artistName);
    }

    /**
     * Returns the artist with the given name.
     *
     * @param artistName   The artist name.
     * @param musicFolders Only return artists that have at least one album in these folders.
     * @return The artist or null.
     */
    public Artist getArtist(final String artistName, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return null;
        }
        Map<String, Object> args = new HashMap<>();
        args.put("name", artistName);
        args.put("folders", MusicFolder.toIdList(musicFolders));

        return namedQueryOne("select " + QUERY_COLUMNS + " from artist where name = :name and folder_id in (:folders)",
                             rowMapper, args);
    }

    /**
     * Returns the artist with the given ID.
     *
     * @param id The artist ID.
     * @return The artist or null.
     */
    public Artist getArtist(int id) {
        return queryOne("select " + QUERY_COLUMNS + " from artist where id=?", rowMapper, id);
    }

    /**
     * Creates or updates an artist.
     *
     * @param artist The artist to create/update.
     */
    public void createOrUpdateArtist(Artist artist) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED, status -> {
        String sql = "update artist set " +
                     "album_count=?," +
                     "last_scanned=?," +
                     "present=?," +
                     "folder_id=? " +
                     "where name=?";

        int n = update(sql, artist.getAlbumCount(), artist.getLastScanned(), artist.isPresent(), artist.getFolderId(), artist.getName());

        if (n == 0) {
            update("insert into artist (" + INSERT_COLUMNS + ") values (" + questionMarks(INSERT_COLUMNS) + ")",
                   artist.getName(), artist.getAlbumCount(), artist.getLastScanned(), artist.isPresent(), artist.getFolderId());
        }

        int id = queryForInt("select id from artist where name=?", null, artist.getName());
        artist.setId(id);
        return null;
        });
    }

    /**
     * Returns artists in alphabetical order.
     *
     * @param offset       Number of artists to skip.
     * @param count        Maximum number of artists to return.
     * @param musicFolders Only return artists that have at least one album in these folders.
     * @return Artists in alphabetical order.
     */
    public List<Artist> getAlphabetialArtists(final int offset, final int count, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = new HashMap<>();
        args.put("folders", MusicFolder.toIdList(musicFolders));
        args.put("count", count);
        args.put("offset", offset);

        return namedQuery("select " + QUERY_COLUMNS + " from artist where present and folder_id in (:folders) " +
                          "order by name, id limit :count offset :offset", rowMapper, args);
    }

    /**
     * Returns the most recently starred artists.
     *
     * @param offset       Number of artists to skip.
     * @param count        Maximum number of artists to return.
     * @param username     Returns artists starred by this user.
     * @param musicFolders Only return artists that have at least one album in these folders.
     * @return The most recently starred artists for this user.
     */
    public List<Artist> getStarredArtists(final int offset, final int count, final String username,
                                          final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = new HashMap<>();
        args.put("folders", MusicFolder.toIdList(musicFolders));
        args.put("username", username);
        args.put("count", count);
        args.put("offset", offset);

        return namedQuery("select " + prefix(QUERY_COLUMNS, "artist") + " from starred_artist, artist " +
                          "where artist.id = starred_artist.artist_id and " +
                          "artist.present and starred_artist.username = :username and " +
                          "artist.folder_id in (:folders) " +
                          "order by starred_artist.created desc, starred_artist.id limit :count offset :offset",
                          rowMapper, args);
    }

    public void markPresent(String artistName, Instant lastScanned) {
        update("update artist set present=?, last_scanned = ? where name=?", true, lastScanned, artistName);
    }

    public void markNonPresent(Instant lastScanned) {
        update("update artist set present=false where last_scanned < ? and present", lastScanned);
    }

    public List<Integer> getExpungeCandidates() {
        return queryForInts("select id from artist where not present");
    }

    public void expunge() {
        update("delete from artist where not present");
    }

    public void starArtist(int artistId, String username) {
        unstarArtist(artistId, username);
        update("insert into starred_artist(artist_id, username, created) values (?,?,?)", artistId, username, Instant.now());
    }

    public void unstarArtist(int artistId, String username) {
        update("delete from starred_artist where artist_id=? and username=?", artistId, username);
    }

    public Instant getArtistStarredDate(int artistId, String username) {
        return queryForInstant("select created from starred_artist where artist_id=? and username=?", null, artistId, username);
    }

    private static class ArtistMapper implements RowMapper<Artist> {
        @Override
        public Artist mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Artist(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getInt("album_count"),
                    Optional.ofNullable(rs.getTimestamp("last_scanned")).map(x -> x.toInstant()).orElse(null),
                    rs.getBoolean("present"),
                    rs.getInt("folder_id"));
        }
    }
}
