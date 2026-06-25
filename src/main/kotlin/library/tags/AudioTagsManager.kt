package su.nnedition.kord.library.tags

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.flac.FlacInfoReader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.slf4j.LoggerFactory
import su.nnedition.kord.library.model.TrackFile
import java.io.File
import java.util.logging.Level

object AudioTagsManager {
    val logger = LoggerFactory.getLogger(javaClass)

    init {
        ID3v23Tag.logger.level = Level.OFF
        FlacInfoReader.logger.level = Level.OFF
    }

    fun rewriteArtistsTags(track: TrackFile, artists: List<String>) {
        val audioFile = AudioFileIO.read(track.file)
        val tag = audioFile.tagOrCreateAndSetDefault

        while (tag.hasField(FieldKey.ARTIST)) {
            tag.deleteField(FieldKey.ARTIST)
        }
        artists.forEach { artist ->
            tag.addField(FieldKey.ARTIST, artist)
        }
        audioFile.commit()
    }

    fun writeTag(track: TrackFile, key: FieldKey, vararg value: String) {
        val audioFile = AudioFileIO.read(track.file)
        val tag = audioFile.tagOrCreateAndSetDefault

        tag.setField(key, *value)
        audioFile.commit()
    }

    fun readTags(file: File): Map<String, List<String>> {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault

        val tags = mutableMapOf<String, MutableList<String>>()
        tag.fields.forEach {
            tags.computeIfAbsent(it.id) { mutableListOf() }.add(it.toString())
        }

        return tags
    }

    fun readTag(track: TrackFile, key: FieldKey): String {
        val audioFile = AudioFileIO.read(track.file)
        val tag = audioFile.tagOrCreateAndSetDefault

        return tag.getFirst(key)
    }

    fun writeCustomTag(track: TrackFile, key: String, value: String) {
        val audioFile = AudioFileIO.read(track.file)
        val tag = audioFile.tagOrCreateAndSetDefault

        writeCustomTag(tag, key, value)
        audioFile.commit()
    }

    fun writeCustomTag(tag: Tag, key: String, value: String) {
        when (tag) {
            is AbstractID3v2Tag -> {
                val frame = ID3v24Frame("TXXX")
                val body = FrameBodyTXXX()
                body.description = key
                body.setText(value)
                frame.body = body
                tag.addField(frame)
            }
            is VorbisCommentTag -> {
                tag.addField(key, value)
            }
            is FlacTag -> {
                tag.addField(tag.createField(key, value))
            }
            else -> {
                logger.warn("Writing custom tag not supported for ${tag.javaClass.simpleName}")
            }
        }
    }

    fun readCustomTag(file: File, key: String): String? {
        try {
            val tag = AudioFileIO.read(file).tag ?: return null
            return when (tag) {
                is AbstractID3v2Tag -> {
                    tag.getFields("TXXX").asSequence()
                        .mapNotNull { it as? AbstractID3v2Frame }
                        .mapNotNull { it.body as? FrameBodyTXXX }
                        .firstOrNull { it.description == key }
                        ?.text
                }
                is VorbisCommentTag, is FlacTag -> {
                    tag.getFirst(key)
                }
                else -> {
                    System.err.println("Ошибка чтения тега: ${tag.javaClass.simpleName}")
                    null
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }
    }

    fun extractYMTag(file: TrackFile): Long? {
        return readCustomTag(file.file, "YM_ID")?.toLongOrNull()
    }
}