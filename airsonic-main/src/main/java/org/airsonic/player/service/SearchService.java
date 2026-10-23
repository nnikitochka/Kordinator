package org.airsonic.player.service;

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.ParamSearchResult;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.domain.SearchCriteria;
import org.airsonic.player.domain.SearchResult;
import org.airsonic.player.service.search.IndexType;

import java.util.List;

/**
 * Performs Lucene-based searching.
 *
 * @author Sindre Mehus
 * @version $Id$
 * @see MediaScannerService
 */
public interface SearchService {
    SearchResult search(SearchCriteria criteria, List<MusicFolder> musicFolders,
            IndexType indexType);

    /**
     * Returns a number of random songs.
     *
     * @param criteria Search criteria.
     * @return List of random songs.
     */
    List<MediaFile> getRandomSongs(RandomSearchCriteria criteria);

    /**
     * Returns a number of random albums.
     *
     * @param count Number of albums to return.
     * @param musicFolders Only return albums from these folders.
     * @return List of random albums.
     */
    List<MediaFile> getRandomAlbums(int count, List<MusicFolder> musicFolders);

    /**
     * Returns a number of random albums, using ID3 tag.
     *
     * @param count Number of albums to return.
     * @param musicFolders Only return albums from these folders.
     * @return List of random albums.
     */
    List<Album> getRandomAlbumsId3(int count, List<MusicFolder> musicFolders);

    <T> ParamSearchResult<T> searchByName(
            String name, int offset, int count, List<MusicFolder> folderList, Class<T> clazz);
}
