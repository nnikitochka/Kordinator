package org.airsonic.player.service.scrobbler

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.util.Util
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue

/** Queues ListenBrainz submissions and retries network failures. */
open class ListenBrainzScrobbler {
    private var thread: Thread? = null
    private val queue = LinkedBlockingQueue<ListenBrainzRegistration>()

    @Synchronized
    open fun register(mediaFile: MediaFile, url: String?, token: String?, submission: Boolean, time: Instant?) {
        if (thread == null) {
            thread = Thread({ processQueue() }, "ListenBrainzScrobbler Registration").also { it.start() }
        }
        if (queue.size >= MAX_PENDING_REGISTRATION) {
            LOG.warn("ListenBrainz scrobbler queue is full. Ignoring '{}'", mediaFile.title)
            return
        }
        val registration = ListenBrainzRegistration(
            url ?: DEFAULT_URL, token, mediaFile.artist, mediaFile.albumName, mediaFile.title,
            mediaFile.musicBrainzReleaseId, mediaFile.musicBrainzRecordingId, mediaFile.trackNumber,
            mediaFile.duration?.let { Math.round(it).toInt() } ?: 0, time ?: Instant.now(), submission,
        )
        try {
            queue.put(registration)
        } catch (failure: InterruptedException) {
            LOG.warn("Interrupted while queuing ListenBrainz scrobble", failure)
        }
    }

    private fun processQueue() {
        while (true) {
            var registration: ListenBrainzRegistration? = null
            try {
                registration = queue.take()
                scrobble(registration)
            } catch (_: ClientProtocolException) {
                // Preserve the existing handling of protocol errors.
            } catch (failure: IOException) {
                handleNetworkError(registration, failure)
            } catch (failure: Exception) {
                LOG.warn("Error in ListenBrainz registration: {}", failure.toString())
            }
        }
    }

    private fun scrobble(registration: ListenBrainzRegistration) {
        val token = registration.token ?: return
        val request = HttpPost(registration.url)
        request.entity = StringEntity(Util.toJson(registration.payload()), "UTF-8")
        request.setHeader("Authorization", "token $token")
        request.setHeader("Content-type", "application/json; charset=utf-8")
        val success = HttpClients.createDefault().use { client ->
            client.execute(request).use { response ->
                val ok = response.statusLine.statusCode == 200
                if (!ok) LOG.warn("Failed to execute ListenBrainz request: {}", response.entity.toString())
                ok
            }
        }
        if (!success) LOG.warn("Failed to scrobble song '{}' at ListenBrainz ({}).", registration.title, registration.url)
        else LOG.info("Successfully registered {} for song '{}' at ListenBrainz ({}): {}",
            if (registration.submission) "submission" else "now playing", registration.title, registration.url, registration.time)
    }

    private fun handleNetworkError(registration: ListenBrainzRegistration?, failure: Exception) {
        try {
            queue.put(registration!!)
            LOG.info("ListenBrainz registration for '{}' encountered network error. Will try again later. In queue: {}",
                registration.title, queue.size, failure)
        } catch (interrupted: InterruptedException) {
            LOG.error("Failed to reschedule ListenBrainz registration for '{}'", registration?.title, interrupted)
        }
        try {
            Thread.sleep(60_000L)
        } catch (interrupted: InterruptedException) {
            LOG.error("Failed to sleep after ListenBrainz registration failure for '{}'", registration?.title, interrupted)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ListenBrainzScrobbler::class.java)
        private const val MAX_PENDING_REGISTRATION = 2000
        private const val DEFAULT_URL = "https://api.listenbrainz.org/1/submit-listens"
    }
}
