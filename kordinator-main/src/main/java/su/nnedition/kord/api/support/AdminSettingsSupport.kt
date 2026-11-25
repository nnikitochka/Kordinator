package su.nnedition.kord.api.support

import su.nnedition.kord.api.module.admin.settings.UpdateMusicFolderSettingsRequest
import su.nnedition.kord.api.module.admin.settings.UpdateMusicFolderEntry

import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import su.nnedition.kord.command.GeneralSettingsCommand
import su.nnedition.kord.command.MusicFolderSettingsCommand
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.Player
import su.nnedition.kord.domain.Transcoding
import su.nnedition.kord.service.GeneralSettingsAdministrationService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.TranscodingService
import su.nnedition.kord.api.module.admin.settings.AdminPlayerResponse
import su.nnedition.kord.api.module.admin.settings.MusicFolderSettingsEntry
import su.nnedition.kord.api.module.admin.settings.MusicFolderSettingsResponse
import su.nnedition.kord.api.module.admin.settings.TranscodingProfileResponse
import su.nnedition.kord.api.module.admin.settings.UpdateAdvancedSettingsRequest
import su.nnedition.kord.api.module.admin.settings.UpdateTranscodingSettingsRequest

internal fun validateGeneralSettings(command: GeneralSettingsCommand, service: GeneralSettingsAdministrationService) {
    require(!command.playlistFolder.isNullOrBlank()) { "Playlist folder is required" }
    require(!command.musicFileTypes.isNullOrBlank()) { "Music file types are required" }
    require(command.videoFileTypes != null && command.coverArtFileTypes != null) { "Media file types are required" }
    require(command.coverArtSource != null) { "Cover art source is required" }
    require(command.coverArtQuality?.let { it in 0..100 } == true) { "Cover art quality must be between 0 and 100" }
    require(command.coverArtConcurrency?.let { it > 0 } == true) { "Cover art concurrency must be positive" }
    require(command.index != null && command.ignoredArticles != null && command.genreSeparators != null && command.shortcuts != null) {
        "Index, ignored articles, genre separators and shortcuts are required"
    }
    require(command.welcomeTitle != null && command.welcomeSubtitle != null && command.welcomeMessage != null && command.loginMessage != null) {
        "Welcome and login messages are required"
    }
    val themes = service.settings.themes ?: emptyArray()
    val themeIndex = command.themeIndex?.toIntOrNull() ?: throw IllegalArgumentException("Invalid theme index")
    require(themeIndex in themes.indices) { "Unknown theme index" }
    val localeIndex = command.localeIndex?.toIntOrNull() ?: throw IllegalArgumentException("Invalid locale index")
    require(localeIndex in service.availableLocales.indices) { "Unknown locale index" }
    java.nio.file.Paths.get(command.playlistFolder)
    val duration = command.sessionDuration?.trim().orEmpty()
    require(duration.isNotEmpty()) { "Session duration is required" }
    if (duration.startsWith("P", true) || duration.startsWith("-P", true) || duration.startsWith("+P", true)) {
        val parsed = java.time.Duration.parse(duration.uppercase())
        require(!parsed.isNegative && !parsed.isZero) { "Session duration must be positive" }
    } else {
        val match = Regex("([+-]?\\d+)(ns|us|ms|s|m|h|d)?", RegexOption.IGNORE_CASE).matchEntire(duration)
            ?: throw IllegalArgumentException("Invalid session duration")
        require(match.groupValues[1].toLongOrNull()?.let { it > 0 } == true) { "Session duration must be positive" }
    }
}

internal fun Transcoding.toResponse() = TranscodingProfileResponse(
    requireNotNull(id), requireNotNull(name), requireNotNull(sourceFormats), requireNotNull(targetFormat),
    requireNotNull(step1), step2, step3, defaultActive,
)

