package org.airsonic.player.service.settings

import com.google.common.util.concurrent.RateLimiter
import java.util.function.Function

/** Keeps configured upload and download rates in sync with their reusable limiters. */
internal class SettingsRateLimitPreferences(private val values: SettingsValueStore) : SettingsRateLimitAccess {
    override fun getDownloadBitrateLimit(): Long = values.getLong(KEY_DOWNLOAD_BITRATE_LIMIT, DEFAULT_BITRATE_LIMIT)

    override fun getDownloadBitrateLimiter(): RateLimiter = limiter(KEY_DOWNLOAD_BITRATE_LIMIT) {
        getDownloadBitrateLimit()
    }

    override fun setDownloadBitrateLimit(limit: Long) {
        values.setProperty(KEY_DOWNLOAD_BITRATE_LIMIT, limit)
        getDownloadBitrateLimiter().setRate(adjustBitrateLimit(limit.toDouble()))
    }

    override fun getUploadBitrateLimit(): Long = values.getLong(KEY_UPLOAD_BITRATE_LIMIT, DEFAULT_BITRATE_LIMIT)

    override fun getUploadBitrateLimiter(): RateLimiter = limiter(KEY_UPLOAD_BITRATE_LIMIT) {
        getUploadBitrateLimit()
    }

    override fun setUploadBitrateLimit(limit: Long) {
        values.setProperty(KEY_UPLOAD_BITRATE_LIMIT, limit)
        getUploadBitrateLimiter().setRate(adjustBitrateLimit(limit.toDouble()))
    }

    private fun limiter(key: String, currentLimit: () -> Long): RateLimiter =
        values.derived(key, Function { RateLimiter.create(adjustBitrateLimit(currentLimit().toDouble())) }) as RateLimiter

    private fun adjustBitrateLimit(rate: Double): Double =
        if (rate == 0.0) Double.POSITIVE_INFINITY else rate * BYTES_PER_KILOBIT

    private companion object {
        const val KEY_DOWNLOAD_BITRATE_LIMIT = "DownloadBitrateLimit"
        const val KEY_UPLOAD_BITRATE_LIMIT = "UploadBitrateLimit"
        const val DEFAULT_BITRATE_LIMIT = 0L
        const val BYTES_PER_KILOBIT = 1024.0
    }
}
