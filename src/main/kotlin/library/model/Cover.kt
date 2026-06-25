package su.nnedition.kord.library.model

import java.io.File

open class Cover {
    class None : Cover()
    class FileCover(
        val file: File
    ) : Cover()
}