package org.airsonic.player.controller.rest.download

import io.ktor.http.Parameters
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.DownloadResponse

class DownloadRequest(
    val parameters: Parameters,
    val download: DownloadResponse?,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val contentDisposition: String? = null,
    val contentType: String? = null,
)
