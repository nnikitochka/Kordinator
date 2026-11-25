package su.nnedition.kord.util

import java.io.IOException
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class FileUtils private constructor() {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun copyRecursively(from: Path, to: Path) {
            Files.walk(from).use { sources ->
                sources.forEach { source ->
                    val destination = to.resolve(from.relativize(source))
                    try {
                        if (Files.isDirectory(source)) {
                            if (Files.notExists(destination)) Files.createDirectories(destination)
                        } else {
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                        }
                    } catch (failure: IOException) {
                        throw RuntimeException("Failed to unzip file.", failure)
                    }
                }
            }
        }

        @JvmStatic
        @Throws(IOException::class, URISyntaxException::class)
        fun copyRecursively(originUrl: URL, destination: Path) {
            copyRecursively(Paths.get(originUrl.toURI()), destination)
        }
    }
}
