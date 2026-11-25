package org.airsonic.player.service

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import javax.cache.Cache
import javax.cache.CacheManager
import org.airsonic.player.cache.CacheHelper
import org.airsonic.player.dao.UserDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.domain.UserCredential.App
import org.airsonic.player.security.AuthorizationPolicy
import org.airsonic.player.security.CallerContext
import org.airsonic.player.security.CredentialEncoderRegistry
import org.airsonic.player.security.UserIdentity
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory

class SecurityService(
    userDao: UserDao,
    settingsService: SettingsService,
    private val mediaFolderService: MediaFolderService,
    cacheManager: CacheManager?,
    credentialEncoders: CredentialEncoderRegistry,
) {
    private var userDao = userDao
    private var settingsService = settingsService
    private val userCache: Cache<String, User>? = cacheManager?.getCache("userCache", String::class.java, User::class.java)
    private val authorizationPolicy = AuthorizationPolicy()
    private val credentialOperations = SecurityCredentialOperations(
        userDao = { this.userDao },
        settingsService = { this.settingsService },
        credentialEncoders = credentialEncoders,
    )

    fun findUserIdentity(username: String?, caseSensitive: Boolean): UserIdentity? {
        val user = getUserByName(username, caseSensitive) ?: return null
        return UserIdentity(user.username, getCredentials(user.username, App.AIRSONIC), user.roles)
    }

    fun updateCredentials(
        oldCredentials: UserCredential,
        newCredentials: UserCredential,
        comment: String?,
        reencodePlaintextNewCredentials: Boolean,
    ): Boolean = credentialOperations.updateCredentials(
        oldCredentials,
        newCredentials,
        comment,
        reencodePlaintextNewCredentials,
    )

    fun createCredential(credentials: UserCredential): Boolean = credentialOperations.createCredential(credentials)

    fun deleteCredential(credentials: UserCredential): Boolean = credentialOperations.deleteCredential(credentials)

    fun getCredentials(username: String, vararg apps: App): List<UserCredential> =
        credentialOperations.getCredentials(username, apps)

    fun getDecodableCredsForApps(username: String, vararg apps: App): Map<App, UserCredential> =
        credentialOperations.getDecodableCredentialsForApps(username, apps)

    fun checkDefaultAdminCredsPresent(): Boolean = credentialOperations.checkDefaultAdminCredentialsPresent()

    fun checkOpenCredsPresent(): Boolean = credentialOperations.checkOpenCredentialsPresent()

    fun checkLegacyCredsPresent(): Boolean = credentialOperations.checkLegacyCredentialsPresent()

    fun migrateLegacyCredsToNonLegacy(useDecodableOnly: Boolean): Boolean =
        credentialOperations.migrateLegacyCredentialsToNonLegacy(useDecodableOnly)

    fun getPreferredPasswordEncoder(nonDecodableAllowed: Boolean): String =
        credentialOperations.preferredPasswordEncoder(nonDecodableAllowed)

    fun decodeCredentials(reversibleCredentials: UserCredential): String? =
        credentialOperations.decodeCredentials(reversibleCredentials)

    fun getUserByName(username: String?): User? = getUserByName(username, true)

    fun getUserByName(username: String?, caseSensitive: Boolean): User? {
        if (!caseSensitive) return userDao.getUserByName(username, false)
        return CacheHelper.getOrCompute(userCache, username) { name -> userDao.getUserByName(name, true) }
    }

    fun getUserByEmail(email: String): User? = userDao.getUserByEmail(email)

    fun getAllUsers(): List<User> = userDao.getAllUsers()

    fun isAdmin(username: String?): Boolean {
        if (User.USERNAME_ADMIN == username) return true
        return getUserByName(username)?.isAdminRole == true
    }

    fun isAdmin(caller: CallerContext?): Boolean = authorizationPolicy.isAdmin(caller)

    fun createCallerContext(username: String?, authenticated: Boolean): CallerContext {
        val user = getUserByName(username) ?: return CallerContext.anonymous()
        val folderIds = mediaFolderService.getMusicFoldersForUser(user.username)
            .map { requireNotNull(it.id) }
            .toSet()
        return CallerContext(user.username, user.roles, folderIds, authenticated)
    }

    fun createUser(user: User, credential: String, comment: String?) {
        val encoder = getPreferredPasswordEncoder(true)
        val userCredential = UserCredential(
            user.username,
            user.username,
            credentialOperations.encodeCredential(encoder, credential),
            encoder,
            App.AIRSONIC,
            comment,
        )
        createUser(user, userCredential)
    }

    fun createUser(user: User, credential: UserCredential) {
        userDao.createUser(user, credential)
        mediaFolderService.setMusicFoldersForUser(
            user.username,
            mediaFolderService.getAllMusicFolders().map { requireNotNull(it.id) },
        )
        LOG.info("Created user {}", user.username)
    }

    fun deleteUser(username: String?) {
        if (userCache != null && username != null) userCache.remove(username)
        userDao.deleteUser(requireNotNull(username))
        LOG.info("Deleted user {}", username)
    }

    fun updateUser(user: User?) {
        if (userCache != null) user?.username?.let(userCache::remove)
        userDao.updateUser(requireNotNull(user))
    }

    fun updateUserByteCounts(user: User?, bytesStreamedDelta: Long, bytesDownloadedDelta: Long, bytesUploadedDelta: Long) {
        if (user == null) return
        userCache?.remove(user.username)
        userDao.updateUserByteCounts(user.username, bytesStreamedDelta, bytesDownloadedDelta, bytesUploadedDelta)

        val updated = requireNotNull(userDao.getUserByName(user.username, true))
        user.bytesStreamed = updated.bytesStreamed
        user.bytesDownloaded = updated.bytesDownloaded
        user.bytesUploaded = updated.bytesUploaded
    }

    fun isReadAllowed(file: MediaFile?, checkExistence: Boolean): Boolean {
        if (file == null) return false
        val folder = requireNotNull(mediaFolderService.getMusicFolderById(file.folderId!!))
        return folder.isEnabled() && (!checkExistence || Files.exists(file.getFullPath(folder.path)))
    }

    fun isWriteAllowed(relativePath: Path, folder: MusicFolder): Boolean =
        folder.isEnabled() && requireNotNull(relativePath.fileName).toString().startsWith("cover.")

    @Throws(IOException::class)
    fun checkUploadAllowed(file: Path, checkFileExists: Boolean) {
        if (getMusicFolderForFile(file) == null) {
            throw AccessDeniedException(file.toString(), null, "Specified location is not in writable music folder")
        }
        if (checkFileExists && Files.exists(file)) {
            throw FileAlreadyExistsException(file.toString(), null, "File already exists")
        }
    }

    private fun getMusicFolderForFile(file: Path): MusicFolder? = getMusicFolderForFile(file, false, true)

    fun getMusicFolderForFile(file: Path, includeDisabled: Boolean, includeNonExisting: Boolean): MusicFolder? =
        mediaFolderService.getAllMusicFolders(includeDisabled, includeNonExisting).stream()
            .filter { folder -> isFileInFolder(file, folder.path) }
            .max(Comparator.comparingInt<MusicFolder> { folder -> folder.path.nameCount })
            .orElse(null)

    fun isFolderAccessAllowed(file: MediaFile, username: String): Boolean =
        mediaFolderService.getMusicFoldersForUser(username).parallelStream()
            .anyMatch { folder -> requireNotNull(folder.id) == file.folderId }

    fun isFolderAccessAllowed(file: MediaFile?, caller: CallerContext?): Boolean =
        authorizationPolicy.hasFolderAccess(caller, file)

    fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
    }

    fun setUserDao(userDao: UserDao) {
        this.userDao = userDao
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SecurityService::class.java)

        @JvmStatic
        fun isFileInFolder(file: Path, folder: Path): Boolean =
            Paths.get(FilenameUtils.separatorsToUnix(file.toString())).normalize()
                .startsWith(Paths.get(FilenameUtils.separatorsToUnix(folder.toString())).normalize())
    }
}
