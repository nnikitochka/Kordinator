package su.nnedition.kord.util

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.time.DurationFormatUtils
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern

/** Miscellaneous string utility methods. */
object StringUtil {
    const val ENCODING_UTF8 = "UTF-8"

    private val SPLIT_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)")
    private val MIME_TYPES = arrayOf(
        arrayOf("mp3", "audio/mpeg"), arrayOf("ogg", "audio/ogg"), arrayOf("oga", "audio/ogg"),
        arrayOf("opus", "audio/ogg"), arrayOf("ogx", "application/ogg"), arrayOf("aac", "audio/mp4"),
        arrayOf("m4a", "audio/mp4"), arrayOf("m4b", "audio/mp4"), arrayOf("flac", "audio/flac"),
        arrayOf("wav", "audio/x-wav"), arrayOf("wma", "audio/x-ms-wma"), arrayOf("ape", "audio/x-monkeys-audio"),
        arrayOf("mpc", "audio/x-musepack"), arrayOf("shn", "audio/x-shn"), arrayOf("flv", "video/x-flv"),
        arrayOf("avi", "video/avi"), arrayOf("mpg", "video/mpeg"), arrayOf("mpeg", "video/mpeg"),
        arrayOf("mp4", "video/mp4"), arrayOf("m4v", "video/x-m4v"), arrayOf("mkv", "video/x-matroska"),
        arrayOf("mov", "video/quicktime"), arrayOf("wmv", "video/x-ms-wmv"), arrayOf("ogv", "video/ogg"),
        arrayOf("divx", "video/divx"), arrayOf("m2ts", "video/MP2T"), arrayOf("ts", "video/MP2T"),
        arrayOf("webm", "video/webm"), arrayOf("gif", "image/gif"), arrayOf("jpg", "image/jpeg"),
        arrayOf("jpeg", "image/jpeg"), arrayOf("png", "image/png"), arrayOf("bmp", "image/bmp"),
        arrayOf("zip", "application/zip")
    )
    private val FILE_SYSTEM_UNSAFE = arrayOf("/", "\\", "..", ":", "\"", "?", "*", "|")

    @JvmStatic
    fun getMimeType(suffix: String?): String = MIME_TYPES.firstOrNull {
        it[0].equals(suffix, ignoreCase = true) || ("." + it[0]).equals(suffix, ignoreCase = true)
    }?.get(1) ?: "application/octet-stream"

    @JvmStatic
    fun getSuffix(mimeType: String?): String? = MIME_TYPES.firstOrNull { it[1].equals(mimeType, ignoreCase = true) }?.get(0)

    @JvmStatic
    @Synchronized
    fun formatBytes(byteCount: Long?, locale: Locale): String? {
        if (byteCount == null) return null
        if (byteCount >= 1024L * 1024 * 1024 * 1024) {
            return DecimalFormat("0.00 TB", DecimalFormatSymbols(locale))
                .format(byteCount / (1024.0 * 1024 * 1024 * 1024))
        }
        if (byteCount >= 1024L * 1024 * 1024) {
            return DecimalFormat("0.00 GB", DecimalFormatSymbols(locale))
                .format(byteCount / (1024.0 * 1024 * 1024))
        }
        if (byteCount >= 1024L * 1024) {
            return DecimalFormat("0.0 MB", DecimalFormatSymbols(locale))
                .format(byteCount / (1024.0 * 1024))
        }
        if (byteCount >= 1024L) {
            return DecimalFormat("0 KB", DecimalFormatSymbols(locale)).format(byteCount / 1024.0)
        }
        return "$byteCount B"
    }

    @JvmStatic
    fun formatDuration(millis: Long, convertToHours: Boolean?): String {
        var format = "m:ss"
        if ((millis >= 3600000 && convertToHours == null) || convertToHours == true) format = "H:m$format"
        if (millis % 1000 != 0L) format += ".S"
        return DurationFormatUtils.formatDuration(millis, format)
    }

    @JvmStatic
    fun formatDuration(millis: Long): String = formatDuration(millis, null)

    @JvmStatic
    fun split(input: String?): Array<String> {
        if (input == null) return emptyArray()
        val result = ArrayList<String>()
        val matcher = SPLIT_PATTERN.matcher(input)
        while (matcher.find()) result.add(matcher.group(1) ?: matcher.group(2))
        return result.toTypedArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLines(input: InputStream): Array<String> {
        try {
            BufferedReader(InputStreamReader(input)).use { reader ->
                val result = ArrayList<String>()
                var line = reader.readLine()
                while (line != null) {
                    line = line.trim()
                    if (!line.startsWith("#") && line.isNotEmpty()) result.add(line)
                    line = reader.readLine()
                }
                return result.toTypedArray()
            }
        } finally {
            FileUtil.closeQuietly(input)
        }
    }

    @JvmStatic
    fun parseLocale(value: String?): Locale? {
        if (value == null) return null
        val elements = ArrayList(Arrays.asList(*value.split("_", limit = 3).toTypedArray()))
        while (elements.size < 3) elements.add("")
        return Locale.of(elements[0], elements[1], elements[2])
    }

    @JvmStatic
    fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    @JvmStatic
    fun urlDecode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    @JvmStatic
    fun utf8HexEncode(value: String?): String? = value?.let { String(Hex.encodeHex(it.toByteArray(StandardCharsets.UTF_8))) }

    @JvmStatic
    fun decodeSubsonicPassword(value: String?): String? {
        if (value == null || !value.startsWith("enc:")) return value
        return try {
            utf8HexDecode(value.substring(4))
        } catch (_: Exception) {
            value
        }
    }

    @JvmStatic
    @Throws(DecoderException::class)
    fun utf8HexDecode(value: String?): String? =
        value?.let { String(Hex.decodeHex(it.toCharArray()), StandardCharsets.UTF_8) }

    @JvmStatic
    fun getUrlFile(value: String): String? {
        try {
            val path = URL(value).path
            if (StringUtils.isBlank(path) || path.endsWith("/")) return null
            val filename = File(path).name
            if (StringUtils.isBlank(filename)) return null
            return filename
        } catch (_: MalformedURLException) {
            return null
        }
    }

    @JvmStatic
    fun fileSystemSafe(filename: String): String {
        var safe = filename
        for (unsafe in FILE_SYSTEM_UNSAFE) safe = safe.replace(unsafe, "-")
        return safe
    }

    @JvmStatic
    fun removeMarkup(value: String?): String? = value?.replace(Regex("<.*?>"), "")
}
