package org.airsonic.player.service

import com.fasterxml.jackson.core.type.TypeReference
import org.airsonic.player.domain.Version
import org.airsonic.player.util.Util
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpEntity
import org.apache.http.client.ResponseHandler
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.impl.client.AbstractResponseHandler
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Enumeration
import java.util.Properties

/** Provides local build information and checks for newer published releases. */
class VersionService @Throws(IOException::class) constructor() {
    private val build: Properties
    private var resolvedLocalVersion: Version? = null
    private var latestFinalVersion: Version? = null
    private var latestBetaVersion: Version? = null
    private var localBuildDate: Instant? = null
    private var localBuildNumber: String? = null
    private var lastVersionFetched: Long = 0

    init {
        val properties = Properties()
        VersionService::class.java.classLoader?.let { classLoader ->
            val urls: Enumeration<URL> = classLoader.getResources("build.properties")
            while (urls.hasMoreElements()) {
                urls.nextElement().openStream().use(properties::load)
            }
        }
        build = properties
    }

    fun getLocalVersion(): Version? {
        if (resolvedLocalVersion == null) {
            try {
                resolvedLocalVersion = Version("${build.getProperty("version")}.${build.getProperty("timestamp")}")
                LOG.info("Resolved local Airsonic version to: {}", resolvedLocalVersion)
            } catch (failure: Exception) {
                LOG.warn("Failed to resolve local Airsonic version.", failure)
            }
        }
        return resolvedLocalVersion
    }

    @Synchronized
    fun getLatestFinalVersion(): Version? {
        refreshLatestVersion()
        return latestFinalVersion
    }

    @Synchronized
    fun getLatestBetaVersion(): Version? {
        refreshLatestVersion()
        return latestBetaVersion
    }

    fun getLocalBuildDate(): Instant? {
        if (localBuildDate == null) {
            try {
                localBuildDate = DATE_FORMAT.parse(build.getProperty("timestamp"), Instant::from)
            } catch (failure: Exception) {
                LOG.warn("Failed to resolve local Airsonic build date.", failure)
            }
        }
        return localBuildDate
    }

    fun getLocalBuildNumber(): String? {
        if (localBuildNumber == null) {
            try {
                localBuildNumber = build.getProperty("revision")
            } catch (failure: Exception) {
                LOG.warn("Failed to resolve local Airsonic build commit.", failure)
            }
        }
        return localBuildNumber
    }

    fun isNewFinalVersionAvailable(): Boolean {
        val latest = getLatestFinalVersion() ?: return false
        val local = getLocalVersion() ?: return false
        return local.compareTo(latest) < 0
    }

    fun isNewBetaVersionAvailable(): Boolean {
        val latest = getLatestBetaVersion() ?: return false
        val local = getLocalVersion() ?: return false
        return local.compareTo(latest) < 0
    }

    private fun refreshLatestVersion() {
        val now = System.currentTimeMillis()
        if (now - lastVersionFetched > LAST_VERSION_FETCH_INTERVAL) {
            try {
                lastVersionFetched = now
                readLatestVersion()
            } catch (failure: Exception) {
                LOG.warn("Failed to resolve latest Airsonic version.", failure)
            }
        }
    }

    @Throws(IOException::class)
    private fun readLatestVersion() {
        LOG.debug("Starting to read latest version")
        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(10_000)
            .setSocketTimeout(10_000)
            .build()
        val request = HttpGet(VERSION_URL).apply {
            config = requestConfig
            setHeader("accept", "application/vnd.github.v3+json")
            setHeader("User-Agent", "Airsonic/${getLocalVersion()}")
        }

        val content: List<Map<String, Any?>> = try {
            HttpClients.createDefault().use { client -> client.execute(request, RESPONSE_HANDLER) }
        } catch (failure: ConnectTimeoutException) {
            LOG.warn("Got a timeout when trying to reach {}", VERSION_URL)
            return
        }

        val releases = content.sortedByDescending { Instant.parse(it["published_at"] as String) }
        val betaRelease = releases.firstOrNull()
        val finalRelease = releases.firstOrNull { release ->
            !(release["draft"] as Boolean) && !(release["prerelease"] as Boolean)
        }
        val localRelease = releases.firstOrNull { release ->
            StringUtils.equals(build.getProperty("version") + "." + build.getProperty("timestamp"), release["tag_name"] as String?) ||
                StringUtils.equals(build.getProperty("version"), release["tag_name"] as String?)
        }

        LOG.debug("Got {} for beta version", betaRelease?.get("tag_name"))
        LOG.debug("Got {} for final version", finalRelease?.get("tag_name"))
        latestBetaVersion = betaRelease?.let(::releaseToVersion)
        latestFinalVersion = finalRelease?.let(::releaseToVersion)
        resolvedLocalVersion = localRelease?.let(::releaseToVersion) ?: resolvedLocalVersion
    }

    private fun releaseToVersion(release: Map<String, Any?>): Version {
        @Suppress("UNCHECKED_CAST")
        val assets = release["assets"] as List<Map<String, Any>>?
        return Version(
            release["tag_name"] as String,
            release["target_commitish"] as String?,
            (release["draft"] as Boolean) || (release["prerelease"] as Boolean),
            release["html_url"] as String?,
            Instant.parse(release["published_at"] as String),
            Instant.parse(release["created_at"] as String),
            assets,
        )
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(VersionService::class.java)
        private const val VERSION_URL = "https://api.github.com/repos/airsonic-advanced/airsonic-advanced/releases"
        private const val LAST_VERSION_FETCH_INTERVAL = 7L * 24L * 3600L * 1000L
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("UTC"))
        private val RESPONSE_HANDLER: ResponseHandler<List<Map<String, Any?>>> =
            object : AbstractResponseHandler<List<Map<String, Any?>>>() {
                override fun handleEntity(entity: HttpEntity): List<Map<String, Any?>> =
                    entity.content.use { input ->
                        BufferedInputStream(input).use { buffered ->
                            Util.getObjectMapper().readValue(buffered, object : TypeReference<List<Map<String, Any?>>>() {})
                        }
                    }
            }
    }
}
