package org.airsonic.player.controller.statistics

data class UserDataTransferStatistics(
    val user: String,
    val streamed: Long,
    val downloaded: Long,
    val uploaded: Long
)
