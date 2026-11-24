package su.nnedition.kord.api.module.bookmark

data class SetBookmarkRequest(
    val positionMillis: Long,
    val comment: String? = null,
)