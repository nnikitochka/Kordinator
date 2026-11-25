package su.nnedition.kord.api.module.admin.status

data class TransferStatisticsResponse(
    val type: String,
    val username: String?,
    val playerType: String?,
    val playerName: String?,
    val path: String?,
    val bytesTransferred: Long,
    val history: List<TransferSampleResponse>,
)
