package org.airsonic.player.service

import org.airsonic.player.domain.CoverArt
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.TransferStatus
import org.airsonic.player.domain.User
import org.airsonic.player.domain.CoverArt.EntityType
import org.airsonic.player.io.PipeStreams.MonitoredInputStream
import org.airsonic.player.io.PipeStreams.PipedInputStream
import org.airsonic.player.io.PipeStreams.PipedOutputStream
import org.airsonic.player.security.AuthorizationDeniedException
import org.airsonic.player.security.CallerContext
import org.airsonic.player.util.FileUtil
import org.airsonic.player.util.StringUtil
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashSet
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Stream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Prepares single-file downloads and streamed ZIP archives for authenticated users. */
class DownloadService(
    private val statusService: StatusService,
    private val securityService: SecurityService,
    private val playlistService: PlaylistService,
    private val settingsService: SettingsService,
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val coverArtService: CoverArtService
) {
    @Throws(IOException::class)
    fun prepare(
        user: User,
        transferPlayer: Player,
        id: Optional<Int>,
        playlist: Int?,
        player: Int?,
        indices: List<Int>?
    ): DownloadResponse? = prepare(user, transferPlayer, id, playlist, player, indices, null)

    /** Uses a validated selection snapshot so live queue or playlist edits cannot replace checked files. */
    @Throws(IOException::class)
    fun prepare(
        user: User,
        transferPlayer: Player,
        id: Optional<Int>,
        playlist: Int?,
        player: Int?,
        indices: List<Int>?,
        selection: List<MediaFile>?
    ): DownloadResponse? = prepare(
        user,
        securityService.createCallerContext(user.username, true),
        transferPlayer,
        id,
        playlist,
        player,
        indices,
        selection
    )

    /** Uses the immutable caller identity established by the authenticated HTTP request. */
    @Throws(IOException::class)
    fun prepare(
        user: User,
        callerContext: CallerContext,
        transferPlayer: Player,
        id: Optional<Int>,
        playlist: Int?,
        player: Int?,
        indices: List<Int>?,
        selection: List<MediaFile>?
    ): DownloadResponse? {
        var defaultDownloadName: String? = null
        var response: DownloadResponse? = null
        val statusSupplier = Supplier { statusService.createDownloadStatus(transferPlayer) }
        val statusCloser = Consumer<TransferStatus> { status ->
            statusService.removeDownloadStatus(status)
            securityService.updateUserByteCounts(user, 0L, status.bytesTransferred, 0L)
            log.info("Transferred {} bytes to user: {}, player: {}", status.bytesTransferred, user.username, transferPlayer)
        }

        val mediaFile = id.map { mediaFileService.getMediaFile(it) }.orElse(null)
        var additionalFiles: Collection<Pair<Path, Int?>> = emptyList()
        if (mediaFile != null) {
            if (!securityService.isFolderAccessAllowed(mediaFile, callerContext)) {
                throw AuthorizationDeniedException(
                    "Access to file ${mediaFile.id} is forbidden for user ${user.username}"
                )
            }
            if (mediaFile.isFile) {
                response = prepareResponse(listOf(mediaFile), null, statusSupplier, statusCloser, additionalFiles)
                defaultDownloadName = FilenameUtils.getName(mediaFile.path)
            } else {
                if (indices == null) {
                    val art = coverArtService.get(EntityType.MEDIA_FILE, mediaFile.id!!)
                    if (CoverArt.NULL_ART != art) additionalFiles = listOf(Pair.of(art.relativePath, art.folderId))
                }
                val files = selection ?: mediaFileService.getChildrenOf(mediaFile, true, false, true)
                response = prepareResponse(files, indices, statusSupplier, statusCloser, additionalFiles)
                defaultDownloadName = FilenameUtils.getBaseName(mediaFile.path) + ".zip"
            }
        } else if (playlist != null) {
            val files = selection ?: playlistService.getFilesInPlaylist(playlist)
            response = prepareResponse(files, indices, statusSupplier, statusCloser, additionalFiles)
            defaultDownloadName = playlistService.getPlaylist(playlist).name + ".zip"
        } else if (player != null) {
            val files = selection ?: transferPlayer.playQueue!!.files
            response = prepareResponse(files, indices, statusSupplier, statusCloser, additionalFiles)
            defaultDownloadName = "player-${transferPlayer.id}-${transferPlayer.name}-playqueue.zip"
        }

        val prepared = response ?: return null
        val filename = StringUtil.fileSystemSafe(prepared.proposedName ?: defaultDownloadName!!)
        return prepared.withFilename(filename)
    }

    @Throws(IOException::class)
    private fun prepareResponse(
        files: List<MediaFile>,
        indices: List<Int>?,
        statusSupplier: Supplier<TransferStatus>,
        statusCloser: Consumer<TransferStatus>,
        additionalFiles: Collection<Pair<Path, Int?>>
    ): DownloadResponse {
        val selectedIndices = indices ?: files.indices.toList()
        if (indices != null && indices.parallelStream().anyMatch { it >= files.size }) {
            throw IllegalArgumentException("Can't have index > number of files")
        }
        if (selectedIndices.isEmpty()) return DownloadResponse(null, "emptyfile.download", 0, -1)
        if (selectedIndices.size == 1 && additionalFiles.isEmpty()) {
            val file = files[selectedIndices.first()]
            val path = file.getFullPath(mediaFolderService.getMusicFolderById(file.folderId)!!.path)
            val changed = file.changed?.toEpochMilli() ?: -1
            return DownloadResponse(
                {
                    MonitoredInputStream(
                        Files.newInputStream(path), settingsService.downloadBitrateLimiter,
                        statusSupplier, statusCloser
                    ) { _, _ -> }
                },
                path.fileName.toString(),
                file.fileSize!!,
                changed
            )
        }

        val pathsToZip = collectFilesToZip(files, selectedIndices, additionalFiles)
        val initializePipe = { input: InputStream, status: TransferStatus ->
            val pin = input as PipedInputStream
            Thread({ writeZipArchive(pin, pathsToZip, status) }, "DownloadDatafeed").start()
            while (pin.source == null) Thread.yield()
        }
        val size = zipSize(pathsToZip.stream().map { it.right })
        return DownloadResponse(
            {
                MonitoredInputStream(
                    PipedInputStream(null, 16 * 1024), settingsService.downloadBitrateLimiter,
                    statusSupplier, statusCloser, initializePipe
                )
            },
            null,
            size,
            -1
        )
    }

    private fun collectFilesToZip(
        files: List<MediaFile>,
        indices: List<Int>,
        additionalFiles: Collection<Pair<Path, Int?>>
    ): LinkedHashSet<Pair<Path, Pair<String, Long>>> {
        val roots = indices.map { files[it] }
            .map { Pair.of(it.relativePath, it.folderId) } + additionalFiles
        val collected = LinkedHashSet<Pair<Path, Pair<String, Long>>>()
        for (root in roots) {
            val musicFolder = mediaFolderService.getMusicFolderById(root.right)!!
            val path = musicFolder.path.resolve(root.left)
            val parent = path.parent
            val rootEntries = ArrayList<Pair<Path, Pair<String, Long>>>()
            try {
                Files.walk(path).use { paths ->
                    paths.filter { !it.fileName.toString().startsWith(".") }.forEach { file ->
                        var zipName = parent.relativize(file).toString()
                        var size = 0L
                        if (Files.isRegularFile(file)) size = FileUtil.size(file) else zipName += "/"
                        rootEntries.add(Pair.of(file, Pair.of(zipName, size)))
                    }
                }
                collected.addAll(rootEntries)
            } catch (failure: Exception) {
                log.warn("Error retrieving file to zip", failure)
            }
        }
        return collected
    }

    private fun writeZipArchive(
        input: PipedInputStream,
        pathsToZip: Set<Pair<Path, Pair<String, Long>>>,
        status: TransferStatus
    ) {
        try {
            PipedOutputStream(input).use { pipe ->
                ZipOutputStream(pipe).use { zip ->
                    zip.setMethod(ZipOutputStream.STORED)
                    for (item in pathsToZip) {
                        status.externalFile = item.left
                        val entry = ZipEntry(item.right.left)
                        entry.size = item.right.right
                        entry.compressedSize = item.right.right
                        if (entry.name.endsWith("/") && item.right.right == 0L) {
                            entry.crc = 0
                        } else {
                            entry.crc = computeCrc(item.left)
                        }
                        zip.putNextEntry(entry)
                        if (!(entry.name.endsWith("/") && item.right.right == 0L)) Files.copy(item.left, zip)
                        zip.closeEntry()
                    }
                }
            }
        } catch (failure: Exception) {
            log.debug("Error with output to zip", failure)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DownloadService::class.java)

        @Throws(IOException::class)
        private fun computeCrc(file: Path): Long = Files.newInputStream(file).use { input ->
            BufferedInputStream(input).use { buffered ->
                CheckedInputStream(buffered, CRC32()).use { checked ->
                    val buffer = ByteArray(8192)
                    while (checked.read(buffer) != -1) Unit
                    checked.checksum.value
                }
            }
        }

        private fun zipSize(paths: Stream<Pair<String, Long>>): Long =
            paths.mapToLong { 30 + 46 + 2L * it.left.toByteArray(StandardCharsets.UTF_8).size + it.right }
                .sum() + 22
    }
}
