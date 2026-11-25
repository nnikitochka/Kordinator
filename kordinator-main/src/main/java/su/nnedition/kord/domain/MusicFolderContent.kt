package su.nnedition.kord.domain

import su.nnedition.kord.domain.MusicIndex.SortableArtistWithMediaFiles
import java.util.*

data class MusicFolderContent(
    val indexedArtists: SortedMap<MusicIndex, MutableList<SortableArtistWithMediaFiles>>,
    val singleSongs: MutableList<MediaFile>
)
