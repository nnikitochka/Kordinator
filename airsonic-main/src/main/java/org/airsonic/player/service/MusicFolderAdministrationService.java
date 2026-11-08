package org.airsonic.player.service;

import org.airsonic.player.command.MusicFolderSettingsCommand;
import org.airsonic.player.command.MusicFolderSettingsCommand.MusicFolderInfo;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaLibraryStatistics;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.search.IndexManager;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Library administration operations shared by REST clients.
 */
public class MusicFolderAdministrationService {
    private static final Logger LOG = LoggerFactory.getLogger(MusicFolderAdministrationService.class);

    private final SettingsService settingsService;
    private final MediaScannerService mediaScannerService;
    private final MediaFolderService mediaFolderService;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final MediaFileDao mediaFileDao;
    private final IndexManager indexManager;
    private final PlaylistService playlistService;
    private final CoverArtService coverArtService;

    public MusicFolderAdministrationService(SettingsService settingsService, MediaScannerService mediaScannerService, MediaFolderService mediaFolderService, ArtistDao artistDao, AlbumDao albumDao, MediaFileDao mediaFileDao, IndexManager indexManager, PlaylistService playlistService, CoverArtService coverArtService) {
        this.settingsService = settingsService;
        this.mediaScannerService = mediaScannerService;
        this.mediaFolderService = mediaFolderService;
        this.artistDao = artistDao;
        this.albumDao = albumDao;
        this.mediaFileDao = mediaFileDao;
        this.indexManager = indexManager;
        this.playlistService = playlistService;
        this.coverArtService = coverArtService;
    }

    public MusicFolderSettingsCommand getSettings() {
        MusicFolderSettingsCommand command = new MusicFolderSettingsCommand();

        command.setInterval(String.valueOf(settingsService.getIndexCreationInterval()));
        command.setHour(String.valueOf(settingsService.getIndexCreationHour()));
        command.setFastCache(settingsService.isFastCacheEnabled());
        command.setOrganizeByFolderStructure(settingsService.isOrganizeByFolderStructure());
        command.setScanning(mediaScannerService.isScanning());
        command.setMusicFolders(wrap(mediaFolderService.getAllMusicFolders(true, true)));
        command.setNewMusicFolder(new MusicFolderSettingsCommand.MusicFolderInfo());
        command.setDeletedMusicFolders(wrap(mediaFolderService.getDeletedMusicFolders()));
        command.setUploadsFolder(settingsService.getUploadsFolder());
        command.setExcludePatternString(settingsService.getExcludePatternString());
        command.setIgnoreSymLinks(settingsService.getIgnoreSymLinks());
        command.setFullScan(settingsService.getFullScan());
        command.setClearFullScanSettingAfterScan(!settingsService.getFullScan() ? settingsService.getFullScan() : settingsService.getClearFullScanSettingAfterScan());

        return command;
    }

    public void expunge() {
        // to be before dao#expunge
        MediaLibraryStatistics statistics = indexManager.getStatistics();
        if (statistics != null) {
            LOG.debug("Cleaning search index...");
            indexManager.startIndexing();
            try {
                indexManager.expunge();
            } finally {
                indexManager.stopIndexing(statistics);
            }
            LOG.debug("Search index cleanup complete.");
        } else {
            LOG.warn("Missing index statistics - index probably hasn't been created yet. Not expunging index.");
        }

        LOG.debug("Cleaning database...");
        LOG.debug("Deleting non-present artists...");
        artistDao.expunge();
        LOG.debug("Deleting non-present albums...");
        albumDao.expunge();
        LOG.debug("Deleting non-present media files...");
        mediaFileDao.expunge();
        LOG.debug("Deleting non-present cover art...");
        coverArtService.expunge();
        LOG.debug("Deleting non-present media folders...");
        mediaFolderService.expunge();
        LOG.debug("Refreshing playlist stats...");
        playlistService.refreshPlaylistsStats();
        LOG.debug("Database cleanup complete.");
    }

    private List<MusicFolderSettingsCommand.MusicFolderInfo> wrap(List<MusicFolder> musicFolders) {
        return musicFolders.stream().map(f -> {
            Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> overlaps = MediaFolderService.getMusicFolderPathOverlaps(f, mediaFolderService.getAllMusicFolders(true, true));
            return new MusicFolderSettingsCommand.MusicFolderInfo(f, !overlaps.getLeft().isEmpty() || !overlaps.getMiddle().isEmpty() || !overlaps.getRight().isEmpty(), MediaFolderService.logMusicFolderOverlap(overlaps));
        }).sorted(Comparator.comparing(MusicFolderInfo::getType).thenComparing(MusicFolderInfo::getId)).collect(toList());
    }

    public void save(MusicFolderSettingsCommand command) {
        for (MusicFolderSettingsCommand.MusicFolderInfo musicFolderInfo : command.getMusicFolders()) {
            if (musicFolderInfo.getDelete()) {
                mediaFolderService.deleteMusicFolder(musicFolderInfo.getId());
            } else {
                MusicFolder musicFolder = musicFolderInfo.toMusicFolder();
                if (musicFolder != null) {
                    mediaFolderService.updateMusicFolder(musicFolder);
                }
            }
        }
        MusicFolder newMusicFolder = command.getNewMusicFolder().toMusicFolder();
        if (newMusicFolder != null) {
            mediaFolderService.createMusicFolder(newMusicFolder);
        }

        settingsService.setIndexCreationInterval(Integer.parseInt(command.getInterval()));
        settingsService.setIndexCreationHour(Integer.parseInt(command.getHour()));
        settingsService.setFastCacheEnabled(command.isFastCache());
        settingsService.setOrganizeByFolderStructure(command.isOrganizeByFolderStructure());
        settingsService.setUploadsFolder(command.getUploadsFolder());
        settingsService.setExcludePatternString(command.getExcludePatternString());
        settingsService.setIgnoreSymLinks(command.getIgnoreSymLinks());
        settingsService.setFullScan(command.getFullScan());
        settingsService.setClearFullScanSettingAfterScan(!command.getFullScan() ? command.getFullScan() : command.getClearFullScanSettingAfterScan());
        settingsService.save();

        mediaScannerService.schedule();

    }

}