internal fun saveTranscodingSettings(
    service: TranscodingService,
    settings: SettingsService,
    request: UpdateTranscodingSettingsRequest,
) {
    require(request.estimateTimePadding == null || request.estimateTimePadding >= 0) { "Time padding must be non-negative" }
    require(request.estimateBytePadding == null || request.estimateBytePadding >= 0) { "Byte padding must be non-negative" }
    val current = service.allTranscodings.associateBy { requireNotNull(it.id) }
    val requestedIds = request.profiles.mapNotNull { it.id }
    require(requestedIds.distinct().size == requestedIds.size) { "Duplicate transcoding profile ID" }
    require(request.deleteIds.size == request.deleteIds.distinct().size) { "Duplicate transcoding delete ID" }
    require(requestedIds.none { it in request.deleteIds }) { "A profile cannot be updated and deleted" }
    require(requestedIds.all { it in current }) { "Unknown transcoding profile ID" }
    require(request.deleteIds.all { it in current }) { "Unknown transcoding profile ID" }
    require((requestedIds + request.deleteIds).toSet() == current.keys) { "Every existing profile must be updated or deleted" }
    request.profiles.forEach {
        require(it.name.isNotBlank() && it.sourceFormats.isNotBlank() && it.targetFormat.isNotBlank() && it.step1.isNotBlank()) {
            "Profile name, formats, target and first command are required"
        }
    }
    val profiles = request.profiles.map { input ->
        Transcoding(
            input.id, input.name.trim(), input.sourceFormats.trim(), input.targetFormat.trim(),
            input.step1.trim(), input.step2?.trim()?.ifEmpty { null }, input.step3?.trim()?.ifEmpty { null }, input.defaultActive,
        )
    }
    profiles.filter { it.id != null }.forEach(service::updateTranscoding)
    request.deleteIds.forEach(service::deleteTranscoding)
    profiles.filter { it.id == null }.forEach(service::createTranscoding)
    settings.setDownsamplingCommand(request.downsampleCommand)
    settings.setHlsCommand(request.hlsCommand)
    settings.setJukeboxCommand(request.jukeboxCommand)
    settings.setVideoImageCommand(request.videoImageCommand)
    settings.setSubtitlesExtractionCommand(request.subtitlesExtractionCommand)
    settings.setTranscodeEstimateTimePadding(request.estimateTimePadding)
    settings.setTranscodeEstimateBytePadding(request.estimateBytePadding)
    settings.save()
}

internal fun updateAdvancedSettings(settings: SettingsService, request: UpdateAdvancedSettingsRequest) {
    require(request.downloadLimit >= 0) { "Download limit must be non-negative" }
    require(request.uploadLimit >= 0) { "Upload limit must be non-negative" }
    require(request.smtpPort.toIntOrNull()?.let { it in 1..65535 } == true) { "SMTP port must be between 1 and 65535" }
    require(request.smtpEncryption in setOf("None", "STARTTLS", "SSL/TLS")) { "Invalid SMTP encryption" }
    settings.setDownloadBitrateLimit(request.downloadLimit)
    settings.setUploadBitrateLimit(request.uploadLimit)
    settings.setLdapUrl(request.ldapUrl)
    settings.setLdapSearchFilter(request.ldapSearchFilter)
    settings.setLdapManagerDn(request.ldapManagerDn)
    settings.setSmtpServer(request.smtpServer)
    settings.setSmtpEncryption(request.smtpEncryption)
    settings.setSmtpPort(request.smtpPort)
    settings.setSmtpUser(request.smtpUser)
    settings.setSmtpFrom(request.smtpFrom)
    settings.setRecaptchaSiteKey(request.recaptchaSiteKey)
    settings.setLdapEnabled(request.ldapEnabled)
    settings.setLdapAutoShadowing(request.ldapAutoShadowing)
    settings.setCaptchaEnabled(request.captchaEnabled)
    request.ldapManagerPassword?.takeIf { it.isNotEmpty() }?.let { settings.setLdapManagerPassword(it) }
    request.smtpPassword?.takeIf { it.isNotEmpty() }?.let { settings.setSmtpPassword(it) }
    request.recaptchaSecretKey?.takeIf { it.isNotEmpty() }?.let { settings.setRecaptchaSecretKey(it) }
    settings.save()
}

