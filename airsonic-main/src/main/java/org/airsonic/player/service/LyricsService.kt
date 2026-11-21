package org.airsonic.player.service

import org.airsonic.player.util.StringUtil
import org.apache.commons.lang.StringUtils
import org.apache.http.client.HttpResponseException
import org.apache.http.client.ResponseHandler
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.IOException
import java.io.StringReader
import java.net.SocketException
import java.net.SocketTimeoutException
import org.airsonic.player.util.XMLUtil.createSAXBuilder

/** Retrieves song lyrics from the legacy chartlyrics.com endpoint. */
class LyricsService(private val fetcher: Fetcher) {
    constructor() : this(Fetcher(::executeGetRequest))

    fun getLyrics(artist: String?, song: String?): Result {
        if (artist == null || song == null) return Result.empty(false)
        var encodedSong = song
        try {
            val encodedArtist = StringUtil.urlEncode(artist)
            encodedSong = StringUtil.urlEncode(song)
            val url = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect?artist=$encodedArtist&song=$encodedSong"
            return parseSearchResult(fetcher.get(url))
        } catch (failure: HttpResponseException) {
            LOG.warn("Failed to get lyrics for song '{}'. Request failed: {}", encodedSong, failure.toString())
            if (failure.statusCode == 503) return Result.empty(true)
        } catch (failure: SocketException) {
            LOG.warn("Failed to get lyrics for song '{}': {}", encodedSong, failure.toString())
            return Result.empty(true)
        } catch (failure: SocketTimeoutException) {
            LOG.warn("Failed to get lyrics for song '{}': {}", encodedSong, failure.toString())
            return Result.empty(true)
        } catch (failure: ConnectTimeoutException) {
            LOG.warn("Failed to get lyrics for song '{}': {}", encodedSong, failure.toString())
            return Result.empty(true)
        } catch (failure: Exception) {
            LOG.warn("Failed to get lyrics for song '{}'.", encodedSong, failure)
        }
        return Result.empty(false)
    }

    private fun parseSearchResult(xml: String): Result {
        val document = createSAXBuilder().build(StringReader(xml))
        val root = document.rootElement
        val namespace = root.namespace
        val lyric = StringUtils.trimToNull(root.getChildText("Lyric", namespace))
        val title = root.getChildText("LyricSong", namespace)
        val artist = root.getChildText("LyricArtist", namespace)
        return Result(lyric, artist, title, false)
    }

    fun interface Fetcher {
        @Throws(IOException::class)
        fun get(url: String): String
    }

    data class Result(
        private val lyricValue: String?,
        private val artistValue: String?,
        private val titleValue: String?,
        private val shouldTryLater: Boolean,
    ) {
        fun lyrics(): String? = lyricValue
        fun artist(): String? = artistValue
        fun title(): String? = titleValue
        fun tryLater(): Boolean = shouldTryLater

        companion object {
            @JvmStatic
            fun empty(tryLater: Boolean) = Result(null, null, null, tryLater)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LyricsService::class.java)

        @JvmStatic
        @Throws(IOException::class)
        private fun executeGetRequest(url: String): String {
            val requestConfig = RequestConfig.custom().setConnectTimeout(15_000).setSocketTimeout(15_000).build()
            val method = HttpGet(url).apply { config = requestConfig }
            HttpClients.createDefault().use { client ->
                val responseHandler: ResponseHandler<String> = BasicResponseHandler()
                return client.execute(method, responseHandler)
            }
        }
    }
}
