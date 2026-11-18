package org.airsonic.player.service;

import com.google.common.io.MoreFiles;
import org.airsonic.player.ajax.media.MediaFileEntry;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.i18n.LocaleService;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.airsonic.player.util.FileUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.CacheManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides services for instantiating and caching media files and cover art.
 *
 * @author Sindre Mehus
 */
public class MediaFileService {
    private static final Logger LOG = LoggerFactory.getLogger(MediaFileService.class);

    private SecurityService securityService;
    private SettingsService settingsService;
    private MediaFileDao mediaFileDao;
    private final MediaFolderService mediaFolderService;
    private AlbumDao albumDao;
    private JaudiotaggerParser parser;
    private MetaDataParserFactory metaDataParserFactory;
    private final CoverArtService coverArtService;
    private final LocaleService localeService;
    private final Cache<String, MediaFile> mediaFilePathCache;
    private final Cache<Integer, MediaFile> mediaFileIdCache;
    private boolean memoryCacheEnabled = true;

    public MediaFileService(SecurityService securityService, SettingsService settingsService, MediaFileDao mediaFileDao, MediaFolderService mediaFolderService, AlbumDao albumDao, JaudiotaggerParser parser, MetaDataParserFactory metaDataParserFactory, CoverArtService coverArtService, LocaleService localeService) {
        this(securityService, settingsService, mediaFileDao, mediaFolderService, albumDao, parser, metaDataParserFactory, coverArtService, localeService, null);
    }

    public MediaFileService(SecurityService securityService, SettingsService settingsService, MediaFileDao mediaFileDao, MediaFolderService mediaFolderService, AlbumDao albumDao, JaudiotaggerParser parser, MetaDataParserFactory metaDataParserFactory, CoverArtService coverArtService, LocaleService localeService,
                            CacheManager cacheManager) {
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.mediaFileDao = mediaFileDao;
        this.mediaFolderService = mediaFolderService;
        this.albumDao = albumDao;
        this.parser = parser;
        this.metaDataParserFactory = metaDataParserFactory;
        this.coverArtService = coverArtService;
        this.localeService = localeService;
        this.mediaFilePathCache = cacheManager != null ? cacheManager.getCache("mediaFilePathCache", String.class, MediaFile.class) : null;
        this.mediaFileIdCache = cacheManager != null ? cacheManager.getCache("mediaFileIdCache", Integer.class, MediaFile.class) : null;
    }

    public MediaFile getMediaFile(String pathName) {
        return getMediaFile(Paths.get(pathName));
    }

    public MediaFile getMediaFile(Path fullPath) {
        return getMediaFile(fullPath, settingsService.isFastCacheEnabled());
    }

    // This may be an expensive op
    public MediaFile getMediaFile(Path fullPath, boolean minimizeDiskAccess) {
        MusicFolder folder = securityService.getMusicFolderForFile(fullPath, true, true);
        if (folder == null) {
            // can't look outside folders and not present in folder
            return null;
        }
        try {
            Path relativePath = folder.getPath().relativize(fullPath);
            return getMediaFile(relativePath, folder, minimizeDiskAccess);
        } catch (Exception e) {
            // ignore
            return null;
        }
    }

    public MediaFile getMediaFile(String relativePath, Integer folderId) {
        return getMediaFile(relativePath, mediaFolderService.getMusicFolderById(folderId));
    }

    public MediaFile getMediaFile(String relativePath, MusicFolder folder) {
        return getMediaFile(Paths.get(relativePath), folder);
    }

    public MediaFile getMediaFile(Path relativePath, MusicFolder folder) {
        return getMediaFile(relativePath, folder, settingsService.isFastCacheEnabled());
    }

    public MediaFile getMediaFile(String relativePath, Integer folderId, boolean minimizeDiskAccess) {
        return getMediaFile(Paths.get(relativePath), mediaFolderService.getMusicFolderById(folderId), minimizeDiskAccess);
    }

