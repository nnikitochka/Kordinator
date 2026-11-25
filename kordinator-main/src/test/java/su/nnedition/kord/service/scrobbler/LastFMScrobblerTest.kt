package su.nnedition.kord.service.scrobbler

import com.sun.net.httpserver.HttpServer
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class LastFMScrobblerTest {
    @Test
    fun performsHandshakeAndSendsSubmissionAndNowPlaying() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val queries = CopyOnWriteArrayList<Map<String, String>>()
        val posted = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        server.createContext("/handshake") { exchange ->
            queries.add(decode(exchange.requestURI.rawQuery))
            val base = "http://127.0.0.1:${server.address.port}"
            val response = "OK\nsession-key\n$base/now\n$base/submit\n".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        for (path in listOf("/now", "/submit")) server.createContext(path) { exchange ->
            posted.add(path to decode(exchange.requestBody.bufferedReader().use { it.readText() }))
            val response = "OK\n".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val instance = LastFMScrobbler(URI("http://127.0.0.1:${server.address.port}/handshake"))
            val method = LastFMScrobbler::class.java.getDeclaredMethod("scrobble", LastFmRegistration::class.java)
            method.isAccessible = true
            val registration = LastFmRegistration("alice", "password", "Artist", "Album", "Song", 125,
                Instant.ofEpochSecond(12345), true)
            method.invoke(instance, registration)
            method.invoke(instance, registration.copy(submission = false))
            assertEquals(2, queries.size)
            val query = queries.first()
            assertEquals("alice", query["u"])
            assertEquals(DigestUtils.md5Hex(DigestUtils.md5Hex("password") + query["t"]), query["a"])
            assertEquals("/submit", posted[0].first)
            assertEquals("session-key", posted[0].second["s"])
            assertEquals("12345", posted[0].second["i[0]"])
            assertEquals("Song", posted[0].second["t[0]"])
            assertEquals("/now", posted[1].first)
            assertEquals("Song", posted[1].second["t"])
            assertFalse(posted[1].second.containsKey("i[0]"))
        } finally {
            server.stop(0)
        }
    }

    private fun decode(value: String): Map<String, String> = value.split('&').associate { part ->
        val pair = part.split('=', limit = 2)
        URLDecoder.decode(pair[0], StandardCharsets.UTF_8) to URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
    }
}
