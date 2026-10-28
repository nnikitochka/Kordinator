package org.airsonic.player.domain

import org.airsonic.player.domain.MusicIndex.SortableArtistWithMediaFiles
import java.util.*

data class MusicFolderContent(
    val indexedArtists: SortedMap<MusicIndex, MutableList<SortableArtistWithMediaFiles>>,
    val singleSongs: MutableList<MediaFile>
)
