package org.airsonic.player.service.playlist

import chameleon.playlist.SpecificPlaylist
import chameleon.playlist.SpecificPlaylistProvider

interface PlaylistExportHandler {
    fun canHandle(providerClass: Class<out SpecificPlaylistProvider>): Boolean

    @Throws(Exception::class)
    fun handle(id: Int, provider: SpecificPlaylistProvider): SpecificPlaylist

    val order: Int get() = Int.MAX_VALUE
}
