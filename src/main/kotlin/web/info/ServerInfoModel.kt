package su.nnedition.kord.web.info

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class ServerInfoModel(
    val version: String,
    val uptime: Duration,
    val allowRegistration: Boolean,
)