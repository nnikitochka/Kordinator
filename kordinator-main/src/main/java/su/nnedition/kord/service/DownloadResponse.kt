package su.nnedition.kord.service

import java.io.IOException
import java.io.InputStream

class DownloadResponse private constructor(
    private val source: DownloadStreamSupplier?,
    val proposedName: String?,
    val size: Long,
    val changed: Long,
    val filename: String?,
) {
    constructor(source: DownloadStreamSupplier?, proposedName: String?, size: Long, changed: Long) :
        this(source, proposedName, size, changed, null)

    fun withFilename(filename: String) = DownloadResponse(source, proposedName, size, changed, filename)

    fun hasContent(): Boolean = source != null

    @Throws(IOException::class)
    fun openStream(): InputStream = source!!.open()
}
