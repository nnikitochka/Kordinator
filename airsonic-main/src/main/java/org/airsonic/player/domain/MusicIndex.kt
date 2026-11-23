package org.airsonic.player.domain

import java.io.Serializable
import java.text.CollationKey
import java.text.Collator

open class MusicIndex(index: String) : Serializable {
    companion object {
        @JvmField
        val OTHER = MusicIndex("#")

        private const val serialVersionUID = 7631094417730994210L
    }

    open val index: String = index
    open val prefixes: MutableList<String> = ArrayList()

    open fun addPrefix(prefix: String) {
        prefixes.add(prefix)
    }

    override fun equals(other: Any?): Boolean = this === other || (other is MusicIndex && index == other.index)

    override fun hashCode(): Int = index.hashCode()

    abstract class SortableArtist(
        open val name: String,
        open val sortableName: String,
        collator: Collator,
    ) : Comparable<SortableArtist> {
        private val collationKey: CollationKey = collator.getCollationKey(sortableName)

        override fun compareTo(other: SortableArtist): Int = collationKey.compareTo(other.collationKey)
    }

    open class SortableArtistWithMediaFiles(
        name: String,
        sortableName: String,
        collator: Collator,
    ) : SortableArtist(name, sortableName, collator) {
        open val mediaFiles: MutableList<MediaFile> = ArrayList()

        open fun addMediaFile(mediaFile: MediaFile) {
            mediaFiles.add(mediaFile)
        }
    }

    open class SortableArtistWithArtist(
        name: String,
        sortableName: String,
        open val artist: Artist,
        collator: Collator,
    ) : SortableArtist(name, sortableName, collator)
}
