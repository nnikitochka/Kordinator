package org.airsonic.player.ajax;

import java.util.UUID;

/**
 * Contains status for a file upload.
 *
 * @param bytesUploaded The number of bytes uploaded.
 * @param bytesTotal The total number of bytes.
 */
public record UploadInfo(
        UUID transferId,
        long bytesUploaded,
        long bytesTotal
) {
}
