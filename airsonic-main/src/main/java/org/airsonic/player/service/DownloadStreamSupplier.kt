package org.airsonic.player.service

import java.io.IOException
import java.io.InputStream

fun interface DownloadStreamSupplier {
    @Throws(IOException::class)
    fun open(): InputStream
}
