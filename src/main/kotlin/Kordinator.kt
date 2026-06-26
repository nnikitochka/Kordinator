package su.nnedition.kord

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import su.nnedition.kord.data.local.repository.ArtistsContainer
import su.nnedition.kord.data.local.track.TracksContainer
import su.nnedition.kord.library.KordLibrary
import su.nnedition.kord.library.LibraryLoader
import su.nnedition.kord.web.artist.configureArtistsRouting
import su.nnedition.kord.web.info.configureServerInfoRouting
import su.nnedition.kord.web.login.configureLoginRouting
import su.nnedition.kord.web.registration.configureRegisterRouting
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Kordinator {
    lateinit var library: KordLibrary

    val startTime = Clock.System.now()
    val uptime: Duration get() = Clock.System.now() - startTime

    val config = KordinatorConfig(
        libraryScanDelaySeconds = 10.toDuration(DurationUnit.SECONDS),
        musicLibraryPath = "/home/nnikitochka/Музыка/Kord/",
        allowRegistration = true
    )

    @JvmStatic
    fun main(args: Array<String>) {
        library = KordLibrary(
            config = config,
            loader = LibraryLoader(File(config.musicLibraryPath)),
            artistsRepository = ArtistsContainer(),
            tracksRepository = TracksContainer(),
        )

        embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
            configureSerialization()

            configureRouting()
            configureServerInfoRouting()
            configureRegisterRouting()
            configureLoginRouting()

            configureArtistsRouting()
        }.start(wait = true)
    }
}