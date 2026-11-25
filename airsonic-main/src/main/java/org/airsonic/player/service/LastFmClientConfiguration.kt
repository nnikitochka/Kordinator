package org.airsonic.player.service

import de.umass.lastfm.Caller

/** Initializes Last.fm's shared HTTP client and disk cache. */
internal object LastFmClientConfiguration {
    private const val CACHE_TIME_TO_LIVE_MILLIS = 6 * 30 * 24 * 3600 * 1000L

    fun initialize() {
        val caller = Caller.getInstance()
        caller.setUserAgent("Airsonic")
        val cacheDir = SettingsService.getAirsonicHome().resolve("lastfmcache")
        caller.setCache(LastFmCache(cacheDir, CACHE_TIME_TO_LIVE_MILLIS))
    }
}
