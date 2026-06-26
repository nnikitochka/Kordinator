package su.nnedition.kord

import kotlin.time.Duration

class KordinatorConfig(
    val libraryScanDelaySeconds: Duration,
    val musicLibraryPath: String,
    val allowRegistration: Boolean,
)