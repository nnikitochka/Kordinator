package org.airsonic.player.service.upnp;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.util.Util;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.SortCriterion;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Allen Petersen
 * @version $Id$
 */
@Service
public class RecentAlbumUpnpProcessor extends AlbumUpnpProcessor {
    private final static int RECENT_COUNT = 50;

    public RecentAlbumUpnpProcessor() {
        setRootId(DispatchingContentDirectory.CONTAINER_ID_RECENT_PREFIX);
        setRootTitle("RecentAlbums");
    }

    /**
     * Browses the top-level content.
     */
    @Override
    public BrowseResult browseRoot(String filter, long firstResult, long maxResults, SortCriterion[] orderBy) throws Exception {
        // AlbumUpnpProcessor overrides browseRoot() with an optimization;
        // this restores the default behavior for the subclass.
        DIDLContent didl = new DIDLContent();
        List<Album> allItems = getAllItems();
        if (filter != null) {
            // filter items (not implemented yet)
        }
        if (orderBy != null) {
            // sort items (not implemented yet)
        }
        List<Album> selectedItems = Util.subList(allItems, firstResult, maxResults);
        for (Album item : selectedItems) {
            addItem(didl, item);
        }

        return createBrowseResult(didl, didl.getCount(), allItems.size());
    }

    @Override
    public List<Album> getAllItems() {
        List<MusicFolder> allFolders = getDispatchingContentDirectory().getMediaFolderService().getAllMusicFolders();
        List<Album> recentAlbums = getAlbumDao().getNewestAlbums(0, RECENT_COUNT, allFolders);
        if (recentAlbums.size() > 1) {
            // if there is more than one recent album, add in an option to
            // view the tracks in all the recent albums together
            Album viewAll = new Album();
            viewAll.setName("- All Albums -");
            viewAll.setId(-1);
            viewAll.setComment(AlbumUpnpProcessor.ALL_RECENT);
            recentAlbums.add(0, viewAll);
        }
        return recentAlbums;
    }

    @Override
    public int getAllItemsSize() {
        List<MusicFolder> allFolders = getDispatchingContentDirectory().getMediaFolderService().getAllMusicFolders();
        int allAlbumCount = getAlbumDao().getAlbumCount(allFolders);
        return Math.min(allAlbumCount, RECENT_COUNT);
    }
}
