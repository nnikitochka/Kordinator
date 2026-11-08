@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlRootElement
import jakarta.xml.bind.annotation.XmlType

@XmlRootElement(name = "subsonic-response", namespace = "http://subsonic.org/restapi")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Response", propOrder = ["musicFolders", "indexes", "directory", "genres", "artists", "artist", "album", "song", "videos", "videoInfo", "nowPlaying", "searchResult", "searchResult2", "searchResult3", "playlists", "playlist", "jukeboxStatus", "jukeboxPlaylist", "license", "users", "user", "chatMessages", "albumList", "albumList2", "randomSongs", "songsByGenre", "lyrics", "podcasts", "newestPodcasts", "bookmarks", "playQueue", "shares", "starred", "starred2", "albumInfo", "artistInfo", "artistInfo2", "similarSongs", "similarSongs2", "topSongs", "scanStatus", "coverArtSearchResults", "livePlayQueue", "websiteDirectory", "websiteArtistInfo", "tagEdit", "websiteSettings", "websiteUserAdministration", "musicFolderSettings", "generalSettings", "transcodingSettings", "advancedSettings", "playerSettings", "uploadStatuses", "databaseStatuses", "error"])
class Response {
    @field:XmlElement(name = "playerSettings")
    var playerSettings: PlayerSettings? = null

    @field:XmlElement(name = "advancedSettings")
    var advancedSettings: AdvancedSettings? = null

    @field:XmlElement(name = "transcodingSettings")
    var transcodingSettings: TranscodingSettings? = null

    @field:XmlElement(name = "generalSettings")
    var generalSettings: GeneralSettings? = null

    @field:XmlElement(name = "musicFolderSettings")
    var musicFolderSettings: MusicFolderSettings? = null

    @field:XmlElement(name = "websiteUserAdministration")
    var websiteUserAdministration: WebsiteUserAdministration? = null

    @field:XmlElement(name = "databaseStatuses")
    var databaseStatuses: DatabaseStatuses? = null

    @field:XmlElement(name = "uploadStatuses")
    var uploadStatuses: UploadStatuses? = null

    @field:XmlElement(name = "websiteSettings")
    var websiteSettings: WebsiteSettings? = null

    @field:XmlElement(name = "livePlayQueue")
    var livePlayQueue: LivePlayQueue? = null

    @field:XmlElement(name = "tagEdit")
    var tagEdit: TagEdit? = null

    @field:XmlElement(name = "websiteArtistInfo")
    var websiteArtistInfo: WebsiteArtistInfo? = null

    @field:XmlElement(name = "websiteDirectory")
    var websiteDirectory: WebsiteDirectory? = null

    @field:XmlElement(name = "musicFolders")
    var musicFolders: MusicFolders? = null

    @field:XmlElement(name = "indexes")
    var indexes: Indexes? = null

    @field:XmlElement(name = "directory")
    var directory: Directory? = null

    @field:XmlElement(name = "genres")
    var genres: Genres? = null

    @field:XmlElement(name = "artists")
    var artists: ArtistsID3? = null

    @field:XmlElement(name = "artist")
    var artist: ArtistWithAlbumsID3? = null

    @field:XmlElement(name = "album")
    var album: AlbumWithSongsID3? = null

    @field:XmlElement(name = "song")
    var song: Child? = null

    @field:XmlElement(name = "videos")
    var videos: Videos? = null

    @field:XmlElement(name = "videoInfo")
    var videoInfo: VideoInfo? = null

    @field:XmlElement(name = "nowPlaying")
    var nowPlaying: NowPlaying? = null

    @field:XmlElement(name = "searchResult")
    var searchResult: SearchResult? = null

    @field:XmlElement(name = "searchResult2")
    var searchResult2: SearchResult2? = null

    @field:XmlElement(name = "searchResult3")
    var searchResult3: SearchResult3? = null

    @field:XmlElement(name = "playlists")
    var playlists: Playlists? = null

    @field:XmlElement(name = "playlist")
    var playlist: PlaylistWithSongs? = null

    @field:XmlElement(name = "jukeboxStatus")
    var jukeboxStatus: JukeboxStatus? = null

    @field:XmlElement(name = "jukeboxPlaylist")
    var jukeboxPlaylist: JukeboxPlaylist? = null

    @field:XmlElement(name = "license")
    var license: License? = null

    @field:XmlElement(name = "users")
    var users: Users? = null

    @field:XmlElement(name = "user")
    var user: User? = null

    @field:XmlElement(name = "chatMessages")
    var chatMessages: ChatMessages? = null

    @field:XmlElement(name = "albumList")
    var albumList: AlbumList? = null

    @field:XmlElement(name = "albumList2")
    var albumList2: AlbumList2? = null

    @field:XmlElement(name = "randomSongs")
    var randomSongs: Songs? = null

    @field:XmlElement(name = "songsByGenre")
    var songsByGenre: Songs? = null

    @field:XmlElement(name = "lyrics")
    var lyrics: Lyrics? = null

    @field:XmlElement(name = "podcasts")
    var podcasts: Podcasts? = null

    @field:XmlElement(name = "newestPodcasts")
    var newestPodcasts: NewestPodcasts? = null

    @field:XmlElement(name = "bookmarks")
    var bookmarks: Bookmarks? = null

    @field:XmlElement(name = "playQueue")
    var playQueue: PlayQueue? = null

    @field:XmlElement(name = "shares")
    var shares: Shares? = null

    @field:XmlElement(name = "starred")
    var starred: Starred? = null

    @field:XmlElement(name = "starred2")
    var starred2: Starred2? = null

    @field:XmlElement(name = "albumInfo")
    var albumInfo: AlbumInfo? = null

    @field:XmlElement(name = "artistInfo")
    var artistInfo: ArtistInfo? = null

    @field:XmlElement(name = "artistInfo2")
    var artistInfo2: ArtistInfo2? = null

    @field:XmlElement(name = "similarSongs")
    var similarSongs: SimilarSongs? = null

    @field:XmlElement(name = "similarSongs2")
    var similarSongs2: SimilarSongs2? = null

    @field:XmlElement(name = "topSongs")
    var topSongs: TopSongs? = null

    @field:XmlElement(name = "scanStatus")
    var scanStatus: ScanStatus? = null

    @field:XmlElement(name = "coverArtSearchResults")
    var coverArtSearchResults: CoverArtSearchResults? = null

    @field:XmlElement(name = "error")
    var error: Error? = null

    @field:XmlAttribute(name = "status", required = true)
    var status: ResponseStatus? = null

    @field:XmlAttribute(name = "version", required = true)
    var version: String? = null

}
