package su.nnedition.kord.io

import su.nnedition.kord.util.FileUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Utility class which reads everything from an input stream and optionally logs it.
 * 
 * @see TranscodeInputStream
 */
class InputStreamReaderThread(
    private val input: InputStream,
    private val name: String,
    private val log: Boolean
) : Thread("$name InputStreamLogger") {
    override fun run() {
        try {
            BufferedReader(InputStreamReader(input)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (log) {
                        LOG.info("($name) $line")
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: IOException) {
        } finally {
            FileUtil.closeQuietly(input)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(InputStreamReaderThread::class.java)
    }
}
