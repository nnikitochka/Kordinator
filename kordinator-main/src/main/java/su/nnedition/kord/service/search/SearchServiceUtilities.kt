package su.nnedition.kord.service.search

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.Artist
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.ParamSearchResult
import su.nnedition.kord.domain.SearchResult
import su.nnedition.kord.service.media.MediaFileService
import org.apache.commons.collections4.CollectionUtils
import org.apache.lucene.document.Document
import java.util.function.BiConsumer
import java.util.function.Function

/** Helper operations used by SearchService to join Lucene results with stored entities. */
class SearchServiceUtilities(
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val mediaFileService: MediaFileService
) {
    @JvmField
    val round: Function<Long, Int> = Function { it.toInt() }

    @JvmField
    val getId: Function<Document, Int> = Function { Integer.valueOf(it.get(FieldNames.ID)) }

    @JvmField
    val addMediaFileIfAnyMatch: BiConsumer<MutableList<MediaFile>, Int> = BiConsumer { destination, id ->
        if (destination.none { id == it.id }) {
            mediaFileService.getMediaFile(id)?.let(destination::add)
        }
    }

    @JvmField
    val addArtistId3IfAnyMatch: BiConsumer<MutableList<Artist>, Int> = BiConsumer { destination, id ->
        if (destination.none { id == it.id }) {
            artistDao.getArtist(id)?.let(destination::add)
        }
    }

    @JvmField
    val getIndexType: Function<Class<*>, IndexType?> = Function { assignableClass ->
        when {
            assignableClass.isAssignableFrom(Album::class.java) -> IndexType.ALBUM_ID3
            assignableClass.isAssignableFrom(Artist::class.java) -> IndexType.ARTIST_ID3
            assignableClass.isAssignableFrom(MediaFile::class.java) -> IndexType.SONG
            else -> null
        }
    }

    @JvmField
    val getFieldName: Function<Class<*>, String?> = Function { assignableClass ->
        when {
            assignableClass.isAssignableFrom(Album::class.java) -> FieldNames.ALBUM
            assignableClass.isAssignableFrom(Artist::class.java) -> FieldNames.ARTIST
            assignableClass.isAssignableFrom(MediaFile::class.java) -> FieldNames.TITLE
            else -> null
        }
    }

    @JvmField
    val addAlbumId3IfAnyMatch: BiConsumer<MutableList<Album>, Int> = BiConsumer { destination, subjectId ->
        if (destination.none { subjectId == it.id }) {
            albumDao.getAlbum(subjectId)?.let(destination::add)
        }
    }

    fun addIgnoreNull(collection: MutableCollection<*>, value: Any?): Boolean =
        CollectionUtils.addIgnoreNull(collection as MutableCollection<Any?>, value)

    fun addIgnoreNull(collection: MutableCollection<*>, indexType: IndexType, subjectId: Int): Boolean =
        when (indexType) {
            IndexType.ALBUM, IndexType.SONG -> addIgnoreNull(collection, mediaFileService.getMediaFile(subjectId))
            IndexType.ALBUM_ID3 -> addIgnoreNull(collection, albumDao.getAlbum(subjectId))
            else -> false
        }

    fun <T> addIgnoreNull(
        destination: ParamSearchResult<T>,
        indexType: IndexType,
        subjectId: Int,
        subjectClass: Class<T>
    ) {
        when (indexType) {
            IndexType.SONG -> addIgnoreNull(destination.items, subjectClass.cast(mediaFileService.getMediaFile(subjectId)))
            IndexType.ARTIST_ID3 -> addIgnoreNull(destination.items, subjectClass.cast(artistDao.getArtist(subjectId)))
            IndexType.ALBUM_ID3 -> addIgnoreNull(destination.items, subjectClass.cast(albumDao.getAlbum(subjectId)))
            else -> Unit
        }
    }

    fun addIfAnyMatch(destination: SearchResult, subjectIndexType: IndexType, subject: Document) {
        val documentId = getId.apply(subject)
        when (subjectIndexType) {
            IndexType.ARTIST, IndexType.ALBUM, IndexType.SONG ->
                addMediaFileIfAnyMatch.accept(destination.mediaFiles, documentId)
            IndexType.ARTIST_ID3 -> addArtistId3IfAnyMatch.accept(destination.artists, documentId)
            IndexType.ALBUM_ID3 -> addAlbumId3IfAnyMatch.accept(destination.albums, documentId)
        }
    }
}
