package org.airsonic.player.service;

import org.airsonic.player.dao.UserDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.security.GlobalSecurityConfig;
import org.airsonic.player.security.CallerContext;
import org.airsonic.player.security.AuthorizationPolicy;
import org.airsonic.player.security.PasswordDecoder;
import org.airsonic.player.security.UserIdentity;
import org.airsonic.player.cache.CacheHelper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;

import jakarta.servlet.http.HttpServletRequest;
import javax.cache.Cache;
import javax.cache.CacheManager;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides security-related services for authentication and authorization.
 *
 * @author Sindre Mehus
 */
public class SecurityService {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityService.class);

    private UserDao userDao;
    private SettingsService settingsService;
    private final MediaFolderService mediaFolderService;
    private final Cache<String, User> userCache;
    private final AuthorizationPolicy authorizationPolicy = new AuthorizationPolicy();

    public SecurityService(UserDao userDao, SettingsService settingsService, MediaFolderService mediaFolderService) {
        this(userDao, settingsService, mediaFolderService, null);
    }

    public SecurityService(UserDao userDao, SettingsService settingsService, MediaFolderService mediaFolderService,
                           CacheManager cacheManager) {
        this.userDao = userDao;
        this.settingsService = settingsService;
        this.mediaFolderService = mediaFolderService;
        this.userCache = cacheManager != null ? cacheManager.getCache("userCache", String.class, User.class) : null;
    }

    /** Loads identity data without exposing web-framework types. */
    public UserIdentity findUserIdentity(String username, boolean caseSensitive) {
        User user = getUserByName(username, caseSensitive);
        if (user == null) {
            return null;
        }

        return new UserIdentity(user.getUsername(), getCredentials(user.getUsername(), App.AIRSONIC), user.getRoles());
    }

    public boolean updateCredentials(UserCredential oldCreds, UserCredential newCreds, String comment,
            boolean reencodePlaintextNewCreds) {
        if (!StringUtils.equals(newCreds.getEncoder(), oldCreds.getEncoder()) || reencodePlaintextNewCreds) {
            if (reencodePlaintextNewCreds) {
                newCreds.setCredential(GlobalSecurityConfig.ENCODERS.get(newCreds.getEncoder()).encode(newCreds.getCredential()));
            } else if (GlobalSecurityConfig.DECODABLE_ENCODERS.contains(oldCreds.getEncoder())) {
                try {
                    // decode using original creds decoder
                    PasswordDecoder decoder = (PasswordDecoder) GlobalSecurityConfig.ENCODERS.get(oldCreds.getEncoder());
                    newCreds.setCredential(decoder.decode(oldCreds.getCredential()));
                    // reencode
                    newCreds.setCredential(GlobalSecurityConfig.ENCODERS.get(newCreds.getEncoder()).encode(newCreds.getCredential()));
                } catch (Exception e) {
                    LOG.warn("Could not update credentials for user {}", oldCreds.getUsername(), e);
                    // Do not try and save it
                    return false;
                }
            }
        }

        if (!newCreds.equals(oldCreds)) {
            newCreds.setComment(comment);
            newCreds.setUpdated(Instant.now());

            return userDao.updateCredential(oldCreds, newCreds);
        }

        return true;
    }

    public boolean createCredential(UserCredential newCreds) {
        newCreds.setCredential(GlobalSecurityConfig.ENCODERS.get(newCreds.getEncoder()).encode(newCreds.getCredential()));
        return userDao.createCredential(newCreds);
    }

    // ensure we can't delete all airsonic creds
    private final Predicate<UserCredential> retainOneAirsonicCred = c ->
            App.AIRSONIC != c.getApp()
            || !userDao.getCredentials(c.getUsername(), c.getApp()).isEmpty();

    public boolean deleteCredential(UserCredential creds) {
        try {
            return userDao.deleteCredential(creds, retainOneAirsonicCred);
        } catch (Exception e) {
            LOG.info("Can't delete a credential", e);
            return false;
        }
    }

    public List<UserCredential> getCredentials(String username, App... apps) {
        return userDao.getCredentials(username, apps);
    }

    public Map<App, UserCredential> getDecodableCredsForApps(String username, App... apps) {
        return getCredentials(username, apps).parallelStream()
                .filter(c -> GlobalSecurityConfig.DECODABLE_ENCODERS.contains(c.getEncoder()))
                .filter(c -> c.getExpiration() == null || c.getExpiration().isAfter(Instant.now()))
                .collect(Collectors.groupingByConcurrent(
                        UserCredential::getApp,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(UserCredential::getUpdated)), o -> o.orElse(null))));
    }

    public boolean checkDefaultAdminCredsPresent() {
        return userDao.getCredentials(User.USERNAME_ADMIN, App.AIRSONIC).parallelStream()
                .anyMatch(c -> GlobalSecurityConfig.ENCODERS.get(c.getEncoder()).matches(User.USERNAME_ADMIN, c.getCredential()));
    }

    public boolean checkOpenCredsPresent() {
        return GlobalSecurityConfig.OPENTEXT_ENCODERS.parallelStream()
                .mapToInt(userDao::getCredentialCountByEncoder)
                .anyMatch(i -> i > 0);
    }

    public boolean checkLegacyCredsPresent() {
        return userDao.getCredentialCountByEncoder("legacy%") != 0;
    }

    public boolean migrateLegacyCredsToNonLegacy(boolean useDecodableOnly) {
        String decodableEncoder = settingsService.getDecodablePasswordEncoder();
        String nonDecodableEncoder = useDecodableOnly ? decodableEncoder
                : settingsService.getNonDecodablePasswordEncoder();

        List<UserCredential> failures = new ArrayList<>();

        userDao.getCredentialsByEncoder("legacy%").forEach(c -> {
            UserCredential newCreds = new UserCredential(c);
            if (App.AIRSONIC == c.getApp()) {
                newCreds.setEncoder(nonDecodableEncoder);
            } else {
                newCreds.setEncoder(decodableEncoder);
            }
            if (!updateCredentials(c, newCreds, c.getComment() + " | Migrated to nonlegacy by admin", false)) {
                LOG.warn("System failed to migrate creds created on {} for user {}", c.getCreated(), c.getUsername());
                failures.add(c);
            }
        });

        return failures.isEmpty();
    }

    public String getPreferredPasswordEncoder(boolean nonDecodableAllowed) {
        if (!nonDecodableAllowed || !settingsService.getPreferNonDecodablePasswords()) {
            return settingsService.getDecodablePasswordEncoder();
        } else {
            return settingsService.getNonDecodablePasswordEncoder();
        }
    }

    public static List<GrantedAuthority> getGrantedAuthorities(User user) {
        return Stream.concat(
                Stream.of(
                        new SimpleGrantedAuthority("IS_AUTHENTICATED_ANONYMOUSLY"),
                        new SimpleGrantedAuthority("ROLE_USER")),
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())))
                .collect(Collectors.toList());
    }

    public static String decodeCredentials(UserCredential reversibleCred) {
        PasswordDecoder decoder = (PasswordDecoder) GlobalSecurityConfig.ENCODERS.get(reversibleCred.getEncoder());
        try {
            return decoder.decode(reversibleCred.getCredential());
        } catch (Exception e) {
            LOG.warn("Could not decode credentials for user {}, app {}", reversibleCred.getUsername(), reversibleCred.getApp(), e);
            return null;
        }
    }

    /**
     * Returns the currently logged-in user for the given HTTP request.
     *
     * @param request The HTTP request.
     * @return The logged-in user, or <code>null</code>.
     */
    public User getCurrentUser(HttpServletRequest request) {
        String username = getCurrentUsername(request);
        return username == null ? null : getUserByName(username);
    }

    /**
     * Returns the name of the currently logged-in user.
     *
     * @param request The HTTP request.
     * @return The name of the logged-in user, or <code>null</code>.
     */
    public String getCurrentUsername(HttpServletRequest request) {
        if (request.getUserPrincipal() != null && request.getUserPrincipal().getName() != null) {
            return request.getUserPrincipal().getName();
        }
        return new SecurityContextHolderAwareRequestWrapper(request, null).getRemoteUser();
    }

    /**
     * Returns the user with the given username.
     *
     * @param username The username used when logging in.
     * @return The user, or <code>null</code> if not found.
     */
    public User getUserByName(String username) {
        return getUserByName(username, true);
    }

    /**
     * Returns the user with the given username<br>
     * Cache note: Will only cache if case-sensitive. Otherwise, cache eviction is difficult
     *
     * @param username      The username to look for
     * @param caseSensitive If false, will do a case insensitive search
     * @return The corresponding User
     */
    public User getUserByName(String username, boolean caseSensitive) {
        if (!caseSensitive) {
            return userDao.getUserByName(username, false);
        }
        return CacheHelper.getOrCompute(userCache, username, u -> userDao.getUserByName(u, true));
    }

    /**
     * Returns the user with the given email address.
     *
     * @param email The email address.
     * @return The user, or <code>null</code> if not found.
     */
    public User getUserByEmail(String email) {
        return userDao.getUserByEmail(email);
    }

    /**
     * Returns all users.
     *
     * @return Possibly empty array of all users.
     */
    public List<User> getAllUsers() {
        return userDao.getAllUsers();
    }

    /**
     * Returns whether the given user has administrative rights.
     */
    public boolean isAdmin(String username) {
        if (User.USERNAME_ADMIN.equals(username)) {
            return true;
        }
        User user = getUserByName(username);
        return user != null && user.isAdminRole();
    }

    public boolean isAdmin(CallerContext caller) {
        return authorizationPolicy.isAdmin(caller);
    }

    public CallerContext createCallerContext(String username, boolean authenticated) {
        User user = getUserByName(username);
        if (user == null) {
            return CallerContext.anonymous();
        }
        return new CallerContext(
                user.getUsername(),
                user.getRoles(),
                mediaFolderService.getMusicFoldersForUser(user.getUsername()).stream()
                        .map(MusicFolder::getId)
                        .collect(Collectors.toSet()),
                authenticated);
    }

    /**
     * Creates a new user.
     *
     * @param user       The user to create.
     * @param credential The raw credential (will be encoded)
     */
    public void createUser(User user, String credential, String comment) {
        String defaultEncoder = getPreferredPasswordEncoder(true);
        UserCredential uc = new UserCredential(
                user.getUsername(),
                user.getUsername(),
                GlobalSecurityConfig.ENCODERS.get(defaultEncoder).encode(credential),
                defaultEncoder,
                App.AIRSONIC,
                comment);
        createUser(user, uc);
    }

    public void createUser(User user, UserCredential credential) {
        userDao.createUser(user, credential);
        mediaFolderService.setMusicFoldersForUser(user.getUsername(), MusicFolder.toIdList(mediaFolderService.getAllMusicFolders()));
        LOG.info("Created user {}", user.getUsername());
    }

    /**
     * Deletes the user with the given username.
     *
     * @param username The username.
     */
    public void deleteUser(String username) {
        if (userCache != null && username != null) {
            userCache.remove(username);
        }
        userDao.deleteUser(username);
        LOG.info("Deleted user {}", username);
    }

    /**
     * Updates the given user.
     *
     * @param user The user to update.
     */
    public void updateUser(User user) {
        if (userCache != null && user != null && user.getUsername() != null) {
            userCache.remove(user.getUsername());
        }
        userDao.updateUser(user);
    }

    /**
     * Updates the byte counts for given user.
     *
     * @param user                 The user to update, may be <code>null</code>.
     * @param bytesStreamedDelta   Increment bytes streamed count with this value.
     * @param bytesDownloadedDelta Increment bytes downloaded count with this value.
     * @param bytesUploadedDelta   Increment bytes uploaded count with this value.
     */
    public void updateUserByteCounts(User user, long bytesStreamedDelta, long bytesDownloadedDelta, long bytesUploadedDelta) {
        if (user == null) {
            return;
        }
        if (userCache != null && user.getUsername() != null) {
            userCache.remove(user.getUsername());
        }

        userDao.updateUserByteCounts(user.getUsername(), bytesStreamedDelta, bytesDownloadedDelta, bytesUploadedDelta);

        User updated = userDao.getUserByName(user.getUsername(), true);
        user.setBytesStreamed(updated.getBytesStreamed());
        user.setBytesDownloaded(updated.getBytesDownloaded());
        user.setBytesUploaded(updated.getBytesUploaded());
    }

    public boolean isReadAllowed(MediaFile file, boolean checkExistence) {
        if (file == null) {
            return false;
        }
        MusicFolder folder = mediaFolderService.getMusicFolderById(file.getFolderId());
        return folder.isEnabled() && (!checkExistence || Files.exists(file.getFullPath(folder.getPath())));
    }

    public boolean isWriteAllowed(Path relativePath, MusicFolder folder) {
        // Only allowed to write cover art.
        return folder.isEnabled() && relativePath.getFileName().toString().startsWith("cover.");
    }

    /**
     * Returns whether the given file may be uploaded.
     */
    public void checkUploadAllowed(Path file, boolean checkFileExists) throws IOException {
        if (getMusicFolderForFile(file) == null) {
            throw new AccessDeniedException(file.toString(), null, "Specified location is not in writable music folder");
        }

        if (checkFileExists && Files.exists(file)) {
            throw new FileAlreadyExistsException(file.toString(), null, "File already exists");
        }
    }

    private MusicFolder getMusicFolderForFile(Path file) {
        return getMusicFolderForFile(file, false, true);
    }

    public MusicFolder getMusicFolderForFile(Path file, boolean includeDisabled, boolean includeNonExisting) {
        return mediaFolderService.getAllMusicFolders(includeDisabled, includeNonExisting).stream()
                .filter(folder -> isFileInFolder(file, folder.getPath()))
                .max(Comparator.comparing(folder -> folder.getPath().getNameCount())).orElse(null);
    }

    public boolean isFolderAccessAllowed(MediaFile file, String username) {
        return mediaFolderService.getMusicFoldersForUser(username).parallelStream()
                .anyMatch(musicFolder -> musicFolder.getId().equals(file.getFolderId()));
    }

    public boolean isFolderAccessAllowed(MediaFile file, CallerContext caller) {
        return authorizationPolicy.hasFolderAccess(caller, file);
    }

    public static boolean isFileInFolder(Path file, Path folder) {
        // not using this to account for / and \\ issues in linux
        // return file.normalize().startsWith(folder.normalize());
        return Paths.get(FilenameUtils.separatorsToUnix(file.toString())).normalize()
                .startsWith(Paths.get(FilenameUtils.separatorsToUnix(folder.toString())).normalize());
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

}
