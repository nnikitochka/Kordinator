package org.airsonic.player.service;

import org.airsonic.player.dao.RatingDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides services for user ratings.
 */
@Service
public class RatingService {
    private RatingDao ratingDao;
    private SecurityService securityService;
    private MediaFileService mediaFileService;

    public RatingService(RatingDao ratingDao, SecurityService securityService, MediaFileService mediaFileService) {
        this.ratingDao = ratingDao;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
    }

    /**
     * Returns the highest rated albums.
     *
     * @param offset      Number of albums to skip.
     * @param count       Maximum number of albums to return.
     * @param musicFolders Only return albums in these folders.
     * @return The highest rated albums.
     */
    public List<MediaFile> getHighestRatedAlbums(int offset, int count, List<MusicFolder> musicFolders) {
        return ratingDao.getHighestRatedAlbums(offset, count, musicFolders)
                .parallelStream()
                .map(mediaFileService::getMediaFile)
                .filter(file -> securityService.isReadAllowed(file, true))
                .collect(Collectors.toList());
    }

    /**
     * Sets the rating for a music file and a given user.
     *
     * @param username  The user name.
     * @param mediaFile The music file.
     * @param rating    The rating between 1 and 5, or <code>null</code> to remove the rating.
     */
    public void setRatingForUser(String username, MediaFile mediaFile, Integer rating) {
        ratingDao.setRatingForUser(username, mediaFile, rating);
    }

    /**
     * Returns the average rating for the given music file.
     *
     * @param mediaFile The music file.
     * @return The average rating, or <code>null</code> if no ratings are set.
     */
    public Double getAverageRating(MediaFile mediaFile) {
        return ratingDao.getAverageRating(mediaFile);
    }

    /**
     * Returns the rating for the given user and music file.
     *
     * @param username  The user name.
     * @param mediaFile The music file.
     * @return The rating, or <code>null</code> if no rating is set.
     */
    public Integer getRatingForUser(String username, MediaFile mediaFile) {
        return ratingDao.getRatingForUser(username, mediaFile);
    }

    public int getRatedAlbumCount(String username, List<MusicFolder> musicFolders) {
        return ratingDao.getRatedAlbumCount(username, musicFolders);
    }

    public void setRatingDao(RatingDao ratingDao) {
        this.ratingDao = ratingDao;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }
}
