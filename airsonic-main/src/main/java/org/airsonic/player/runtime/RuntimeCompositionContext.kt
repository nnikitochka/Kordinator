package org.airsonic.player.runtime

/** Shared ownership, graph state and reentrant lock for all composition groups. */
internal class RuntimeCompositionContext(
    val configuration: RuntimeConfiguration,
    val lifecycleRegistry: LifecycleRegistry,
) {
    val lock = Any()
    lateinit var root: RuntimeCompositionRoot
    var persistenceGraph: PersistenceGraph? = null
    val infrastructure = RuntimeInfrastructureComposition(this)
    val security = RuntimeSecurityComposition(this)
    val search = RuntimeSearchComposition(this)
    val settings = RuntimeSettingsComposition(this)
    val upnp = RuntimeUpnpComposition(this)
    val media = RuntimeMediaComposition(this)
    val playlist = RuntimePlaylistComposition(this)
    val playback = RuntimePlaybackComposition(this)
    val application = RuntimeApplicationComposition(this)
}
