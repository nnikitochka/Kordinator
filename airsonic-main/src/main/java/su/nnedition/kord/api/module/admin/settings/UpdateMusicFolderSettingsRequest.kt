package su.nnedition.kord.api.module.admin.settings

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
