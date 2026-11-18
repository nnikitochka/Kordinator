package org.airsonic.player.service;

import org.airsonic.player.ajax.media.MediaFileEntry;
import org.airsonic.player.ajax.queue.PlayQueueInfo;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.PlayQueueDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.PlayQueue.RepeatStatus;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.domain.SavedPlayQueue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;


public class PlayQueueService {
    /** Selects where the web-player start instruction is delivered. */
    public record PlaybackTarget(String sessionId, boolean returnToCaller) {
        public static PlaybackTarget response() {
            return new PlaybackTarget(null, true);
        }
    }

    private final java.util.concurrent.atomic.AtomicLong queueRevision = new java.util.concurrent.atomic.AtomicLong();
    private final JukeboxService jukeboxService;
    private final SettingsService settingsService;
    private final MediaFileService mediaFileService;
    private final LastFmService lastFmService;
    private final SearchService searchService;
    private final RatingService ratingService;
    private final PlaylistService playlistService;
    private final MediaFileDao mediaFileDao;
    private final MediaFolderService mediaFolderService;
    private final PlayQueueDao playQueueDao;
    private final JWTSecurityService jwtSecurityService;

    public PlayQueueService(SearchService searchService, JukeboxService jukeboxService, SettingsService settingsService, MediaFileService mediaFileService, LastFmService lastFmService, RatingService ratingService, PlaylistService playlistService, MediaFileDao mediaFileDao, MediaFolderService mediaFolderService, PlayQueueDao playQueueDao, JWTSecurityService jwtSecurityService) {
        this.searchService = searchService;
        this.jukeboxService = jukeboxService;
        this.settingsService = settingsService;
        this.mediaFileService = mediaFileService;
        this.lastFmService = lastFmService;
        this.ratingService = ratingService;
        this.playlistService = playlistService;
        this.mediaFileDao = mediaFileDao;
        this.mediaFolderService = mediaFolderService;
        this.playQueueDao = playQueueDao;
        this.jwtSecurityService = jwtSecurityService;
    }

    public void start(Player player) {
        player.getPlayQueue().setStatus(PlayQueue.Status.PLAYING);
        if (player.isJukebox()) {
            jukeboxService.start(player);
        }
    }

    public void stop(Player player) {
        player.getPlayQueue().setStatus(PlayQueue.Status.STOPPED);
        if (player.isJukebox()) {
            jukeboxService.stop(player);
        }
    }

    public void toggleStartStop(Player player) {
        if (player.getPlayQueue().getStatus() == PlayQueue.Status.STOPPED) {
            start(player);
        } else {
            stop(player);
        }
    }

    public void skip(Player player, int index, long offset) {
        player.getPlayQueue().setIndex(index);
        if (player.isJukebox()) {
            jukeboxService.skip(player, index, (int) (offset / 1000));
        }

    }

    public PlayQueueInfo reloadSearchCriteria(Player player, PlaybackTarget target) {
        PlayQueue playQueue = player.getPlayQueue();
        int size = playQueue.size();
        if (playQueue.getRandomSearchCriteria() != null) {
            playQueue.addFiles(true, mediaFileService.getRandomSongs(playQueue.getRandomSearchCriteria(), player.getUsername()));
        }

        return broadcastPlayQueue(player, pq -> pq.setStartPlayerAt(size), target);
    }

    public int savePlayQueue(Player player, int index, long offset) {
        PlayQueue playQueue = player.getPlayQueue();
        List<Integer> ids = MediaFile.toIdList(playQueue.getFiles());

        Integer currentId = index == -1 ? null : playQueue.getFile(index).getId();
        SavedPlayQueue savedPlayQueue = new SavedPlayQueue(null, player.getUsername(), ids, currentId, offset,
                Instant.now(), player.getUsername());
        playQueueDao.savePlayQueue(savedPlayQueue);

        return savedPlayQueue.getId();
    }

