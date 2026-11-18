package org.airsonic.player.service.upnp;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.service.PlaylistService;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.PlaylistContainer;

import java.util.List;

/**
 * @author Allen Petersen
 * @version $Id$
 */
public class PlaylistUpnpProcessor extends UpnpContentProcessor <Playlist, MediaFile> {
    private PlaylistService playlistService;

    public PlaylistUpnpProcessor() {
        setRootId(DispatchingContentDirectory.CONTAINER_ID_PLAYLIST_PREFIX);
        setRootTitle("Playlists");
    }

    public Container createContainer(Playlist item) {
        PlaylistContainer container = new PlaylistContainer();
        container.setId(getRootId() + DispatchingContentDirectory.SEPARATOR + item.getId());
        container.setParentID(getRootId());
        container.setTitle(item.getName());
        container.setDescription(item.getComment());
        container.setChildCount(getPlaylistService().getFilesInPlaylist(item.getId()).size());

        return container;
    }

    public List<Playlist> getAllItems() {
        return getPlaylistService().getAllPlaylists();
    }

    public Playlist getItemById(String id) {
        return getDispatcher().getPlaylistService().getPlaylist(Integer.parseInt(id));
    }

    public List<MediaFile> getChildren(Playlist item) {
        return getPlaylistService().getFilesInPlaylist(item.getId());
    }

    public void addChild(DIDLContent didl, MediaFile child) {
        didl.addItem(getDispatchingContentDirectory().createItem(child));
    }

    public PlaylistService getPlaylistService() {
        return this.playlistService;
    }
    public void setPlaylistService(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

}
