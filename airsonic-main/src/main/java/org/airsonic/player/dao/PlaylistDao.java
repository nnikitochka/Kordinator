package org.airsonic.player.dao;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.util.LambdaUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides database services for playlists.
 */
public class PlaylistDao extends AbstractDao {
    public PlaylistDao(JdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    private static final String INSERT_COLUMNS = "username, is_public, name, comment, file_count, duration, " +
                                                "created, changed, imported_from";
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;
    private final PlaylistMapper rowMapper = new PlaylistMapper();
    private final static Comparator<Playlist> sorter = Comparator.comparing(Playlist::getName);

    public void register() throws Exception {
        registerInserts("playlist", "id", Arrays.asList(INSERT_COLUMNS.split(", ")), Playlist.class);
    }

    public List<Playlist> getReadablePlaylistsForUser(String username) {

        List<Playlist> result1 = getWritablePlaylistsForUser(username);
        List<Playlist> result2 = query("select " + QUERY_COLUMNS + " from playlist where is_public", rowMapper);
        List<Playlist> result3 = query("select " + prefix(QUERY_COLUMNS, "playlist") + " from playlist, playlist_user where " +
                                       "playlist.id = playlist_user.playlist_id and " +
                                       "playlist.username != ? and " +
                                       "playlist_user.username = ?", rowMapper, username, username);

        // Remove duplicates.
        return Stream.of(result1, result2, result3)
                .flatMap(Collection::parallelStream)
                .filter(LambdaUtils.distinctByKey(Playlist::getId))
                .sorted(sorter)
                .collect(Collectors.toList());
    }

    public List<Playlist> getWritablePlaylistsForUser(String username) {
        return query("select " + QUERY_COLUMNS + " from playlist where username=?", rowMapper, username)
                .stream()
                .sorted(sorter)
                .collect(Collectors.toList());
    }

    public Playlist getPlaylist(int id) {
        return queryOne("select " + QUERY_COLUMNS + " from playlist where id=?", rowMapper, id);
    }

    public List<Playlist> getAllPlaylists() {
        return query("select " + QUERY_COLUMNS + " from playlist", rowMapper)
                .stream()
                .sorted(sorter)
                .collect(Collectors.toList());
    }

    public void createPlaylist(Playlist playlist) {
        Integer id = insert("playlist", playlist);
        playlist.setId(id);
    }

    @Transactional
    public void setFilesInPlaylist(int id, List<MediaFile> files) {
        update("delete from playlist_file where playlist_id=?", id);
        batchedUpdate("insert into playlist_file (playlist_id, media_file_id) values (?, ?)",
                files.stream().map(x -> new Object[] { id, x.getId() }).collect(Collectors.toList()));
    }

    public Pair<Integer, Double> getPlaylistFileStats(int id) {
        return queryOne("select count(*), sum(duration) from media_file m, playlist_file p where p.media_file_id = m.id and p.playlist_id=?", (rs, i) -> Pair.of(rs.getInt(1), rs.getDouble(2)), id);
    }

    public List<String> getPlaylistUsers(int playlistId) {
        return queryForStrings("select username from playlist_user where playlist_id=?", playlistId);
    }

    public void addPlaylistUser(int playlistId, String username) {
        if (!getPlaylistUsers(playlistId).contains(username)) {
            update("insert into playlist_user(playlist_id,username) values (?,?)", playlistId, username);
        }
    }

    public void deletePlaylistUser(int playlistId, String username) {
        update("delete from playlist_user where playlist_id=? and username=?", playlistId, username);
    }

    public void deletePlaylist(int id) {
        update("delete from playlist where id=?", id);
    }

    public void updatePlaylist(Playlist playlist) {
        update("update playlist set username=?, is_public=?, name=?, comment=?, changed=?, imported_from=?, file_count=?, duration=? where id=?",
                playlist.getUsername(), playlist.getShared(), playlist.getName(), playlist.getComment(),
                Instant.now(), playlist.getImportedFrom(), playlist.getFileCount(), playlist.getDuration(),
                playlist.getId());
    }

    private static class PlaylistMapper implements RowMapper<Playlist> {
        @Override
        public Playlist mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Playlist(
                    rs.getInt(1),
                    rs.getString(2),
                    rs.getBoolean(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getInt(6),
                    rs.getDouble(7),
                    Optional.ofNullable(rs.getTimestamp(8)).map(Timestamp::toInstant).orElse(null),
                    Optional.ofNullable(rs.getTimestamp(9)).map(Timestamp::toInstant).orElse(null),
                    rs.getString(10)
            );
        }
    }
}