    public PlayQueueInfo loadSavedPlayQueue(Player player, PlaybackTarget target) {
        SavedPlayQueue savedPlayQueue = playQueueDao.getPlayQueue(player.getUsername());

        if (savedPlayQueue == null) {
            return null;
        }

        PlayQueue playQueue = player.getPlayQueue();
        playQueue.clear();
        for (Integer mediaFileId : savedPlayQueue.getMediaFileIds()) {
            MediaFile mediaFile = mediaFileService.getMediaFile(mediaFileId);
            if (mediaFile != null) {
                playQueue.addFiles(true, mediaFile);
            }
        }

        long positionMillis = savedPlayQueue.getPositionMillis() == null ? 0L : savedPlayQueue.getPositionMillis();
        Integer currentId = savedPlayQueue.getCurrentMediaFileId();
        int currentIndex = Optional.ofNullable(currentId).map(mediaFileService::getMediaFile)
                .map(c -> playQueue.getFiles().indexOf(c)).orElse(-1);

        return broadcastPlayQueue(player,
                currentIndex == -1 ? identity : pq -> pq.setStartPlayerAt(currentIndex).setStartPlayerAtPosition(positionMillis),
                target);
    }

    public PlayQueueInfo playMediaFile(Player player, int id, PlaybackTarget target) {
        MediaFile file = mediaFileService.getMediaFile(id);

        List<MediaFile> songs;

        if (file.isFile()) {
            boolean queueFollowingSongs = settingsService.getUserSettings(player.getUsername()).getQueueFollowingSongs();
            if (queueFollowingSongs) {
                MediaFile dir = mediaFileService.getParentOf(file);
                songs = mediaFileService.getChildrenOf(dir, true, false, true);
                if (!songs.isEmpty()) {
                    int index = songs.indexOf(file);
                    songs = songs.subList(index, songs.size());
                }
            } else {
                songs = List.of(file);
            }
        } else {
            songs = mediaFileService.getDescendantsOf(file, true);
        }

        return doPlay(player, songs, target);
    }

    /**
     * @param index Start playing at this index, or play whole playlist if {@code null}.
     */
    public PlayQueueInfo playPlaylist(Player player, int id, Integer index, PlaybackTarget target) {
        boolean queueFollowingSongs = settingsService.getUserSettings(player.getUsername()).getQueueFollowingSongs();

        List<MediaFile> files = playlistService.getFilesInPlaylist(id, true);
        if (!files.isEmpty() && index != null) {
            if (queueFollowingSongs) {
                files = files.subList(index, files.size());
            } else {
                files = Collections.singletonList(files.get(index));
            }
        }

        // Remove non-present files
        files = new ArrayList<>(files);
        files.removeIf(file -> !file.isPresent());
        return doPlay(player, files, target);
    }

    /**
     * @param index Start playing at this index, or play all top songs if {@code null}.
     */
    public PlayQueueInfo playTopSong(Player player, int id, Integer index, PlaybackTarget target) {
        boolean queueFollowingSongs = settingsService.getUserSettings(player.getUsername()).getQueueFollowingSongs();

        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(player.getUsername());
        List<MediaFile> files = lastFmService.getTopSongs(mediaFileService.getMediaFile(id), 50, musicFolders);
        if (!files.isEmpty() && index != null) {
            if (queueFollowingSongs) {
                files = files.subList(index, files.size());
            } else {
                files = Collections.singletonList(files.get(index));
            }
        }

        return doPlay(player, files, target);
    }

    public PlayQueueInfo playStarred(Player player, PlaybackTarget target) {
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(player.getUsername());
        List<MediaFile> files = mediaFileDao.getStarredFiles(0, Integer.MAX_VALUE, player.getUsername(), musicFolders);
        return doPlay(player, files, target);
    }

