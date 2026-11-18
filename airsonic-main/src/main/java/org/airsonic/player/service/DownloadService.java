package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.io.PipeStreams.MonitoredInputStream;
import org.airsonic.player.io.PipeStreams.PipedInputStream;
import org.airsonic.player.io.PipeStreams.PipedOutputStream;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.LambdaUtils;
import org.airsonic.player.util.StringUtil;
import org.airsonic.player.security.CallerContext;
import org.airsonic.player.security.AuthorizationDeniedException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.*;
import java.util.stream.*;
import java.util.zip.*;

public class DownloadService {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadService.class);
    private final StatusService statusService;
    private final SecurityService securityService;
    private final PlaylistService playlistService;
    private final SettingsService settingsService;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final CoverArtService coverArtService;

    public DownloadService(StatusService statusService, SecurityService securityService, PlaylistService playlistService,
            SettingsService settingsService, MediaFileService mediaFileService, MediaFolderService mediaFolderService, CoverArtService coverArtService) {
        this.statusService = statusService;
        this.securityService = securityService;
        this.playlistService = playlistService;
        this.settingsService = settingsService;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.coverArtService = coverArtService;
    }

    public DownloadResponse prepare(User user, Player transferPlayer, Optional<Integer> id, Integer playlist, Integer player, List<Integer> indices) throws IOException {
        return prepare(user, transferPlayer, id, playlist, player, indices, null);
    }

    /** Uses a validated selection snapshot so live queue or playlist edits cannot replace checked files. */
    public DownloadResponse prepare(User user, Player transferPlayer, Optional<Integer> id, Integer playlist,
            Integer player, List<Integer> indices, List<MediaFile> selection) throws IOException {
        return prepare(user, securityService.createCallerContext(user.getUsername(), true), transferPlayer, id, playlist, player, indices, selection);
    }

    /** Uses the immutable request identity established at the servlet boundary. */
    public DownloadResponse prepare(User user, CallerContext callerContext, Player transferPlayer, Optional<Integer> id,
            Integer playlist, Integer player, List<Integer> indices, List<MediaFile> selection) throws IOException {
        String defaultDownloadName = null;
        DownloadResponse response = null;

        Supplier<TransferStatus> statusSupplier = () -> statusService.createDownloadStatus(transferPlayer);

        Consumer<TransferStatus> statusCloser = status -> {
            statusService.removeDownloadStatus(status);
            securityService.updateUserByteCounts(user, 0L, status.getBytesTransferred(), 0L);
            LOG.info("Transferred {} bytes to user: {}, player: {}", status.getBytesTransferred(), user.getUsername(), transferPlayer);
        };

        MediaFile mediaFile = id.map(mediaFileService::getMediaFile).orElse(null);
        Collection<Pair<Path, Integer>> additionalFiles = Collections.emptyList();

        if (mediaFile != null) {
            if (!securityService.isFolderAccessAllowed(mediaFile, callerContext)) {
                throw new AuthorizationDeniedException("Access to file " + mediaFile.getId() + " is forbidden for user " + user.getUsername());
            }
            if (mediaFile.isFile()) {
                response = prepareResponse(Collections.singletonList(mediaFile), null, statusSupplier, statusCloser, additionalFiles);
                defaultDownloadName = FilenameUtils.getName(mediaFile.getPath());
            } else {
                if (indices == null) {
                    CoverArt art = coverArtService.get(EntityType.MEDIA_FILE, mediaFile.getId());
                    if (!CoverArt.NULL_ART.equals(art)) {
                        additionalFiles = Collections.singletonList(Pair.of(art.getRelativePath(), art.getFolderId()));
                    }
                }
                response = prepareResponse(selection != null ? selection : mediaFileService.getChildrenOf(mediaFile, true, false, true), indices, statusSupplier, statusCloser, additionalFiles);
                defaultDownloadName = FilenameUtils.getBaseName(mediaFile.getPath()) + ".zip";
            }
        } else if (playlist != null) {
            response = prepareResponse(selection != null ? selection : playlistService.getFilesInPlaylist(playlist), indices, statusSupplier, statusCloser, additionalFiles);
            defaultDownloadName = playlistService.getPlaylist(playlist).getName() + ".zip";
        } else if (player != null) {
            response = prepareResponse(selection != null ? selection : transferPlayer.getPlayQueue().getFiles(), indices, statusSupplier, statusCloser, additionalFiles);
            defaultDownloadName = "player-" + transferPlayer.getId() + "-" + transferPlayer.getName() + "-" + "playqueue.zip";
        }

        if (response == null) return null;
        String filename = StringUtil.fileSystemSafe(Optional.ofNullable(response.getProposedName()).orElse(defaultDownloadName));
        return response.withFilename(filename);
    }

    /**
     * Computes the CRC checksum for the given file.
     *
     * @param file The file to compute checksum for.
     * @return A CRC32 checksum.
     * @throws IOException If an I/O error occurs.
     */
    private static long computeCrc(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(is);
                CheckedInputStream cis = new CheckedInputStream(bis, new CRC32())) {
            byte[] buf = new byte[8192];
            while ((cis.read(buf)) != -1) {
                continue;
            }

            return cis.getChecksum().getValue();
        }
    }

    private static long zipSize(Stream<Entry<String, Long>> paths) {
        return paths.mapToLong(e -> 30 + 46 + (2L * e.getKey().getBytes(StandardCharsets.UTF_8).length) + e.getValue()).sum() + 22;
    }

    private DownloadResponse prepareResponse(List<MediaFile> files, List<Integer> indices,
            Supplier<TransferStatus> statusSupplier, Consumer<TransferStatus> statusCloser, Collection<Pair<Path, Integer>> additionalFiles)
            throws IOException {
        if (indices == null) {
            indices = IntStream.range(0, files.size()).boxed().collect(Collectors.toList());
        } else if (indices.parallelStream().anyMatch(i -> i >= files.size())) {
            throw new IllegalArgumentException("Can't have index > number of files");
        }

        if (indices.isEmpty()) {
            // nothing, just return empty
            return new DownloadResponse(null, "emptyfile.download", 0, -1);
        }

        if (indices.size() == 1 && (additionalFiles == null || additionalFiles.isEmpty())) {
            // single file
            MediaFile file = files.get(indices.getFirst());
            Path path = file.getFullPath(mediaFolderService.getMusicFolderById(file.getFolderId()).getPath());
            long changed = file.getChanged() == null ? -1 : file.getChanged().toEpochMilli();
            return new DownloadResponse(
                    () -> new MonitoredInputStream(Files.newInputStream(path), settingsService.getDownloadBitrateLimiter(),
                            statusSupplier, statusCloser, (input, status) -> {}),
                    path.getFileName().toString(),
                    file.getFileSize(),
                    changed);
        } else {
            // get a list of all paths under the tree, plus their zip names and sizes
            Collection<Pair<Path, Pair<String, Long>>> pathsToZip = Stream
                    .concat(
                            indices.stream().map(files::get).filter(Objects::nonNull).map(x -> Pair.of(x.getRelativePath(), x.getFolderId())),
                            additionalFiles.stream().filter(Objects::nonNull))
                    .flatMap(pf -> {
                        MusicFolder mf = mediaFolderService.getMusicFolderById(pf.getRight());
                        Path p = mf.getPath().resolve(pf.getLeft());
                        Path parent = p.getParent();
                        try (Stream<Path> paths = Files.walk(p)) {
                            return paths.filter(f -> !f.getFileName().toString().startsWith("."))
                                    .map(f -> {
                                        String zipName = parent.relativize(f).toString();
                                        long size = 0L;
                                        if (Files.isRegularFile(f)) {
                                            size = FileUtil.size(f);
                                        } else {
                                            zipName = zipName + '/';
                                        }
                                        return Pair.of(f, Pair.of(zipName, size));
                                    })
                                    // need to create a new stream, because try-with-resources will close the paths stream before it exits
                                    .toList().stream();
                        } catch (Exception e) {
                            LOG.warn("Error retrieving file to zip", e);
                            return Stream.empty();
                        }
                    }).filter(Objects::nonNull)
                    // need to preserve order because zip file creation needs to create folders first then files
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // zip to out
            BiConsumer<InputStream, TransferStatus> poutInit = (input, status) -> {
                PipedInputStream pin = (PipedInputStream) input;

                // start a new thread to feed data in
                new Thread(() -> {
                    try (PipedOutputStream pout = new PipedOutputStream(pin);
                            ZipOutputStream zout = new ZipOutputStream(pout)) {
                        zout.setMethod(ZipOutputStream.STORED); // No compression.
                        pathsToZip.forEach(LambdaUtils.uncheckConsumer(f -> {
                            status.setExternalFile(f.getLeft());
                            ZipEntry zipEntry = new ZipEntry(f.getRight().getKey());
                            zipEntry.setSize(f.getRight().getValue());
                            zipEntry.setCompressedSize(f.getRight().getValue());

                            if (f.getRight().getKey().endsWith("/") && f.getRight().getValue() == 0L) {
                                // directory
                                zipEntry.setCrc(0);
                                zout.putNextEntry(zipEntry);
                            } else {
                                zipEntry.setCrc(computeCrc(f.getLeft()));
                                zout.putNextEntry(zipEntry);
                                Files.copy(f.getLeft(), zout);
                            }

                            zout.closeEntry();
                        }));
                    } catch (Exception e1) {
                        LOG.debug("Error with output to zip", e1);
                    }
                }, "DownloadDatafeed").start();

                // wait for src data thread to connect
                while (pin.source == null) {
                    // sit and wait and ponder life
                }
            };

            long size = zipSize(pathsToZip.stream().map(Pair::getRight));

            return new DownloadResponse(
                    () -> new MonitoredInputStream(new PipedInputStream(null, 16 * 1024),
                            settingsService.getDownloadBitrateLimiter(), statusSupplier, statusCloser, poutInit),
                    null, size, -1);
        }
    }

}
