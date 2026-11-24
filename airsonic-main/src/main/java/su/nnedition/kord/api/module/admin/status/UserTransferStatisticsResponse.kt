package su.nnedition.kord.api.module.admin.status

data class UserTransferStatisticsResponse(val username: String, val streamed: Long, val downloaded: Long, val uploaded: Long)
