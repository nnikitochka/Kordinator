package su.nnedition.kord.service

import su.nnedition.kord.dao.RatingDao
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFileService
import java.util.stream.Collectors

/** Provides services for user ratings. */
class RatingService(
    var ratingDao: RatingDao,
    var securityService: SecurityService,
    var mediaFileService: MediaFileService,
) {
    fun getHighestRatedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        ratingDao.getHighestRatedAlbums(offset, count, musicFolders)
            .parallelStream()
            .map { mediaFileService.getMediaFile(it)!! }
            .filter { securityService.isReadAllowed(it, true) }
            .collect(Collectors.toList())

    fun setRatingForUser(username: String, mediaFile: MediaFile, rating: Int?) {
        ratingDao.setRatingForUser(username, mediaFile, rating)
    }

    fun getAverageRating(mediaFile: MediaFile): Double? = ratingDao.getAverageRating(mediaFile)

    fun getRatingForUser(username: String, mediaFile: MediaFile): Int? =
        ratingDao.getRatingForUser(username, mediaFile)

    fun getRatedAlbumCount(username: String, musicFolders: List<MusicFolder>): Int =
        ratingDao.getRatedAlbumCount(username, musicFolders)
}
