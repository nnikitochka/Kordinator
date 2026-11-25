package org.airsonic.player.service.library.scan

import java.time.Instant

data class LibraryScan(
    val id: String,
    val libraryId: Long,
    val state: String,
    val tracksScanned: Long,
    val errorCount: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
)