internal fun MusicFolderSettingsCommand.toMusicFolderSettingsResponse() = MusicFolderSettingsResponse(
    interval = requireNotNull(interval).toInt(),
    hour = requireNotNull(hour).toInt(),
    scanning = isScanning,
    fastCache = isFastCache,
    organizeByFolderStructure = isOrganizeByFolderStructure,
    folders = musicFolders.orEmpty().map { it.toSettingsEntry() },
    deletedFolders = deletedMusicFolders.orEmpty().map { it.toSettingsEntry() },
    folderTypes = musicFolderTypes.map { it.name }.sorted(),
    uploadsFolder = uploadsFolder.orEmpty(),
    excludePattern = excludePatternString.orEmpty(),
    ignoreSymbolicLinks = ignoreSymLinks,
    fullScan = fullScan == true,
    clearFullScanAfterScan = clearFullScanSettingAfterScan == true,
)

internal fun MusicFolderSettingsCommand.MusicFolderInfo.toSettingsEntry() = MusicFolderSettingsEntry(
    id = requireNotNull(id),
    name = name.orEmpty(),
    path = path.orEmpty(),
    type = type,
    enabled = enabled == true,
    exists = existing,
    overlap = overlap,
    overlapDescription = overlapStatus.orEmpty(),
)

internal fun UpdateMusicFolderSettingsRequest.toCommand(current: MusicFolderSettingsCommand): MusicFolderSettingsCommand {
    require(interval == -1 || interval > 0) { "Invalid scan interval" }
    require(hour in 0..23) { "Invalid scan hour" }
    require(excludePattern.length <= 4096) { "Exclude pattern is too long" }
    if (excludePattern.isNotBlank()) java.util.regex.Pattern.compile(excludePattern)
    val existingIds = current.musicFolders.orEmpty().mapNotNull { it.id }.toSet()
    val suppliedIds = folders.mapNotNull { it.id }
    require(suppliedIds.size == folders.size && suppliedIds.distinct().size == suppliedIds.size) { "Each existing folder must have one unique ID" }
    require(suppliedIds.toSet() == existingIds) { "The folder list must include every current folder exactly once" }
    val command = current
    command.interval = interval.toString()
    command.hour = hour.toString()
    command.isFastCache = fastCache
    command.isOrganizeByFolderStructure = organizeByFolderStructure
    command.uploadsFolder = uploadsFolder
    command.excludePatternString = excludePattern
    command.ignoreSymLinks = ignoreSymbolicLinks
    command.fullScan = fullScan
    command.clearFullScanSettingAfterScan = clearFullScanAfterScan && fullScan
    command.musicFolders = folders.map { input -> input.toMusicFolderInfo(requireNotNull(input.id)) }
    command.newMusicFolder = MusicFolderSettingsCommand.MusicFolderInfo()
    newFolder?.let { input ->
        require(input.id == null && !input.delete) { "New folder cannot have an ID or be marked for deletion" }
        require(input.path.isNotBlank()) { "New folder path is required" }
        command.newMusicFolder = input.toMusicFolderInfo(null)
    }
    return command
}

internal fun UpdateMusicFolderEntry.toMusicFolderInfo(folderId: Int?): MusicFolderSettingsCommand.MusicFolderInfo {
    require(path.isNotBlank()) { "Folder path is required" }
    require(type in su.nnedition.kord.domain.MusicFolder.Type.entries.map { it.name }) { "Invalid folder type" }
    val pathValue = java.nio.file.Paths.get(path.trim())
    val normalizedName = name.trim().ifEmpty { pathValue.fileName?.toString() ?: path.trim() }
    require(normalizedName.isNotBlank()) { "Folder name is required" }
    val info = MusicFolderSettingsCommand.MusicFolderInfo().apply {
        id = folderId
        this.path = this@toMusicFolderInfo.path.trim()
        this.name = normalizedName
        this.type = this@toMusicFolderInfo.type
        enabled = this@toMusicFolderInfo.enabled
        delete = this@toMusicFolderInfo.delete
    }
    if (!delete) info.toMusicFolder()
    return info
}

internal fun Player.toAdminPlayerResponse(transcoding: TranscodingService) = AdminPlayerResponse(
    id = requireNotNull(id),
    name = name,
    description = description,
    type = type,
    lastSeen = lastSeen?.toString(),
    dynamicIp = dynamicIp,
    autoControlEnabled = autoControlEnabled,
    m3uBomEnabled = m3uBomEnabled,
    technology = technology.name,
    transcodeScheme = transcodeScheme.name,
    transcodingIds = transcoding.getTranscodingsForPlayer(this).map { requireNotNull(it.id) },
)