    public MediaFile getMediaFile(Path relativePath, MusicFolder folder, boolean minimizeDiskAccess) {
        String key = relativePath.toString() + "-" + folder.getId();
        if (memoryCacheEnabled && mediaFilePathCache != null) {
            MediaFile cached = mediaFilePathCache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        // Look in database.
        MediaFile result = mediaFileDao.getMediaFile(relativePath.toString(), folder.getId());
        if (result != null) {
            result = checkLastModified(result, folder, minimizeDiskAccess);
            if (result != null && memoryCacheEnabled && mediaFilePathCache != null) {
                mediaFilePathCache.put(key, result);
            }
            return result;
        }

        if (!Files.exists(folder.getPath().resolve(relativePath))) {
            return null;
        }

        // Not found in database, must read from disk.
        result = createMediaFile(relativePath, folder, null);

        // Put in database.
        updateMediaFile(result);

        if (result != null && memoryCacheEnabled && mediaFilePathCache != null) {
            mediaFilePathCache.put(key, result);
        }

        return result;
    }

    public MediaFile getMediaFile(int id) {
        if (memoryCacheEnabled && mediaFileIdCache != null) {
            MediaFile cached = mediaFileIdCache.get(id);
            if (cached != null) {
                return cached;
            }
        }
        MediaFile mediaFile = mediaFileDao.getMediaFile(id);
        if (mediaFile == null) {
            return null;
        }

        MediaFile result = checkLastModified(mediaFile, mediaFolderService.getMusicFolderById(mediaFile.getFolderId()), settingsService.isFastCacheEnabled());
        if (result != null && memoryCacheEnabled && mediaFileIdCache != null) {
            mediaFileIdCache.put(id, result);
        }
        return result;
    }

    public List<MediaFile> getMediaFilesByRelativePath(Path relativePath) {
        return mediaFileDao.getMediaFilesByRelativePath(relativePath.toString());
    }

    public MediaFile getParentOf(MediaFile mediaFile) {
        return getParentOf(mediaFile, settingsService.isFastCacheEnabled());
    }

    public MediaFile getParentOf(MediaFile mediaFile, boolean minimizeDiskAccess) {
        if (mediaFile.getParentPath() == null) {
            return null;
        }
        return getMediaFile(mediaFile.getParentPath(), mediaFile.getFolderId(), minimizeDiskAccess);
    }

    private MediaFile checkLastModified(MediaFile mediaFile, MusicFolder folder, boolean minimizeDiskAccess) {
        if (minimizeDiskAccess) {
            return mediaFile;
        }

        // If the file has disappeared from disk, mark the db row absent immediately instead of
        // going through FileUtil.lastModified (which pollutes the log with a stacktrace on every
        // access). The row is then expunged at the next scan.
        if (Files.notExists(mediaFile.getFullPath(folder.getPath()))) {
            LOG.info("Marking media file (id {}, path {} in folder {}) as absent since it no longer exists on disk",
                    mediaFile.getId(), mediaFile.getPath(), folder.getName());
            mediaFile.setPresent(false);
            mediaFile.setChildrenLastUpdated(Instant.ofEpochMilli(1));
            updateMediaFile(mediaFile);
            return mediaFile;
        }

        if (mediaFile.getVersion() >= MediaFileDao.VERSION
                && !settingsService.getFullScan()
                && mediaFile.getChanged().compareTo(FileUtil.lastModified(mediaFile.getFullPath(folder.getPath()))) > -1) {
            LOG.debug("Detected unmodified file (id {}, path {} in folder {} ({}))", mediaFile.getId(), mediaFile.getPath(), folder.getId(), folder.getName());
            return mediaFile;
        }
        LOG.debug("Updating database file from disk (id {}, path {} in folder {} ({}))", mediaFile.getId(), mediaFile.getPath(), folder.getId(), folder.getName());
        mediaFile = createMediaFile(mediaFile.getRelativePath(), folder, mediaFile);
        updateMediaFile(mediaFile);
        return mediaFile;
    }

    /**
     * Returns all media files that are children of a given media file.
     *
     * @param includeFiles       Whether files should be included in the result.
     * @param includeDirectories Whether directories should be included in the result.
     * @param sort               Whether to sort files in the same directory.
     * @return All children media files.
     */
    public List<MediaFile> getChildrenOf(MediaFile parent, boolean includeFiles, boolean includeDirectories, boolean sort) {
        return getChildrenOf(parent, includeFiles, includeDirectories, sort, settingsService.isFastCacheEnabled());
    }

    /**
     * Returns all media files that are children of a given media file.
     *
     * @param includeFiles       Whether files should be included in the result.
     * @param includeDirectories Whether directories should be included in the result.
     * @param sort               Whether to sort files in the same directory.
     * @return All children media files.
     */
    public List<MediaFile> getChildrenOf(MediaFile parent, boolean includeFiles, boolean includeDirectories, boolean sort, boolean minimizeDiskAccess) {

        if (!parent.isDirectory()) {
            return Collections.emptyList();
        }

        Stream<MediaFile> resultStream = null;

        // Make sure children are stored and up-to-date in the database.
        if (!minimizeDiskAccess) {
            resultStream = Optional.ofNullable(updateChildren(parent)).map(Collection::parallelStream).orElse(null);
        }

        if (resultStream == null) {
            MusicFolder folder = mediaFolderService.getMusicFolderById(parent.getFolderId());
            resultStream = mediaFileDao.getChildrenOf(parent.getPath(), parent.getFolderId(), true).parallelStream()
                    .map(x -> checkLastModified(x, folder, minimizeDiskAccess))
                    .filter(x -> includeMediaFile(x, folder));
        }

        resultStream = resultStream.filter(x -> (includeDirectories && x.isDirectory()) || (includeFiles && x.isFile()));

        if (sort) {
            resultStream = resultStream.sorted(new MediaFileComparator(settingsService.isSortAlbumsByYear()));
        }

        return resultStream.collect(Collectors.toList());
    }

    /**
     * Returns whether the given file is the root of a media folder.
     *
     * @see MusicFolder
     */
    public boolean isRoot(MediaFile mediaFile) {
        return StringUtils.isEmpty(mediaFile.getPath()) &&
                mediaFolderService.getAllMusicFolders(true, true).parallelStream()
                        .anyMatch(x -> mediaFile.getFolderId().equals(x.getId()));
    }

    /**
     * Returns all genres in the music collection.
     *
     * @param sortByAlbum Whether to sort by album count, rather than song count.
     * @return Sorted list of genres.
     */
    public List<Genre> getGenres(boolean sortByAlbum) {
        return mediaFileDao.getGenres(sortByAlbum);
    }

    /**
     * Returns the most frequently played albums.
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param musicFolders Only return albums in these folders.
     * @return The most frequently played albums.
     */
    public List<MediaFile> getMostFrequentlyPlayedAlbums(int offset, int count, List<MusicFolder> musicFolders) {
        return mediaFileDao.getMostFrequentlyPlayedAlbums(offset, count, musicFolders);
    }

    /**
     * Returns the most recently played albums.
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param musicFolders Only return albums in these folders.
     * @return The most recently played albums.
     */
    public List<MediaFile> getMostRecentlyPlayedAlbums(int offset, int count, List<MusicFolder> musicFolders) {
        return mediaFileDao.getMostRecentlyPlayedAlbums(offset, count, musicFolders);
    }

    /**
     * Returns the most recently added albums.
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param musicFolders Only return albums in these folders.
     * @return The most recently added albums.
     */
    public List<MediaFile> getNewestAlbums(int offset, int count, List<MusicFolder> musicFolders) {
        return mediaFileDao.getNewestAlbums(offset, count, musicFolders);
    }

    /**
     * Returns the most recently starred albums.
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param username     Returns albums starred by this user.
     * @param musicFolders Only return albums from these folders.
     * @return The most recently starred albums for this user.
     */
    public List<MediaFile> getStarredAlbums(int offset, int count, String username, List<MusicFolder> musicFolders) {
        return mediaFileDao.getStarredAlbums(offset, count, username, musicFolders);
    }

    /**
     * Returns albums in alphabetical order.
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param byArtist     Whether to sort by artist name
     * @param musicFolders Only return albums in these folders.
     * @return Albums in alphabetical order.
     */
    public List<MediaFile> getAlphabeticalAlbums(int offset, int count, boolean byArtist, List<MusicFolder> musicFolders) {
        return mediaFileDao.getAlphabeticalAlbums(offset, count, byArtist, musicFolders);
    }

    /**
     * Returns albums within a year range.
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param fromYear     The first year in the range.
     * @param toYear       The last year in the range.
     * @param musicFolders Only return albums in these folders.
     * @return Albums in the year range.
     */
    public List<MediaFile> getAlbumsByYear(int offset, int count, int fromYear, int toYear, List<MusicFolder> musicFolders) {
        return mediaFileDao.getAlbumsByYear(offset, count, fromYear, toYear, musicFolders);
    }

    /**
     * Returns albums in a genre.
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param genre        The genre name.
     * @param musicFolders Only return albums in these folders.
     * @return Albums in the genre.
     */
    public List<MediaFile> getAlbumsByGenre(int offset, int count, String genre, List<MusicFolder> musicFolders) {
        return mediaFileDao.getAlbumsByGenre(offset, count, genre, musicFolders);
    }

    /**
     * Returns random songs for the given parent.
     *
     * @param parent The parent.
     * @param count  Max number of songs to return.
     * @return Random songs.
     */
    public List<MediaFile> getRandomSongsForParent(MediaFile parent, int count) {
        List<MediaFile> children = getDescendantsOf(parent, false);
        removeVideoFiles(children);

        if (children.isEmpty()) {
            return children;
        }
        Collections.shuffle(children);
        return children.subList(0, Math.min(count, children.size()));
    }

    /**
     * Returns random songs matching search criteria.
     *
     */
    public List<MediaFile> getRandomSongs(RandomSearchCriteria criteria, String username) {
        return mediaFileDao.getRandomSongs(criteria, username);
    }

    /**
     * Removes video files from the given list.
     */
    public void removeVideoFiles(List<MediaFile> files) {
        files.removeIf(MediaFile::isVideo);
    }

    public Instant getMediaFileStarredDate(int id, String username) {
        return mediaFileDao.getMediaFileStarredDate(id, username);
    }

    public void populateStarredDate(List<MediaFile> mediaFiles, String username) {
        for (MediaFile mediaFile : mediaFiles) {
            populateStarredDate(mediaFile, username);
        }
    }

    public void populateStarredDate(MediaFile mediaFile, String username) {
        Instant starredDate = mediaFileDao.getMediaFileStarredDate(mediaFile.getId(), username);
        mediaFile.setStarredDate(starredDate);
    }

    private List<MediaFile> updateChildren(MediaFile parent) {
        // Check timestamps.
        if (parent.getChildrenLastUpdated().compareTo(parent.getChanged()) >= 0) {
            return null;
        }

        Map<String, MediaFile> storedChildrenMap = mediaFileDao.getChildrenOf(parent.getPath(), parent.getFolderId(), false).parallelStream().collect(Collectors.toConcurrentMap(MediaFile::getPath, i -> i));
        MusicFolder folder = mediaFolderService.getMusicFolderById(parent.getFolderId());
        try (Stream<Path> children = Files.list(parent.getFullPath(folder.getPath()))) {
            List<MediaFile> result = children.parallel()
                    .filter(this::includeMediaFile)
                    .filter(x -> securityService.getMusicFolderForFile(x, true, true).getId().equals(parent.getFolderId()))
                    .map(x -> folder.getPath().relativize(x))
                    .map(x -> {
                        MediaFile media = storedChildrenMap.remove(x.toString());
                        if (media == null) {
                            media = createMediaFile(x, folder, null);
                            // Add children that are not already stored.
                            updateMediaFile(media);
                        } else {
                            media = checkLastModified(media, folder, false); // has to be false, only time it's called
                        }

                        return media;
                    })
                    .collect(Collectors.toList());

            // Delete children that no longer exist on disk.
            mediaFileDao.deleteMediaFiles(storedChildrenMap.keySet(), parent.getFolderId());

            // Update timestamp in parent.
            parent.setChildrenLastUpdated(parent.getChanged());
            parent.setPresent(true);
            updateMediaFile(parent);

            return result;
        } catch (IOException e) {
            LOG.warn("Could not retrieve and update all the children for {} in folder {}. Will skip", parent.getPath(), folder.getId(), e);

            return null;
        }
    }

    public boolean includeMediaFile(MediaFile candidate, MusicFolder folder) {
        return includeMediaFile(candidate.getFullPath(folder.getPath()));
    }

    public boolean includeMediaFile(Path candidate) {
        String suffix = MoreFiles.getFileExtension(candidate).toLowerCase();
        return (!isExcluded(candidate) && (Files.isDirectory(candidate) || isAudioFile(suffix) || isVideoFile(suffix)));
    }

    private boolean isAudioFile(String suffix) {
        return settingsService.getMusicFileTypesSet().contains(suffix.toLowerCase());
    }

    private boolean isVideoFile(String suffix) {
        return settingsService.getVideoFileTypesSet().contains(suffix.toLowerCase());
    }

    /**
     * Returns whether the given file is excluded.
     *
     * @param file The child file in question.
     * @return Whether the child file is excluded.
     */
    private boolean isExcluded(Path file) {
        if (settingsService.getIgnoreSymLinks() && Files.isSymbolicLink(file)) {
            LOG.info("excluding symbolic link {}", file);
            return true;
        }
        String name = file.getFileName().toString();
        if (settingsService.getExcludePattern() != null && settingsService.getExcludePattern().matcher(name).find()) {
            LOG.info("excluding file which matches exclude pattern {}: {}", settingsService.getExcludePatternString(), file);
            return true;
        }

        // Exclude all hidden files starting with a single "." or "@eaDir" (thumbnail dir created on Synology devices).
        return (name.startsWith(".") && !name.startsWith("..")) || name.startsWith("@eaDir") || "Thumbs.db".equals(name);
    }

    private MediaFile createMediaFile(Path relativePath, MusicFolder folder, MediaFile existingFile) {
        Path file = folder.getPath().resolve(relativePath);
        if (!Files.exists(file)) {
            if (existingFile != null) {
                existingFile.setPresent(false);
                existingFile.setChildrenLastUpdated(Instant.ofEpochMilli(1));
            }
            return existingFile;
        }

        MediaFile mediaFile = new MediaFile();
        Instant lastModified = FileUtil.lastModified(file);
        mediaFile.setPath(relativePath.toString());
        mediaFile.setFolderId(folder.getId());
        //sanity check
        MusicFolder folderActual = securityService.getMusicFolderForFile(file, true, true);
        if (!folderActual.getId().equals(folder.getId())) {
            LOG.warn("Inconsistent Mediafile folder for media file with path: {}, folder id should be {} and is instead {}", file, folderActual.getId(), folder.getId());
        }
        // distinguish between null (no parent, like root folder), "" (root parent), and else
        String parentPath = null;
        if (StringUtils.isNotEmpty(relativePath.toString())) {
            parentPath = relativePath.getParent() == null ? "" : relativePath.getParent().toString();
        }
        mediaFile.setParentPath(parentPath);
        mediaFile.setChanged(lastModified);
        mediaFile.setLastScanned(Instant.now());
        mediaFile.setPlayCount(existingFile == null ? 0 : existingFile.getPlayCount());
        mediaFile.setLastPlayed(existingFile == null ? null : existingFile.getLastPlayed());
        mediaFile.setComment(existingFile == null ? null : existingFile.getComment());
        mediaFile.setChildrenLastUpdated(Instant.ofEpochMilli(1)); //distant past, can't use Instant.MIN due to HSQL incompatibility
        mediaFile.setCreated(lastModified);
        mediaFile.setMediaType(MediaFile.MediaType.DIRECTORY);
        mediaFile.setPresent(true);
        mediaFile.setId(existingFile == null ? null : existingFile.getId());

        if (Files.isRegularFile(file)) {

            MetaDataParser parser = metaDataParserFactory.getParser(file);
            if (parser != null) {
                MetaData metaData = parser.getMetaData(file);
                mediaFile.setArtist(metaData.getArtist());
                mediaFile.setAlbumArtist(metaData.getAlbumArtist());
                mediaFile.setAlbumName(metaData.getAlbumName());
                mediaFile.setTitle(metaData.getTitle());
                mediaFile.setDiscNumber(metaData.getDiscNumber());
                mediaFile.setTrackNumber(metaData.getTrackNumber());
                mediaFile.setGenre(metaData.getGenre());
                mediaFile.setYear(metaData.getYear());
                mediaFile.setDuration(metaData.getDuration());
                mediaFile.setBitRate(metaData.getBitRate());
                mediaFile.setVariableBitRate(metaData.getVariableBitRate());
                mediaFile.setHeight(metaData.getHeight());
                mediaFile.setWidth(metaData.getWidth());
                mediaFile.setMusicBrainzReleaseId(metaData.getMusicBrainzReleaseId());
                mediaFile.setMusicBrainzRecordingId(metaData.getMusicBrainzRecordingId());
            }
            String format = StringUtils.trimToNull(StringUtils.lowerCase(FilenameUtils.getExtension(mediaFile.getPath())));
            mediaFile.setFormat(format);
            mediaFile.setFileSize(FileUtil.size(file));
            mediaFile.setMediaType(getMediaType(mediaFile, folder));

        } else {

            // Is this an album?
            if (!isRoot(mediaFile)) {
                try (Stream<Path> stream = Files.list(file)) {
                    List<Path> children = stream.parallel().collect(Collectors.toList());
                    Path firstChild = children.parallelStream()
                            .filter(this::includeMediaFile)
                            .filter(Files::isRegularFile)
                            .findFirst().orElse(null);

                    if (firstChild != null) {
                        mediaFile.setMediaType(MediaFile.MediaType.ALBUM);

                        // Guess artist/album name, year and genre.
                        MetaDataParser parser = metaDataParserFactory.getParser(firstChild);
                        if (parser != null) {
                            MetaData metaData = parser.getMetaData(firstChild);
                            mediaFile.setArtist(metaData.getAlbumArtist());
                            mediaFile.setAlbumName(metaData.getAlbumName());
                            mediaFile.setYear(metaData.getYear());
                            mediaFile.setGenre(metaData.getGenre());
                        }

                        // Look for cover art.
                        Path coverArt = findCoverArt(children);
                        if (coverArt != null) {
                            // placeholder to be persisted later
                            mediaFile.setArt(new CoverArt(-1, EntityType.MEDIA_FILE, folder.getPath().relativize(coverArt).toString(), folder.getId(), false));
                        }
                    } else {
                        mediaFile.setArtist(file.getFileName().toString());
                    }

                } catch (IOException e) {
                    LOG.warn("Could not retrieve children for {}.", file, e);

                    mediaFile.setArtist(file.getFileName().toString());
                }
            } else {
                // root folders need to have a title
                mediaFile.setTitle(folder.getName());
            }
        }

        return mediaFile;
    }

    private MediaFile.MediaType getMediaType(MediaFile mediaFile, MusicFolder folder) {
        if (isVideoFile(mediaFile.getFormat())) {
            return MediaFile.MediaType.VIDEO;
        }
        String path = mediaFile.getPath().toLowerCase();
        String genre = StringUtils.trimToEmpty(mediaFile.getGenre()).toLowerCase();
        if (path.contains("audiobook") || genre.contains("audiobook") || path.contains("audio book") || genre.contains("audio book")) {
            return MediaFile.MediaType.AUDIOBOOK;
        }
        return MediaFile.MediaType.MUSIC;
    }

    public void refreshMediaFile(MediaFile mediaFile, MusicFolder folder) {
        mediaFile = createMediaFile(mediaFile.getRelativePath(), folder, mediaFile);
        updateMediaFile(mediaFile);
    }

    public void setMemoryCacheEnabled(boolean memoryCacheEnabled) {
        this.memoryCacheEnabled = memoryCacheEnabled;
        clearMemoryCache();
    }

    public void clearMemoryCache() {
        if (mediaFilePathCache != null) {
            mediaFilePathCache.removeAll();
        }
        if (mediaFileIdCache != null) {
            mediaFileIdCache.removeAll();
        }
    }

    public boolean getMemoryCacheEnabled() {
        return memoryCacheEnabled;
    }

    /**
     * Finds a cover art image for the given directory, by looking for it on the disk.
     */
    private Path findCoverArt(Collection<Path> candidates) {
        Path candidate;
        var coverArtSource = settingsService.getCoverArtSource();
        switch (coverArtSource) {
            case TAG_FILE:
                candidate = findTagCover(candidates);
                if (candidate != null) {
                    return candidate;
                } else {
                    return findFileCover(candidates);
                }
            case FILE:
                return findFileCover(candidates);
            case TAG:
                return findTagCover(candidates);
            case FILE_TAG:
            default:
                candidate = findFileCover(candidates);
                if (candidate != null) {
                    return candidate;
                } else {
                    return findTagCover(candidates);
                }
        }
    }

    private Path findFileCover(Collection<Path> candidates) {
        for (String mask : settingsService.getCoverArtFileTypesSet()) {
            Path cand = candidates.parallelStream().filter(c -> {
                String candidate = c.getFileName().toString().toLowerCase();
                return candidate.endsWith(mask) && !candidate.startsWith(".") && Files.isRegularFile(c);
            }).findAny().orElse(null);

            if (cand != null) {
                return cand;
            }
        }
        return null;
    }

    private Path findTagCover(Collection<Path> candidates) {
        // Look for embedded images in audiofiles.
        return candidates.stream()
                .filter(parser::isApplicable)
                .filter(JaudiotaggerParser::isImageAvailable)
                .findFirst()
                .orElse(null);
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setMediaFileDao(MediaFileDao mediaFileDao) {
        this.mediaFileDao = mediaFileDao;
    }

    /**
     * Returns all media files that are children, grand-children etc of a given media file.
     * Directories are not included in the result.
     *
     * @param sort Whether to sort files in the same directory.
     * @return All descendant music files.
     */
    public List<MediaFile> getDescendantsOf(MediaFile ancestor, boolean sort) {

        if (ancestor.isFile()) {
            return Arrays.asList(ancestor);
        }

        List<MediaFile> result = new ArrayList<>();

        for (MediaFile child : getChildrenOf(ancestor, true, true, sort)) {
            if (child.isDirectory()) {
                result.addAll(getDescendantsOf(child, sort));
            } else {
                result.add(child);
            }
        }
        return result;
    }

    public void setMetaDataParserFactory(MetaDataParserFactory metaDataParserFactory) {
        this.metaDataParserFactory = metaDataParserFactory;
    }

    public void updateMediaFile(MediaFile mediaFile) {
        if (mediaFile != null) {
            if (mediaFilePathCache != null && mediaFile.getPath() != null) {
                mediaFilePathCache.remove(mediaFile.getPath() + "-" + mediaFile.getFolderId());
            }
            if (mediaFileIdCache != null && mediaFile.getId() != null) {
                mediaFileIdCache.remove(mediaFile.getId());
            }
        }
        mediaFileDao.createOrUpdateMediaFile(mediaFile, file -> {
            // Copy values from obsolete table music_file_info if inserting for first time
            MusicFolder folder = mediaFolderService.getMusicFolderById(mediaFile.getFolderId());
            if (folder != null) {
                MediaFile musicFileInfo = mediaFileDao.getMusicFileInfo(file.getFullPath(folder.getPath()).toString());
                if (musicFileInfo != null) {
                    file.setComment(musicFileInfo.getComment());
                    file.setLastPlayed(musicFileInfo.getLastPlayed());
                    file.setPlayCount(musicFileInfo.getPlayCount());
                }
            }
        });

        // persist cover art if not overridden
        coverArtService.persistIfNeeded(mediaFile);
    }

    /**
     * Increments the play count and last played date for the given media file and its
     * directory and album.
     */
    public void incrementPlayCount(MediaFile file) {
        Instant now = Instant.now();
        file.setLastPlayed(now);
        file.setPlayCount(file.getPlayCount() + 1);
        updateMediaFile(file);

        MediaFile parent = getParentOf(file);
        if (!isRoot(parent)) {
            parent.setLastPlayed(now);
            parent.setPlayCount(parent.getPlayCount() + 1);
            updateMediaFile(parent);
        }

        Album album = albumDao.getAlbum(file.getAlbumArtist(), file.getAlbumName());
        if (album != null) {
            album.setLastPlayed(now);
            album.incrementPlayCount();
            albumDao.createOrUpdateAlbum(album);
        }
    }

    public List<MediaFileEntry> toMediaFileEntryList(List<MediaFile> files, String username, boolean calculateStarred, boolean calculateFolderAccess,
            Function<MediaFile, String> streamUrlGenerator, Function<MediaFile, String> remoteStreamUrlGenerator,
            Function<MediaFile, String> remoteCoverArtUrlGenerator) {
        Locale locale = Optional.ofNullable(username).map(localeService::resolveLocale).orElse(null);
        List<MediaFileEntry> entries = new ArrayList<>(files.size());
        for (MediaFile file : files) {
            String streamUrl = Optional.ofNullable(streamUrlGenerator).map(g -> g.apply(file)).orElse(null);
            String remoteStreamUrl = Optional.ofNullable(remoteStreamUrlGenerator).map(g -> g.apply(file)).orElse(null);
            String remoteCoverArtUrl = Optional.ofNullable(remoteCoverArtUrlGenerator).map(g -> g.apply(file)).orElse(null);

            boolean starred = calculateStarred && username != null && getMediaFileStarredDate(file.getId(), username) != null;
            boolean folderAccess = !calculateFolderAccess || username == null || securityService.isFolderAccessAllowed(file, username);
            entries.add(MediaFileEntry.fromMediaFile(file, locale, starred, folderAccess, streamUrl, remoteStreamUrl, remoteCoverArtUrl,
                    getAccentColor(file)));
        }

        return entries;
    }

    /**
     * Returns the accent color of the cover art shown for the given media file.
     * For non-directory files the cover art belongs to the parent (album) directory,
     * mirroring the resolution logic of CoverArtImageService.
     */
    private String getAccentColor(MediaFile file) {
        MediaFile dir = file.isDirectory() ? file : Optional.ofNullable(getParentOf(file)).orElse(file);
        return coverArtService.getAccentColor(EntityType.MEDIA_FILE, dir.getId());
    }

    public int getAlbumCount(List<MusicFolder> musicFolders) {
        return mediaFileDao.getAlbumCount(musicFolders);
    }

    public int getPlayedAlbumCount(List<MusicFolder> musicFolders) {
        return mediaFileDao.getPlayedAlbumCount(musicFolders);
    }

    public int getStarredAlbumCount(String username, List<MusicFolder> musicFolders) {
        return mediaFileDao.getStarredAlbumCount(username, musicFolders);
    }

    public void setAlbumDao(AlbumDao albumDao) {
        this.albumDao = albumDao;
    }

    public void setParser(JaudiotaggerParser parser) {
        this.parser = parser;
    }
}
