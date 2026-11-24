package org.airsonic.player.command

import org.airsonic.player.domain.MusicFolder
import org.apache.commons.lang.StringUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.EnumSet

/** Parameters for library administration. */
class MusicFolderSettingsCommand {
    var interval: String? = null
    var hour: String? = null
    var isScanning: Boolean = false
    var isFastCache: Boolean = false
    var isOrganizeByFolderStructure: Boolean = false
    var musicFolders: List<MusicFolderInfo>? = null
    var newMusicFolder: MusicFolderInfo? = null
    var deletedMusicFolders: List<MusicFolderInfo>? = null
    var musicFolderTypes: EnumSet<MusicFolder.Type> = EnumSet.allOf(MusicFolder.Type::class.java)
    var uploadsFolder: String? = null
    var excludePatternString: String? = null
    var ignoreSymLinks: Boolean = false
    var fullScan: Boolean? = null
    var clearFullScanSettingAfterScan: Boolean? = null

    class MusicFolderInfo {
        var id: Int? = null
        var path: String? = null
        var name: String? = null
        var type: String = MusicFolder.Type.MEDIA.name
        var enabled: Boolean? = true
        var delete: Boolean = false
        var existing: Boolean = false
            private set
        var overlap: Boolean = false
            private set
        var overlapStatus: String? = null
            private set

        constructor()

        constructor(musicFolder: MusicFolder, overlap: Boolean, overlapStatus: String?) {
            id = musicFolder.id
            path = musicFolder.path.toString()
            name = musicFolder.name
            type = musicFolder.type.name
            enabled = musicFolder.isEnabled()
            existing = Files.exists(musicFolder.path) && Files.isDirectory(musicFolder.path)
            this.overlap = overlap
            this.overlapStatus = overlapStatus
        }

        fun toMusicFolder(): MusicFolder? {
            val normalizedPath = StringUtils.trimToNull(path) ?: return null
            val folderPath = Paths.get(normalizedPath)
            val normalizedName = StringUtils.trimToNull(name) ?: folderPath.fileName.toString()
            return MusicFolder(
                id,
                folderPath,
                normalizedName,
                MusicFolder.Type.valueOf(type),
                enabled == true,
                Instant.now(),
            )
        }
    }
}
