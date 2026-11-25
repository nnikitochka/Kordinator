package su.nnedition.kord.util

import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.MusicFolder.Type
import java.net.URISyntaxException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

class MusicFolderTestData private constructor() {
    companion object {
        private const val BASE_RESOURCES = "/MEDIAS/"

        @JvmStatic
        fun resolveBaseMediaPath(): Path = try {
            Paths.get(requireNotNull(MusicFolderTestData::class.java.getResource(BASE_RESOURCES)).toURI())
        } catch (failure: URISyntaxException) {
            throw RuntimeException("Could not find base resources for tests", failure)
        }

        @JvmStatic
        fun resolveMusicFolderPath(): Path = resolveBaseMediaPath().resolve("Music")

        @JvmStatic
        fun resolveMusic2FolderPath(): Path = resolveBaseMediaPath().resolve("Music2")

        @JvmStatic
        fun resolveMusic3FolderPath(): Path = resolveBaseMediaPath().resolve("Music3")

        @JvmStatic
        fun getTestMusicFolders(): List<MusicFolder> = listOf(
            MusicFolder(1, resolveMusicFolderPath(), "Music", Type.MEDIA, true, Instant.now()),
            MusicFolder(2, resolveMusic2FolderPath(), "Music2", Type.MEDIA, true, Instant.now()),
        )
    }
}
