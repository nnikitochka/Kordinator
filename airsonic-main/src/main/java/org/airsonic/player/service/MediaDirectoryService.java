package org.airsonic.player.service;

import org.airsonic.player.ajax.media.MediaFileDirectoryEntry;

import com.google.common.collect.ImmutableMap;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFileComparator;
import org.airsonic.player.security.CallerContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MediaDirectoryService {
    private final MediaFileService mediaFileService;
    private final RatingService ratingService;
    private final SecurityService securityService;
    private final SettingsService settingsService;

    public MediaDirectoryService(MediaFileService mediaFileService, RatingService ratingService, SecurityService securityService, SettingsService settingsService) {
        this.mediaFileService = mediaFileService;
        this.ratingService = ratingService;
        this.securityService = securityService;
        this.settingsService = settingsService;
    }

    public Object get(CallerContext callerContext, List<Integer> ids, Map<Integer, List<String>> paths) {
        String username = callerContext.getUsername();
        List<MediaFile> mediaFiles = getMediaFiles(ids, paths);

        if (mediaFiles.isEmpty()) {
            return ImmutableMap.of("contentType", "notFound");
        }

        MediaFile dir = mediaFiles.getFirst();
        if (dir.isFile()) {
            dir = mediaFileService.getParentOf(dir);
        }

        // Redirect if root directory.
        if (mediaFileService.isRoot(dir)) {
            return ImmutableMap.of("contentType", "home");
        }

        if (!securityService.isFolderAccessAllowed(dir, callerContext)) {
            return ImmutableMap.of("contentType", "accessDenied");
        }

        for (MediaFile selected : mediaFiles) {
            MediaFile selectedDir = selected.isFile() ? mediaFileService.getParentOf(selected) : selected;
            if (selectedDir == null || !securityService.isFolderAccessAllowed(selectedDir, callerContext)) {
                return ImmutableMap.of("contentType", "accessDenied");
            }
        }

        List<MediaFile> children = getChildren(mediaFiles);

        List<MediaFile> files = new ArrayList<>();
        List<MediaFile> subDirs = new ArrayList<>();
        for (MediaFile child : children) {
            if (child.isFile()) {
                files.add(child);
            } else {
                subDirs.add(child);
            }
        }
        MediaFileDirectoryEntry entry = new MediaFileDirectoryEntry(mediaFileService.toMediaFileEntryList(Collections.singletonList(dir), username, true, true, null, null, null).getFirst());
        entry.setFiles(mediaFileService.toMediaFileEntryList(files, username, true, false, null, null, null));
        entry.setSubDirs(mediaFileService.toMediaFileEntryList(subDirs, username, false, false, null, null, null));
        entry.setAncestors(mediaFileService.toMediaFileEntryList(getAncestors(dir), username, false, false, null, null, null));
        entry.setComment(dir.getComment());
        if (dir.isAlbum()) {
            List<MediaFile> siblingAlbums = getSiblingAlbums(dir);
            entry.setSiblingAlbums(mediaFileService.toMediaFileEntryList(siblingAlbums, username, false, false, null, null, null));
            entry.setAlbum(guessAlbum(children));
            entry.setArtist(guessArtist(children));
            entry.setMusicBrainzReleaseId(guessMusicBrainzReleaseId(children));
        }

        Integer userRating = Optional.ofNullable(ratingService.getRatingForUser(username, dir)).orElse(0);
        Double averageRating = Optional.ofNullable(ratingService.getAverageRating(dir)).orElse(0.0D);

        entry.setUserRating(10 * userRating);
        entry.setAverageRating(10.0D * averageRating);

        if (isVideoOnly(children)) {
            entry.setContentType("video");
        } else if (dir.isAlbum()) {
            entry.setContentType("album");
        } else {
            entry.setContentType("artist");
        }

        return entry;
    }

    private boolean isVideoOnly(List<MediaFile> children) {
        boolean videoFound = false;
        for (MediaFile child : children) {
            if (child.isAudio()) {
                return false;
            }
            if (child.isVideo()) {
                videoFound = true;
            }
        }
        return videoFound;
    }

    private List<MediaFile> getMediaFiles(List<Integer> ids, Map<Integer, List<String>> paths) {
        return Stream
                .concat(Optional.ofNullable(paths).orElse(Collections.emptyMap())
                        .entrySet().parallelStream()
                        .flatMap(e -> e.getValue().stream().map(p -> Pair.of(e.getKey(), p)))
                        .map(pf -> mediaFileService.getMediaFile(pf.getValue(), pf.getKey())),
                        Optional.ofNullable(ids).orElse(Collections.emptyList()).parallelStream().map(mediaFileService::getMediaFile))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String guessArtist(List<MediaFile> children) {
        for (MediaFile child : children) {
            if (child.isFile() && child.getArtist() != null) {
                return child.getArtist();
            }
        }
        return null;
    }

    private String guessAlbum(List<MediaFile> children) {
        for (MediaFile child : children) {
            if (child.isFile() && child.getArtist() != null) {
                return child.getAlbumName();
            }
        }
        return null;
    }

    private String guessMusicBrainzReleaseId(List<MediaFile> children) {
        for (MediaFile child : children) {
            if (child.isFile() && child.getMusicBrainzReleaseId() != null) {
                return child.getMusicBrainzReleaseId();
            }
        }
        return null;
    }

    private List<MediaFile> getChildren(List<MediaFile> mediaFiles) {
        SortedSet<MediaFile> result = new TreeSet<>(new MediaFileComparator(settingsService.isSortAlbumsByYear()));
        for (MediaFile mediaFile : mediaFiles) {
            if (mediaFile.isFile()) {
                mediaFile = mediaFileService.getParentOf(mediaFile);
            }
            result.addAll(mediaFileService.getChildrenOf(mediaFile, true, true, true));
        }
        return new ArrayList<>(result);
    }

    private List<MediaFile> getAncestors(MediaFile dir) {
        ArrayList<MediaFile> result = new ArrayList<>();

        try {
            MediaFile parent = mediaFileService.getParentOf(dir);
            while (parent != null) {
                result.add(parent);
                if (mediaFileService.isRoot(parent)) {
                    break;
                }
                parent = mediaFileService.getParentOf(parent);
            }
        } catch (SecurityException x) {
            // Ignored.
        }
        return result;
    }

    private List<MediaFile> getSiblingAlbums(MediaFile dir) {
        List<MediaFile> result = new ArrayList<>();

        MediaFile parent = mediaFileService.getParentOf(dir);
        if (!mediaFileService.isRoot(parent)) {
            List<MediaFile> siblings = mediaFileService.getChildrenOf(parent, false, true, true);
            result.addAll(siblings.stream().filter(sibling -> sibling.isAlbum() && !sibling.equals(dir))
                    .toList());
        }
        return result;
    }
}