    public PlayQueueInfo playShuffle(Player player, String albumListType, int offset, int count, String genre, String decade, PlaybackTarget target) {
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(player.getUsername(),
                settingsService.getUserSettings(player.getUsername()).getSelectedMusicFolderId());
        List<MediaFile> albums = switch (albumListType) {
            case "highest" -> ratingService.getHighestRatedAlbums(offset, count, musicFolders);
            case "frequent" -> mediaFileService.getMostFrequentlyPlayedAlbums(offset, count, musicFolders);
            case "recent" -> mediaFileService.getMostRecentlyPlayedAlbums(offset, count, musicFolders);
            case "newest" -> mediaFileService.getNewestAlbums(offset, count, musicFolders);
            case "starred" -> mediaFileService.getStarredAlbums(offset, count, player.getUsername(), musicFolders);
            case "random" -> searchService.getRandomAlbums(count, musicFolders);
            case "alphabetical" -> mediaFileService.getAlphabeticalAlbums(offset, count, true, musicFolders);
            case "decade" -> {
                int fromYear = Integer.parseInt(decade);
                int toYear = fromYear + 9;
                yield mediaFileService.getAlbumsByYear(offset, count, fromYear, toYear, musicFolders);
            }
            case "genre" -> mediaFileService.getAlbumsByGenre(offset, count, genre, musicFolders);
            case null, default -> Collections.emptyList();
        };

        List<MediaFile> songs = new ArrayList<>();
        for (MediaFile album : albums) {
            songs.addAll(mediaFileService.getChildrenOf(album, true, false, false));
        }
        Collections.shuffle(songs);
        songs = songs.subList(0, Math.min(40, songs.size()));

        return doPlay(player, songs, target);
    }

    public PlayQueueInfo playRandom(Player player, int id, int count, PlaybackTarget target) {
        MediaFile file = mediaFileService.getMediaFile(id);
        List<MediaFile> randomFiles = mediaFileService.getRandomSongsForParent(file, count);

        return doPlay(player, randomFiles, target);
    }

    public PlayQueueInfo playSimilar(Player player, int id, int count, PlaybackTarget target) {
        MediaFile artist = mediaFileService.getMediaFile(id);
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(player.getUsername());
        List<MediaFile> similarSongs = lastFmService.getSimilarSongs(artist, count, musicFolders);

        return doPlay(player, similarSongs, target);
    }

    private PlayQueueInfo doPlay(Player player, List<MediaFile> files, PlaybackTarget target) {
        if (player.isWeb()) {
            files = new ArrayList<>(files);
            mediaFileService.removeVideoFiles(files);
        }
        player.getPlayQueue().addFiles(false, files);
        player.getPlayQueue().setRandomSearchCriteria(null);
        return broadcastPlayQueue(player, startAt0, target);
    }

    public void add(Player player, List<Integer> ids, Integer index, boolean removeVideoFiles, boolean broadcast) {
        PlayQueue playQueue = player.getPlayQueue();
        List<MediaFile> files = new ArrayList<>();
        for (int id : ids) {
            MediaFile ancestor = mediaFileService.getMediaFile(id);
            files.addAll(mediaFileService.getDescendantsOf(ancestor, true));
        }
        if (removeVideoFiles) {
            mediaFileService.removeVideoFiles(files);
        }
        if (index != null) {
            playQueue.addFilesAt(files, index);
        } else {
            playQueue.addFiles(true, files);
        }
        playQueue.setRandomSearchCriteria(null);
    }

    public void addRandomCriteria(Player player, boolean append, RandomSearchCriteria criteria, boolean autoRandom) {
        player.getPlayQueue().addFiles(append, mediaFileService.getRandomSongs(criteria, player.getUsername()));
        player.getPlayQueue().setRandomSearchCriteria(autoRandom ? criteria : null);
    }

    public void addPlaylist(Player player, int id, boolean removeVideoFiles) {
        PlayQueue playQueue = player.getPlayQueue();

        List<MediaFile> files = playlistService.getFilesInPlaylist(id);
        if (removeVideoFiles) {
            mediaFileService.removeVideoFiles(files);
        }

        playQueue.addFiles(true, files);

        playQueue.setRandomSearchCriteria(null);
    }

    public void reset(Player player, List<Integer> ids, boolean removeVideoFiles) {
        PlayQueue playQueue = player.getPlayQueue();
        MediaFile currentFile = playQueue.getCurrentFile();
        PlayQueue.Status status = playQueue.getStatus();

        playQueue.clear();
        add(player, ids, null, removeVideoFiles, false);

        int index = currentFile == null ? -1 : playQueue.getFiles().indexOf(currentFile);
        playQueue.setIndex(index);
        playQueue.setStatus(status);
    }

