package org.airsonic.player.controller.rest.artists

import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MusicIndexService
import org.subsonic.restapi.ArtistID3
import org.subsonic.restapi.ArtistsID3
import org.subsonic.restapi.IndexID3

fun interface ArtistLookup {
    fun getArtists(musicFolders: List<@JvmSuppressWildcards MusicFolder>): List<Artist>
}

fun interface UserMusicFolders {
    fun getMusicFoldersForUser(username: String): List<MusicFolder>
}

fun interface ArtistId3Mapper {
    fun map(target: ArtistID3, artist: Artist, username: String): ArtistID3
}

/** Builds the ID3 artist index of the Subsonic API. */
class ArtistsService(
    private val artistLookup: ArtistLookup,
    private val musicIndexService: MusicIndexService,
    private val ignoredArticles: () -> String,
    private val userMusicFolders: UserMusicFolders,
    private val artistMapper: ArtistId3Mapper,
) {
    fun getArtists(username: String): ArtistsID3 = ArtistsID3().also { result ->
        result.ignoredArticles = ignoredArticles()
        val indexedArtists = musicIndexService.getIndexedArtists(
            artistLookup.getArtists(userMusicFolders.getMusicFoldersForUser(username))
        )
        for ((musicIndex, sortableArtists) in indexedArtists) {
            val index = IndexID3().apply { name = musicIndex.index }
            result.index += index
            for (sortableArtist in sortableArtists) {
                index.artist += artistMapper.map(ArtistID3(), sortableArtist.artist, username)
            }
        }
    }
}
