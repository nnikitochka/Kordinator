package su.nnedition.kord.api.module.admin.settings

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
