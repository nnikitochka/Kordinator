package su.nnedition.kord.api.module.video.bookmark

data class SetVideoBookmarkRequest(val positionMillis: Long, val comment: String? = null)
