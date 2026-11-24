package su.nnedition.kord.api.module.library

data class KordinatorDirectoryContents(
    val id: Int,
    val name: String,
    val folderId: Int,
    val parentId: Int?,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val entries: List<KordinatorLibraryEntry>,
)
