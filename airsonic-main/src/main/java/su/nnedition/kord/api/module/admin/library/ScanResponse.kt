package su.nnedition.kord.api.module.admin.library

data class ScanResponse(
    val id: String,
    val libraryId: Long,
    val state: String,
    val tracksScanned: Long,
    val errorCount: Long,
    val startedAt: String,
    val finishedAt: String?,
)
