package su.nnedition.kord.service.scrobbler

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.util.StringUtil
import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue

/** Queues Last.fm scrobbles and retries network failures. */
open class LastFMScrobbler internal constructor(private val handshakeEndpoint: URI) {
    constructor() : this(URI("http://post.audioscrobbler.com/"))

    private var thread: Thread? = null
    private val queue = LinkedBlockingQueue<LastFmRegistration>()
    private val requestConfig = RequestConfig.custom().setConnectTimeout(15000).setSocketTimeout(15000).build()

    @Synchronized
    open fun register(mediaFile: MediaFile, username: String?, password: String?, submission: Boolean, time: Instant?) {
        if (thread == null) thread = Thread({ processQueue() }, "LastFMScrobbler Registration").also { it.start() }
        if (queue.size >= MAX_PENDING_REGISTRATION) {
            LOG.warn("Last.fm scrobbler queue is full. Ignoring {}", mediaFile)
            return
        }
        val registration = LastFmRegistration(username, password, mediaFile.artist, mediaFile.albumName, mediaFile.title,
            mediaFile.duration?.let { Math.round(it).toInt() } ?: 0, time ?: Instant.now(), submission)
        try { queue.put(registration) } catch (failure: InterruptedException) {
            LOG.warn("Interrupted while queuing Last.fm scrobble: {}", failure.toString())
        }
    }

    private fun processQueue() {
        while (true) {
            var registration: LastFmRegistration? = null
            try {
                registration = queue.take()
                scrobble(registration)
            } catch (failure: IOException) {
                handleNetworkError(registration, failure.toString())
            } catch (failure: Exception) {
                LOG.warn("Error in Last.fm registration: {}", failure.toString())
            }
        }
    }

    private fun scrobble(registration: LastFmRegistration) {
        val lines = authenticate(registration) ?: return
        val session = lines[1]
        val url = if (registration.submission) lines[3] else lines[2]
        val response = executePostRequest(url, registration.parameters(session))
        when {
            response[0].startsWith("FAILED") -> LOG.warn("Failed to scrobble song '{}' at Last.fm: {}", registration.title, response[0])
            response[0].startsWith("BADSESSION") -> LOG.warn("Failed to scrobble song '{}' at Last.fm. Invalid session.", registration.title)
            response[0].startsWith("OK") -> LOG.info("Successfully registered {} for song '{}' for user {} at Last.fm: {}",
                if (registration.submission) "submission" else "now playing", registration.title, registration.username, registration.time)
        }
    }

    private fun authenticate(registration: LastFmRegistration): Array<String>? {
        val timestamp = System.currentTimeMillis() / 1000L
        val token = DigestUtils.md5Hex(DigestUtils.md5Hex(registration.password) + timestamp)
        val uri = URI(handshakeEndpoint.scheme, handshakeEndpoint.userInfo, handshakeEndpoint.host, handshakeEndpoint.port,
            handshakeEndpoint.path, "hs=true&p=1.2.1&c=sub&v=0.1&u=${registration.username}&t=$timestamp&a=$token", null)
        val request = HttpGet(uri).apply { config = requestConfig }
        val lines = executeRequest(request)
        val status = lines[0]
        if (!status.startsWith("OK")) {
            val reason = when {
                status.startsWith("BANNED") -> "Client version is banned."
                status.startsWith("BADAUTH") -> "Wrong username or password."
                status.startsWith("BADTIME") -> "Bad timestamp, please check local clock."
                status.startsWith("FAILED") -> status
                else -> "Unknown response: $status"
            }
            LOG.warn("Failed to scrobble song '{}' at Last.fm. {}", registration.title, reason)
            return null
        }
        return lines
    }

    private fun executePostRequest(url: String, parameters: Map<String, String?>): Array<String> {
        val fields = parameters.map { (name, value) -> BasicNameValuePair(name, value) }
        val request = HttpPost(url).apply {
            entity = UrlEncodedFormEntity(fields, StringUtil.ENCODING_UTF8)
            config = requestConfig
        }
        return executeRequest(request)
    }

    private fun executeRequest(request: HttpUriRequest): Array<String> = HttpClients.createDefault().use { client ->
        val response = client.execute(request, BasicResponseHandler())
        java.util.regex.Pattern.compile("\\r?\\n").split(response)
    }

    private fun handleNetworkError(registration: LastFmRegistration?, message: String) {
        try {
            queue.put(registration!!)
            LOG.info("Last.fm registration for '{}' encountered network error: {}. Will try again later. In queue: {}",
                registration.title, message, queue.size)
        } catch (failure: InterruptedException) {
            LOG.error("Failed to reschedule Last.fm registration for '{}': {}", registration?.title, failure.toString())
        }
        try { Thread.sleep(60_000L) } catch (failure: InterruptedException) {
            LOG.error("Failed to sleep after Last.fm registration failure for '{}': {}", registration?.title, failure.toString())
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LastFMScrobbler::class.java)
        private const val MAX_PENDING_REGISTRATION = 2000
    }
}
