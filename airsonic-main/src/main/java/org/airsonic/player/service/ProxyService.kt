package org.airsonic.player.service

import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import java.io.OutputStream

/** Performs the outbound HTTP GET used by the legacy website proxy. */
class ProxyService {
    fun get(url: String): Response {
        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(TIMEOUT_MILLIS)
            .setSocketTimeout(TIMEOUT_MILLIS)
            .build()
        val request = HttpGet(url).apply { config = requestConfig }
        val client = HttpClients.createDefault()
        return try {
            Response(client, client.execute(request))
        } catch (error: Throwable) {
            client.close()
            throw error
        }
    }

    class Response internal constructor(
        private val client: CloseableHttpClient,
        private val response: CloseableHttpResponse
    ) : AutoCloseable {
        val statusCode: Int get() = response.statusLine.statusCode

        fun copyBodyTo(output: OutputStream) {
            response.entity?.content?.use { it.copyTo(output) }
        }

        override fun close() {
            try {
                response.close()
            } finally {
                client.close()
            }
        }
    }

    companion object {
        private const val TIMEOUT_MILLIS = 15_000
    }
}
