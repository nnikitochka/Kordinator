package su.nnedition.kord.library.model

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

data class TrackFile(
    val file: File,
) {
    val fileName: String = file.name
    val albumFolderPath: File = file.parentFile

    private val lazyTags = CompletableFuture.supplyAsync(
        { TrackTags(file) },
        tagsExecutor,
    )
    val tags: TrackTags get() = lazyTags.join()

    private companion object {
        private val tagsExecutor = Executors.newFixedThreadPool(
            /*if (isSSD) 16 else*/ 4 // ну я надеюсь тут всё понятно
        )
    }
}