package org.airsonic.player.controller

import org.airsonic.player.service.DownloadResponse
import org.springframework.core.io.AbstractResource
import java.io.IOException
import java.io.InputStream

internal class DownloadResource(private val response: DownloadResponse) : AbstractResource() {
    @Throws(IOException::class)
    override fun getInputStream(): InputStream = response.openStream()

    override fun contentLength(): Long = response.size


    override fun getDescription(): String = response.filename
}
