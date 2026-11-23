package org.airsonic.player.service.search

import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.apache.commons.lang3.StringUtils.isEmpty
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.FieldType
import org.apache.lucene.document.IntPoint
import org.apache.lucene.document.SortedDocValuesField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.Term
import org.apache.lucene.util.BytesRef
import java.util.function.BiConsumer

/** Creates the Lucene documents stored in the media indexes. */
class DocumentFactory {
    private val fieldId = BiConsumer<Document, Int> { doc, value ->
        doc.add(StoredField(FieldNames.ID, value.toString(), TYPE_ID))
    }

    private val fieldFolderId = BiConsumer<Document, Int> { doc, value ->
        doc.add(StoredField(FieldNames.FOLDER_ID, value.toString(), TYPE_ID_NO_STORE))
    }

    private val fieldKey = Consumer3<Document, String, String> { doc, field, value ->
        doc.add(StoredField(field, value, TYPE_KEY))
    }

    private val fieldMediaType = BiConsumer<Document, String> { doc, value ->
        fieldKey.accept(doc, FieldNames.MEDIA_TYPE, value)
    }

    private val fieldFolderPath = BiConsumer<Document, String> { doc, value ->
        fieldKey.accept(doc, FieldNames.FOLDER, value)
    }

    private val fieldGenre = BiConsumer<Document, String?> { doc, value ->
        if (!isEmpty(value)) fieldKey.accept(doc, FieldNames.GENRE, value)
    }

    private val fieldYear = Consumer3<Document, String, Int?> { doc, fieldName, value ->
        if (value != null) doc.add(IntPoint(fieldName, value))
    }

    private val fieldWords = Consumer3<Document, String, String?> { doc, fieldName, value ->
        if (!isEmpty(value)) {
            doc.add(TextField(fieldName, value, Store.NO))
            doc.add(SortedDocValuesField(fieldName, BytesRef(value)))
        }
    }

    fun createPrimarykey(id: Int): Term = Term(FieldNames.ID, id.toString())

    fun createPrimarykey(album: Album): Term = createPrimarykey(album.id)

    fun createPrimarykey(artist: Artist): Term = createPrimarykey(artist.id)

    fun createPrimarykey(mediaFile: MediaFile): Term = createPrimarykey(mediaFile.id)

    fun createAlbumDocument(mediaFile: MediaFile, musicFolder: MusicFolder): Document = Document().apply {
        fieldId.accept(this, mediaFile.id)
        fieldWords.accept(this, FieldNames.ARTIST, mediaFile.artist)
        fieldWords.accept(this, FieldNames.ALBUM, mediaFile.albumName)
        fieldFolderPath.accept(this, musicFolder.path.toString())
    }

    fun createArtistDocument(mediaFile: MediaFile, musicFolder: MusicFolder): Document = Document().apply {
        fieldId.accept(this, mediaFile.id)
        fieldWords.accept(this, FieldNames.ARTIST, mediaFile.artist)
        fieldFolderPath.accept(this, musicFolder.path.toString())
    }

    fun createAlbumId3Document(album: Album): Document = Document().apply {
        fieldId.accept(this, album.id)
        fieldWords.accept(this, FieldNames.ARTIST, album.artist)
        fieldWords.accept(this, FieldNames.ALBUM, album.name)
        fieldFolderId.accept(this, album.folderId!!)
    }

    fun createArtistId3Document(artist: Artist, musicFolder: MusicFolder): Document = Document().apply {
        fieldId.accept(this, artist.id)
        fieldWords.accept(this, FieldNames.ARTIST, artist.name)
        fieldFolderId.accept(this, musicFolder.id)
    }

    fun createSongDocument(mediaFile: MediaFile, musicFolder: MusicFolder): Document = Document().apply {
        fieldId.accept(this, mediaFile.id)
        fieldMediaType.accept(this, mediaFile.mediaType.name)
        fieldWords.accept(this, FieldNames.TITLE, mediaFile.title)
        fieldWords.accept(this, FieldNames.ARTIST, mediaFile.artist)
        fieldGenre.accept(this, mediaFile.genre)
        fieldYear.accept(this, FieldNames.YEAR, mediaFile.year)
        fieldFolderPath.accept(this, musicFolder.path.toString())
    }

    private fun interface Consumer3<T, U, V> {
        fun accept(first: T, second: U, third: V)
    }

    companion object {
        private val TYPE_ID = FieldType().apply {
            setIndexOptions(IndexOptions.DOCS)
            setTokenized(false)
            setOmitNorms(true)
            setStored(true)
            freeze()
        }

        private val TYPE_ID_NO_STORE = FieldType().apply {
            setIndexOptions(IndexOptions.DOCS)
            setTokenized(false)
            setOmitNorms(true)
            setStored(false)
            freeze()
        }

        private val TYPE_KEY = FieldType().apply {
            setIndexOptions(IndexOptions.DOCS)
            setTokenized(false)
            setOmitNorms(true)
            setStored(false)
            freeze()
        }
    }
}
