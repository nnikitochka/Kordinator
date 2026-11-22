package org.airsonic.player.service.search

/** Identifies a Lucene index entity and its search fields and boosts. */
enum class IndexType(private val fields: Array<String>, private val boosts: Map<String, Float>) {
    SONG(
        arrayOf(FieldNames.TITLE, FieldNames.ARTIST),
        mapOf(FieldNames.TITLE to 1.1F)
    ),
    ALBUM(
        arrayOf(FieldNames.ALBUM, FieldNames.ARTIST),
        mapOf(FieldNames.ALBUM to 1.1F)
    ),
    ALBUM_ID3(
        arrayOf(FieldNames.ALBUM, FieldNames.ARTIST),
        mapOf(FieldNames.ALBUM to 1.1F)
    ),
    ARTIST(
        arrayOf(FieldNames.ARTIST),
        emptyMap()
    ),
    ARTIST_ID3(
        arrayOf(FieldNames.ARTIST),
        emptyMap()
    );

    fun getBoosts(): Map<String, Float> = boosts

    fun getFields(): Array<String> = fields
}
