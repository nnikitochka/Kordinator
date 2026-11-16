package org.airsonic.player.dao;

import org.airsonic.player.domain.Avatar;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Provides database services for avatars.
 */
@Repository
public class AvatarDao extends AbstractDao {

    private static final String INSERT_COLUMNS = "name, created_date, mime_type, width, height, path";
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;
    private final AvatarRowMapper rowMapper = new AvatarRowMapper();

    /**
     * Returns all system avatars.
     *
     * @return All system avatars.
     */
    public List<Avatar> getAllSystemAvatars() {
        String sql = "select " + QUERY_COLUMNS + " from system_avatar";
        return query(sql, rowMapper);
    }

    /**
     * Returns the system avatar with the given ID.
     *
     * @param id The system avatar ID.
     * @return The avatar or <code>null</code> if not found.
     */
    public Avatar getSystemAvatar(int id) {
        String sql = "select " + QUERY_COLUMNS + " from system_avatar where id=" + id;
        return queryOne(sql, rowMapper);
    }

    /**
     * Returns the custom avatar for the given user.
     *
     * @param username The username.
     * @return The avatar or <code>null</code> if not found.
     */
    public Avatar getCustomAvatar(String username) {
        String sql = "select " + QUERY_COLUMNS + " from custom_avatar where username=?";
        return queryOne(sql, rowMapper, username);
    }

    /**
     * Sets the custom avatar for the given user.
     *
     * @param avatar   The avatar, or <code>null</code> to remove the avatar.
     * @param username The username.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setCustomAvatar(Avatar avatar, String username) {
        String sql = "delete from custom_avatar where username=?";
        update(sql, username);

        if (avatar != null) {
            update("insert into custom_avatar(" + INSERT_COLUMNS
                   + ", username) values(" + questionMarks(INSERT_COLUMNS) + ", ?)",
                   avatar.getName(), avatar.getCreatedDate(), avatar.getMimeType(),
                   avatar.getWidth(), avatar.getHeight(), StringUtils.replace(avatar.getPath().toString(), SettingsService.getAirsonicHome().toString(), "$[AIRSONIC_HOME]"), username);
        }
    }

    private static class AvatarRowMapper implements RowMapper<Avatar> {
        public Avatar mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Avatar(rs.getInt(1), rs.getString(2), Optional.ofNullable(rs.getTimestamp(3)).map(Timestamp::toInstant).orElse(null), rs.getString(4),
                              rs.getInt(5), rs.getInt(6), StringUtils.replace(rs.getString(7), "$[AIRSONIC_HOME]", SettingsService.getAirsonicHome().toString()));
        }
    }

}
