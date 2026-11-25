package su.nnedition.kord.domain

import java.nio.file.Files
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.function.Function
import java.util.stream.IntStream

/** A list of music files associated with a remote player. */
open class PlayQueue(private val folderIdToFolder: Function<Int, MusicFolder>) {
    private var queueFiles: MutableList<MediaFile> = ArrayList()

    private var filesBackup: MutableList<MediaFile> = ArrayList()
    private var indexBackup = 0

    @Volatile
    private var currentIndex = 0

    open var name: String? = "(unnamed)"
    open var repeatStatus: RepeatStatus = RepeatStatus.OFF
    open var status: Status = Status.PLAYING
        @Synchronized set(value) {
            field = value
            if (currentIndex == -1) currentIndex = maxOf(0, minOf(currentIndex, size() - 1))
        }

    open var index: Int
        get() = currentIndex
        @Synchronized set(value) {
            makeBackup()
            currentIndex = maxOf(0, minOf(value, size() - 1))
            status = Status.PLAYING
        }

    open var randomSearchCriteria: RandomSearchCriteria? = null

    open val files: MutableList<MediaFile>
        get() = queueFiles

    @Synchronized
    open fun getCurrentFile(): MediaFile? {
        if (currentIndex == -1 || currentIndex == 0 && size() == 0) {
            status = Status.STOPPED
            return null
        }

        val file = queueFiles[currentIndex]
        val folder = folderIdToFolder.apply(file.folderId!!)
        if (!Files.exists(file.getFullPath(folder.path))) {
            queueFiles.removeAt(currentIndex)
            currentIndex = maxOf(0, minOf(currentIndex, size() - 1))
            return getCurrentFile()
        }

        return file
    }

    @Synchronized
    open fun getFile(index: Int): MediaFile = queueFiles[index]

    @Synchronized
    open fun next() {
        if (repeatStatus == RepeatStatus.TRACK) return

        currentIndex++
        if (currentIndex >= size()) {
            currentIndex = if (repeatStatus == RepeatStatus.QUEUE) 0 else -1
        }
    }

    open fun size(): Int = queueFiles.size

    open fun isEmpty(): Boolean = queueFiles.isEmpty()

    @Synchronized
    open fun addFilesAt(mediaFiles: Collection<MediaFile>, index: Int) {
        makeBackup()
        queueFiles.addAll(index, mediaFiles)
        status = Status.PLAYING
    }

    @Synchronized
    open fun addFiles(append: Boolean, mediaFiles: Collection<MediaFile>) {
        makeBackup()
        if (!append) {
            currentIndex = 0
            queueFiles.clear()
        }
        queueFiles.addAll(mediaFiles)
        status = Status.PLAYING
    }

    @Synchronized
    open fun addFiles(append: Boolean, vararg mediaFiles: MediaFile) {
        addFiles(append, Arrays.asList(*mediaFiles))
    }

    @Synchronized
    open fun removeFileAt(index: Int) {
        makeBackup()
        val removalIndex = maxOf(0, minOf(index, size() - 1))
        if (currentIndex > removalIndex) currentIndex--
        queueFiles.removeAt(removalIndex)
        currentIndex = maxOf(0, minOf(currentIndex, size() - 1))
    }

    @Synchronized
    open fun clear() {
        makeBackup()
        queueFiles.clear()
        randomSearchCriteria = null
        currentIndex = 0
    }

    @Synchronized
    open fun shuffle() {
        makeBackup()
        val currentFile = getCurrentFile()
        Collections.shuffle(queueFiles)
        if (currentFile != null) {
            Collections.swap(queueFiles, queueFiles.indexOf(currentFile), 0)
            currentIndex = 0
        }
    }

    @Synchronized
    open fun sort(sortOrder: SortOrder) {
        makeBackup()
        val currentFile = getCurrentFile()
        queueFiles.sortWith(PlayQueueSorter.comparator(sortOrder))
        if (currentFile != null) currentIndex = queueFiles.indexOf(currentFile)
    }

    @Synchronized
    open fun rearrange(indexes: List<Int>?) {
        if (indexes == null || indexes.size != size()) return
        makeBackup()
        val reordered = arrayOfNulls<MediaFile>(queueFiles.size)
        IntStream.range(0, size()).parallel().forEach { position ->
            reordered[position] = queueFiles[indexes[position]]
            if (currentIndex == indexes[position]) currentIndex = position
        }
        queueFiles.clear()
        queueFiles.addAll(reordered.map { it!! })
    }

    @Synchronized
    open fun moveUp(index: Int) {
        moveDown(index - 1)
    }

    @Synchronized
    open fun moveDown(index: Int) {
        if (index < 0 || index >= size() - 1) return
        makeBackup()
        Collections.swap(queueFiles, index, index + 1)
        if (currentIndex == index) currentIndex++ else if (currentIndex == index + 1) currentIndex--
    }

    open fun isShuffleRadioEnabled(): Boolean = randomSearchCriteria != null

    @Synchronized
    open fun undo() {
        val previousFiles = ArrayList(queueFiles)
        val previousIndex = currentIndex
        currentIndex = indexBackup
        queueFiles = filesBackup
        indexBackup = previousIndex
        filesBackup = previousFiles
    }

    @Synchronized
    open fun length(): Long = queueFiles.parallelStream().mapToLong { it.fileSize!! }.sum()

    @Synchronized
    open fun duration(): Double = queueFiles.parallelStream()
        .filter { it.duration != null }
        .mapToDouble { it.duration!! }
        .sum()

    private fun makeBackup() {
        filesBackup = ArrayList(queueFiles)
        indexBackup = currentIndex
    }

    enum class Status {
        PLAYING,
        STOPPED,
    }

    enum class SortOrder {
        TRACK,
        ARTIST,
        ALBUM,
    }

    enum class RepeatStatus {
        OFF,
        TRACK,
        QUEUE;

        companion object {
            @JvmStatic
            fun getNext(status: RepeatStatus): RepeatStatus = when (status) {
                OFF -> TRACK
                TRACK -> QUEUE
                QUEUE -> OFF
            }
        }
    }
}