    public void clear(Player player) {
        player.getPlayQueue().clear();
    }

    public void shuffle(Player player) {
        player.getPlayQueue().shuffle();
    }

    public void remove(Player player, List<Integer> indexes) {
        Collections.sort(indexes);

        for (int i = indexes.size() - 1; i >= 0; i--) {
            player.getPlayQueue().removeFileAt(indexes.get(i));
        }
    }

    public void rearrange(Player player, List<Integer> indexes) {
        player.getPlayQueue().rearrange(indexes);
    }

    public void up(Player player, int index) {
        player.getPlayQueue().moveUp(index);
    }

    public void down(Player player, int index) {
        player.getPlayQueue().moveDown(index);
    }

    public void toggleRepeat(Player player) {
        PlayQueue playQueue = player.getPlayQueue();
        if (playQueue.isShuffleRadioEnabled()) {
            playQueue.setRandomSearchCriteria(null);
            playQueue.setRepeatStatus(RepeatStatus.OFF);
        } else {
            playQueue.setRepeatStatus(RepeatStatus.getNext(playQueue.getRepeatStatus()));
        }
    }

    public void undo(Player player) {
        player.getPlayQueue().undo();
    }

    public void sort(Player player, PlayQueue.SortOrder order) {
        player.getPlayQueue().sort(order);
    }

    private static final Function<PlayQueueInfo, PlayQueueInfo> identity = pq -> pq;
    private static final Function<PlayQueueInfo, PlayQueueInfo> startAt0 = pq -> pq.setStartPlayerAt(0);

    //
    // Methods dedicated to jukebox
    //
    public void setJukeboxGain(Player player, float gain) {
        jukeboxService.setGain(player, gain);
    }

    public void setJukeboxPosition(Player player, int positionInSeconds) {
        jukeboxService.setPosition(player, positionInSeconds);
    }

    //
    // End : Methods dedicated to jukebox
    //

    private PlayQueueInfo broadcastPlayQueue(Player player, Function<PlayQueueInfo, PlayQueueInfo> modifier, PlaybackTarget target) {
        PlayQueueInfo info = modifier.apply(getPlayQueueInfo(player));
        if (info.getStartPlayerAt() != -1 && !player.isWeb() && !player.isExternalWithPlaylist()) {
            skip(player, info.getStartPlayerAt(), info.getStartPlayerAtPosition());
        }
        return info;
    }

    public PlayQueueInfo getPlayQueueInfo(Player player) {
        return getPlayQueueInfo(player, "");
    }

    public PlayQueueInfo getPlayQueueInfo(Player player, String basePath) {
        PlayQueue playQueue = player.getPlayQueue();

        List<MediaFileEntry> entries = convertMediaFileList(player, basePath);

        float gain = jukeboxService.getGain(player);

        PlayQueueInfo info = new PlayQueueInfo(entries, playQueue.getStatus(), playQueue.getRepeatStatus(), playQueue.isShuffleRadioEnabled(), gain);
        info.setRevision(queueRevision.incrementAndGet());
        return info;
    }

    private List<MediaFileEntry> convertMediaFileList(Player player, String basePath) {
        Function<MediaFile, String> streamUrlGenerator = file -> basePath + "stream?player=" + player.getId() + "&id=" + file.getId();
        Function<MediaFile, String> remoteStreamUrlGenerator = file -> basePath + jwtSecurityService
                .addJWTToken(player.getUsername(), "ext/stream?player=" + player.getId() + "&id=" + file.getId());
        Function<MediaFile, String> remoteCoverArtUrlGenerator = file -> basePath
                + jwtSecurityService.addJWTToken(player.getUsername(), "ext/coverArt.view?id=" + file.getId());
        return mediaFileService.toMediaFileEntryList(player.getPlayQueue().getFiles(), player.getUsername(), true, true,
                streamUrlGenerator, remoteStreamUrlGenerator, remoteCoverArtUrlGenerator);
    }
}
