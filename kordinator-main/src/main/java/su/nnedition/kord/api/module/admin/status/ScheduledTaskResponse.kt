package su.nnedition.kord.api.module.admin.status

data class ScheduledTaskResponse(
    val name: String,
    val createdAt: String,
    val scheduledBy: String,
    val type: String?,
    val firstRun: String?,
    val lastRun: String?,
    val nextRun: String?,
)
