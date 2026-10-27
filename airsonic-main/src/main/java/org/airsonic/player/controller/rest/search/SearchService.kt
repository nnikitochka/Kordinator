package org.airsonic.player.controller.rest.search

import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.SearchCriteria
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.search.IndexType
import org.apache.commons.lang.StringUtils
import org.subsonic.restapi.AlbumID3
import org.subsonic.restapi.ArtistID3
import org.subsonic.restapi.Response
import org.subsonic.restapi.SearchResult2
import org.subsonic.restapi.SearchResult3

/** Application logic for the three versions of the Subsonic search API. */
class SearchService(
    private val searchService: SearchService,
    private val mediaFolderService: MediaFolderService,
    private val responseMapper: SubsonicResponseMapper,
    private val jaxbWriter: JAXBWriter,
) {
    fun search(
        username: String,
        player: Player,
        any: String?,
        artist: String?,
        album: String?,
        title: String?,
        count: Int,
        offset: Int,
    ): Response {
        val query = listOfNotNull(any, artist, album, title).joinToString(" ").trim()
        val criteria = SearchCriteria().apply {
            this.query = query
            this.count = count
            this.offset = offset
        }
        val result = searchService.search(
            criteria,
            mediaFolderService.getMusicFoldersForUser(username),
            IndexType.SONG,
        )
        val searchResult = org.subsonic.restapi.SearchResult().apply {
            this.offset = result.offset
            totalHits = result.totalHits
            result.mediaFiles.forEach { match += responseMapper.child(player, it, username) }
        }
        return response().apply { this.searchResult = searchResult }
    }

    fun search2(
        username: String,
        player: Player,
        query: String?,
        musicFolderId: Int?,
        artistCount: Int,
        artistOffset: Int,
        albumCount: Int,
        albumOffset: Int,
        songCount: Int,
        songOffset: Int,
    ): Response {
        val folders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId)
        val criteria = SearchCriteria().apply { this.query = StringUtils.trimToEmpty(query) }
        val searchResult = SearchResult2()

        criteria.count = artistCount
        criteria.offset = artistOffset
        searchService.search(criteria, folders, IndexType.ARTIST).mediaFiles
            .forEach { searchResult.artist += responseMapper.artist(it, username) }

        criteria.count = albumCount
        criteria.offset = albumOffset
        searchService.search(criteria, folders, IndexType.ALBUM).mediaFiles
            .forEach { searchResult.album += responseMapper.child(player, it, username) }

        criteria.count = songCount
        criteria.offset = songOffset
        searchService.search(criteria, folders, IndexType.SONG).mediaFiles
            .forEach { searchResult.song += responseMapper.child(player, it, username) }

        return response().apply { this.searchResult2 = searchResult }
    }

    fun search3(
        username: String,
        player: Player,
        query: String?,
        musicFolderId: Int?,
        artistCount: Int,
        artistOffset: Int,
        albumCount: Int,
        albumOffset: Int,
        songCount: Int,
        songOffset: Int,
    ): Response {
        val folders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId)
        val criteria = SearchCriteria().apply { this.query = StringUtils.trimToEmpty(query) }
        val searchResult = SearchResult3()

        criteria.count = artistCount
        criteria.offset = artistOffset
        searchService.search(criteria, folders, IndexType.ARTIST_ID3).artists
            .forEach { searchResult.artist += responseMapper.artist(ArtistID3(), it, username) }

        criteria.count = albumCount
        criteria.offset = albumOffset
        searchService.search(criteria, folders, IndexType.ALBUM_ID3).albums
            .forEach { searchResult.album += responseMapper.album(AlbumID3(), it, username) }

        criteria.count = songCount
        criteria.offset = songOffset
        searchService.search(criteria, folders, IndexType.SONG).mediaFiles
            .forEach { searchResult.song += responseMapper.child(player, it, username) }

        return response().apply { this.searchResult3 = searchResult }
    }

    private fun response(): Response = jaxbWriter.createResponse(true)
}
