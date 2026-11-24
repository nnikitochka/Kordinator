package su.nnedition.kord.api.module.bookmark

data class BookmarkPage(
    val items: List<BookmarkResponse>,
    val count: Int,
)