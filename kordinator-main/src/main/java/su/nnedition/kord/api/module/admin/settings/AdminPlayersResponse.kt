package su.nnedition.kord.api.module.admin.settings

data class AdminPlayersResponse(
    val items: List<AdminPlayerResponse>,
    val transcoding: List<AdminTranscodingOption>,
    val schemes: List<String>,
    val technologies: List<String>,
)
