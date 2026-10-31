package org.airsonic.player.controller.rest.download

class DownloadRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
}
