package org.airsonic.player.ajax.media

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.util.StringUtil
import java.time.Instant
import java.util.*

open class MediaFileEntry(
    val id: Int,
    val trackNumber: Int?,
    val discNumber: Int?,
    val title: String?,
    var artist: String?,
    var albumArtist: String?,
    var album: String?,
    val genre: String?,
    val year: Int?,
    val bitRate: String?,
    val dimensions: String?,
    val duration: Double?,
    val format: String?,
    var contentType: String?,
    val entryType: String?,
    val fileSize: String?,
    val playCount: Int,
    val lastPlayed: Instant?,
    val created: Instant?,
    val changed: Instant?,
    val lastScanned: Instant?,
    val starred: Boolean,
    val present: Boolean,
    val albumUrl: String?,
    val streamUrl: String?,
    val remoteStreamUrl: String?,
    val coverArtUrl: String?,
    val remoteCoverArtUrl: String?,
    val accentColor: String?
) {
    companion object {
        @JvmStatic
        fun fromMediaFile(
            file: MediaFile, starred: Boolean,
            folderAccess: Boolean, streamUrl: String?, remoteStreamUrl: String?,
            remoteCoverArtUrl: String?, accentColor: String?
        ): MediaFileEntry {
            return MediaFileEntry(
                id = file.id,
                trackNumber = file.trackNumber,
                discNumber = file.discNumber,
                title = file.name,
                artist = file.artist,
                albumArtist = file.albumArtist,
                album = file.albumName,
                genre = file.genre,
                year = file.year,
                bitRate = formatBitRate(file),
                dimensions = if (file.width != null && file.height != null) file.width.toString() + "x" + file.height else null,
                duration = file.duration,
                file.format,
                StringUtil.getMimeType(file.format),
                file.mediaType.toString(),
                StringUtil.formatBytes(file.fileSize, Locale.ENGLISH),
                file.playCount,
                file.lastPlayed,
                file.created,
                file.changed,
                file.lastScanned,
                starred,
                file.isPresent && folderAccess,
                "main.view?id=${file.id}",
                streamUrl,
                remoteStreamUrl,
                "coverArt.view?id=${file.id}",
                remoteCoverArtUrl,
                accentColor
            )
        }

        private fun formatBitRate(mediaFile: MediaFile): String? {
            mediaFile.bitRate ?: return null

            if (mediaFile.isVariableBitRate)
                return "vbr ${mediaFile.bitRate} Kbps"

            return mediaFile.bitRate.toString() + " Kbps"
        }
    }
}