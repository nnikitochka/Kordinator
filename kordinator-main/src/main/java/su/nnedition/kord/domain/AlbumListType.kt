package su.nnedition.kord.domain

enum class AlbumListType(
    val id: String,
    val description: String
) {
    RANDOM("random", "Random"),
    NEWEST("newest", "Recently Added"),
    STARRED("starred", "Starred"),
    HIGHEST("highest", "Top Rated"),
    FREQUENT("frequent", "Most Played"),
    RECENT("recent", "Recently Played"),
    DECADE("decade", "By Decade"),
    GENRE("genre", "By Genre"),
    ALPHABETICAL("alphabetical", "All");

    companion object {
        @JvmStatic
        fun fromId(id: String?): AlbumListType? {
            id ?: return null
            return entries.firstOrNull { it.id == id }
        }
    }
}
