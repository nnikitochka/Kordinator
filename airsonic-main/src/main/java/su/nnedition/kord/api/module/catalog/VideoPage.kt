package su.nnedition.kord.api.module.catalog

data class VideoPage(val items: List<VideoResponse>, val limit: Int, val offset: Int, val hasMore: Boolean)
