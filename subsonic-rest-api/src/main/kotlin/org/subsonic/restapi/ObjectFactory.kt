@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.JAXBElement
import jakarta.xml.bind.annotation.XmlElementDecl
import jakarta.xml.bind.annotation.XmlRegistry
import javax.xml.namespace.QName

@XmlRegistry
class ObjectFactory {
    companion object {
        private val SUBSONIC_RESPONSE_QNAME = QName("http://subsonic.org/restapi", "subsonic-response")
    }

    fun createResponse(): Response = Response()

    fun createMusicFolders(): MusicFolders = MusicFolders()

    fun createMusicFolder(): MusicFolder = MusicFolder()

    fun createIndexes(): Indexes = Indexes()

    fun createIndex(): Index = Index()

    fun createArtist(): Artist = Artist()

    fun createGenres(): Genres = Genres()

    fun createGenre(): Genre = Genre()

    fun createArtistsID3(): ArtistsID3 = ArtistsID3()

    fun createIndexID3(): IndexID3 = IndexID3()

    fun createArtistID3(): ArtistID3 = ArtistID3()

    fun createArtistWithAlbumsID3(): ArtistWithAlbumsID3 = ArtistWithAlbumsID3()

    fun createAlbumID3(): AlbumID3 = AlbumID3()

    fun createAlbumWithSongsID3(): AlbumWithSongsID3 = AlbumWithSongsID3()

    fun createVideos(): Videos = Videos()

    fun createVideoInfo(): VideoInfo = VideoInfo()

    fun createCaptions(): Captions = Captions()

    fun createAudioTrack(): AudioTrack = AudioTrack()

    fun createVideoConversion(): VideoConversion = VideoConversion()

    fun createDirectory(): Directory = Directory()

    fun createChild(): Child = Child()

    fun createNowPlaying(): NowPlaying = NowPlaying()

    fun createNowPlayingEntry(): NowPlayingEntry = NowPlayingEntry()

    fun createSearchResult(): SearchResult = SearchResult()

    fun createSearchResult2(): SearchResult2 = SearchResult2()

    fun createSearchResult3(): SearchResult3 = SearchResult3()

    fun createPlaylists(): Playlists = Playlists()

    fun createPlaylist(): Playlist = Playlist()

    fun createPlaylistWithSongs(): PlaylistWithSongs = PlaylistWithSongs()

    fun createJukeboxStatus(): JukeboxStatus = JukeboxStatus()

    fun createJukeboxPlaylist(): JukeboxPlaylist = JukeboxPlaylist()

    fun createChatMessages(): ChatMessages = ChatMessages()

    fun createChatMessage(): ChatMessage = ChatMessage()

    fun createAlbumList(): AlbumList = AlbumList()

    fun createAlbumList2(): AlbumList2 = AlbumList2()

    fun createSongs(): Songs = Songs()

    fun createLyrics(): Lyrics = Lyrics()

    fun createPodcasts(): Podcasts = Podcasts()

    fun createPodcastChannel(): PodcastChannel = PodcastChannel()

    fun createNewestPodcasts(): NewestPodcasts = NewestPodcasts()

    fun createPodcastEpisode(): PodcastEpisode = PodcastEpisode()

    fun createBookmarks(): Bookmarks = Bookmarks()

    fun createBookmark(): Bookmark = Bookmark()

    fun createPlayQueue(): PlayQueue = PlayQueue()

    fun createShares(): Shares = Shares()

    fun createShare(): Share = Share()

    fun createStarred(): Starred = Starred()

    fun createAlbumInfo(): AlbumInfo = AlbumInfo()

    fun createArtistInfoBase(): ArtistInfoBase = ArtistInfoBase()

    fun createArtistInfo(): ArtistInfo = ArtistInfo()

    fun createArtistInfo2(): ArtistInfo2 = ArtistInfo2()

    fun createSimilarSongs(): SimilarSongs = SimilarSongs()

    fun createSimilarSongs2(): SimilarSongs2 = SimilarSongs2()

    fun createTopSongs(): TopSongs = TopSongs()

    fun createStarred2(): Starred2 = Starred2()

    fun createLicense(): License = License()

    fun createScanStatus(): ScanStatus = ScanStatus()

    fun createUsers(): Users = Users()

    fun createUser(): User = User()

    fun createError(): Error = Error()

    @XmlElementDecl(namespace = "http://subsonic.org/restapi", name = "subsonic-response")
    fun createSubsonicResponse(value: Response): JAXBElement<Response> =
        JAXBElement(SUBSONIC_RESPONSE_QNAME, Response::class.java, null, value)
}
