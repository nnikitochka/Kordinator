package org.airsonic.player.ktor

import org.airsonic.player.service.DatabaseService

data class TranscodingProfileResponse(
    val id: Int, val name: String, val sourceFormats: String, val targetFormat: String,
    val step1: String, val step2: String?, val step3: String?, val defaultActive: Boolean,
)
data class TranscodingSettingsResponse(
    val brand: String, val transcodeDirectory: String, val downsampleCommand: String,
    val hlsCommand: String, val jukeboxCommand: String, val videoImageCommand: String,
    val subtitlesExtractionCommand: String, val estimateTimePadding: Long, val estimateBytePadding: Long,
    val profiles: List<TranscodingProfileResponse>,
)
data class TranscodingProfileRequest(
    val id: Int? = null, val name: String, val sourceFormats: String, val targetFormat: String,
    val step1: String, val step2: String? = null, val step3: String? = null, val defaultActive: Boolean = false,
)
data class UpdateTranscodingSettingsRequest(
    val profiles: List<TranscodingProfileRequest> = emptyList(),
    val deleteIds: Set<Int> = emptySet(),
    val downsampleCommand: String,
    val hlsCommand: String,
    val jukeboxCommand: String,
    val videoImageCommand: String,
    val subtitlesExtractionCommand: String,
    val estimateTimePadding: Long? = null,
    val estimateBytePadding: Long? = null,
)
data class AdvancedSettingsResponse(
    val downloadLimit: Long, val uploadLimit: Long,
    val ldapUrl: String, val ldapSearchFilter: String, val ldapManagerDn: String,
    val smtpServer: String, val smtpEncryption: String, val smtpPort: String,
    val smtpUser: String, val smtpFrom: String, val recaptchaSiteKey: String,
    val brand: String, val ldapEnabled: Boolean, val ldapAutoShadowing: Boolean, val captchaEnabled: Boolean,
)
data class UpdateAdvancedSettingsRequest(
    val downloadLimit: Long, val uploadLimit: Long,
    val ldapUrl: String, val ldapSearchFilter: String, val ldapManagerDn: String,
    val smtpServer: String, val smtpEncryption: String, val smtpPort: String,
    val smtpUser: String, val smtpFrom: String, val recaptchaSiteKey: String,
    val ldapEnabled: Boolean, val ldapAutoShadowing: Boolean, val captchaEnabled: Boolean,
    val ldapManagerPassword: String? = null, val smtpPassword: String? = null, val recaptchaSecretKey: String? = null,
)
data class DlnaSettingsResponse(val enabled: Boolean, val serverName: String, val baseLanUrl: String)
data class UpdateDlnaSettingsRequest(val enabled: Boolean, val serverName: String, val baseLanUrl: String = "")
data class MusicFolderSettingsResponse(
    val interval: Int,
    val hour: Int,
    val scanning: Boolean,
    val fastCache: Boolean,
    val organizeByFolderStructure: Boolean,
    val folders: List<MusicFolderSettingsEntry>,
    val deletedFolders: List<MusicFolderSettingsEntry>,
    val folderTypes: List<String>,
    val uploadsFolder: String,
    val excludePattern: String,
    val ignoreSymbolicLinks: Boolean,
    val fullScan: Boolean,
    val clearFullScanAfterScan: Boolean,
)
data class MusicFolderSettingsEntry(
    val id: Int,
    val name: String,
    val path: String,
    val type: String,
    val enabled: Boolean,
    val exists: Boolean,
    val overlap: Boolean,
    val overlapDescription: String,
)
data class UpdateMusicFolderSettingsRequest(
    val interval: Int,
    val hour: Int,
    val fastCache: Boolean,
    val organizeByFolderStructure: Boolean,
    val uploadsFolder: String,
    val excludePattern: String,
    val ignoreSymbolicLinks: Boolean,
    val fullScan: Boolean,
    val clearFullScanAfterScan: Boolean,
    val folders: List<UpdateMusicFolderEntry>,
    val newFolder: UpdateMusicFolderEntry? = null,
)
data class UpdateMusicFolderEntry(
    val id: Int? = null,
    val name: String,
    val path: String,
    val type: String = "MEDIA",
    val enabled: Boolean = true,
    val delete: Boolean = false,
)
data class AdminPlayersResponse(
    val items: List<AdminPlayerResponse>,
    val transcoding: List<AdminTranscodingOption>,
    val schemes: List<String>,
    val technologies: List<String>,
)
data class AdminPlayerResponse(
    val id: Int,
    val name: String?,
    val description: String,
    val type: String?,
    val lastSeen: String?,
    val dynamicIp: Boolean,
    val autoControlEnabled: Boolean,
    val m3uBomEnabled: Boolean,
    val technology: String,
    val transcodeScheme: String,
    val transcodingIds: List<Int>,
)
data class AdminTranscodingOption(val id: Int, val name: String)
data class UpdatePlayerSettingsRequest(
    val name: String = "",
    val dynamicIp: Boolean,
    val autoControlEnabled: Boolean,
    val m3uBomEnabled: Boolean,
    val technology: String,
    val transcodeScheme: String,
    val transcodingIds: List<Int> = emptyList(),
)
data class DatabaseSettingsResponse(
    val configType: String,
    val driver: String,
    val url: String,
    val username: String,
    val jndiName: String,
    val mysqlVarcharMaxlength: Int,
    val backupIntervalHours: Int,
    val backupRetentionCount: Int,
    val backuppable: Boolean,
    val passwordConfigured: Boolean,
)
data class UpdateDatabaseSettingsRequest(
    val configType: String,
    val driver: String = "",
    val url: String = "",
    val username: String = "",
    val jndiName: String = "",
    val password: String? = null,
    val clearPassword: Boolean = false,
    val mysqlVarcharMaxlength: Int = 0,
    val backupIntervalHours: Int,
    val backupRetentionCount: Int,
)
data class DatabaseOperationsResponse(
    val backup: DatabaseService.OperationStatus?,
    val export: DatabaseService.OperationStatus?,
    val import: DatabaseService.OperationStatus?,
)
