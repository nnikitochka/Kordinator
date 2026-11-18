package org.airsonic.player.service.upnp;

import org.airsonic.player.domain.Genre;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.util.Util;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.SortCriterion;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.GenreContainer;

import java.util.List;

/**
 * @author Allen Petersen
 * @version $Id$
 */
public class GenreUpnpProcessor extends UpnpContentProcessor <Genre, MediaFile> {

    public GenreUpnpProcessor() {
        setRootId(DispatchingContentDirectory.CONTAINER_ID_GENRE_PREFIX);
        setRootTitle("Genres");
    }

    /**
     * Browses the top-level content of a type.
     */
    @Override
    public BrowseResult browseRoot(String filter, long firstResult, long maxResults, SortCriterion[] orderBy) throws Exception {
        // we have to override this to do an index-based id.
        DIDLContent didl = new DIDLContent();
        List<Genre> allItems = getAllItems();
        if (filter != null) {
            // filter items
        }
        if (orderBy != null) {
            // sort items
        }
        List<Genre> selectedItems = Util.subList(allItems, firstResult, maxResults);
        for (int i = 0; i < selectedItems.size(); i++) {
            Genre item = selectedItems.get(i);
            didl.addContainer(createContainer(item, (int) (i + firstResult)));
        }
        return createBrowseResult(didl, didl.getCount(), allItems.size());
    }

    @Override
    public Container createContainer(Genre item) {
        // genre uses index because we don't have a proper id
        return null;
    }

    public Container createContainer(Genre item, int index) {
        GenreContainer container = new GenreContainer();
        container.setId(getRootId() + DispatchingContentDirectory.SEPARATOR + index);
        container.setParentID(getRootId());
        container.setTitle(item.getName());
        container.setChildCount(item.getAlbumCount());

        return container;
    }

    @Override
    public List<Genre> getAllItems() {
        return getDispatcher().getMediaFileService().getGenres(false);
    }

    @Override
    public Genre getItemById(String id) {
        int index = Integer.parseInt(id);
        List<Genre> allGenres = getAllItems();
        if (allGenres.size() > index) {
            return allGenres.get(index);
        }
        return null;
    }

    @Override
    public List<MediaFile> getChildren(Genre item) {
        List<MusicFolder> allFolders = getDispatcher().getMediaFolderService().getAllMusicFolders();
        return getDispatcher().getMediaFileProcessor().getMediaFileDao().getSongsByGenre(item.getName(), 0, Integer.MAX_VALUE, allFolders);
    }

    @Override
    public void addChild(DIDLContent didl, MediaFile child) {
        didl.addItem(getDispatcher().getMediaFileProcessor().createItem(child));
    }
}
