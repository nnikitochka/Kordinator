package org.airsonic.player.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Date

open class JWTSecurityService(private val settingsService: SettingsService) {

    open fun addJWTToken(user: String, uri: String): String =
        addJWTToken(user, uri, null, Collections.emptyMap())

    open fun addJWTToken(user: String, uri: String, expires: Instant?): String =
        addJWTToken(user, uri, expires, Collections.emptyMap())

    open fun addJWTToken(user: String, uri: String, additionalClaims: Map<String, String>): String =
        addJWTToken(user, uri, null, additionalClaims)

    open fun addJWTToken(
        user: String,
        uri: String,
        expires: Instant?,
        additionalClaims: Map<String, String>
    ): String {
        val expiration = expires ?: Instant.now().plus(DEFAULT_DAYS_VALID_FOR.toLong(), ChronoUnit.DAYS)
        val token = createToken(settingsService.jwtKey, user, uri, expiration, additionalClaims)
        return appendQueryParameter(uri, JWT_PARAM_NAME, token)
    }

    open fun verify(credentials: String): DecodedJWT = verify(settingsService.jwtKey, credentials)

    companion object {
        const val JWT_PARAM_NAME = "jwt"
        const val CLAIM_PATH = "path"
        const val DEFAULT_DAYS_VALID_FOR = 7
        private val LOG = LoggerFactory.getLogger(JWTSecurityService::class.java)
        private val secureRandom = SecureRandom()

        @JvmStatic
        fun generateKey(): String = BigInteger(130, secureRandom).toString(32)

        @JvmStatic
        fun getAlgorithm(jwtKey: String): Algorithm = Algorithm.HMAC256(jwtKey)

        @JvmStatic
        fun verify(jwtKey: String, token: String): DecodedJWT {
            val algorithm = getAlgorithm(jwtKey)
            val verifier: JWTVerifier = JWT.require(algorithm).build()
            return verifier.verify(token)
        }

        @JvmStatic
        fun decode(token: String): DecodedJWT = JWT.decode(token)

        @JvmStatic
        fun initializeSigningKey(settingsService: SettingsService) {
            if (StringUtils.isBlank(settingsService.jwtKey)) {
                LOG.warn("Generating new jwt key")
                settingsService.jwtKey = generateKey()
                settingsService.save()
            }
        }

        private fun createToken(
            jwtKey: String,
            user: String,
            path: String,
            expireDate: Instant,
            additionalClaims: Map<String, String>
        ): String {
            val pathClaim = createPathClaim(path)
            var builder = JWT.create()
                .withIssuer("airsonic")
                .withSubject(user)
                .withClaim(CLAIM_PATH, pathClaim)
                .withExpiresAt(Date.from(expireDate))

            additionalClaims.forEach { (key, value) -> builder = builder.withClaim(key, value) }
            return builder.sign(getAlgorithm(jwtKey))
        }

        private fun createPathClaim(rawUri: String): String {
            val uri = parseUri(rawUri)
            try {
                // Reconstructing from raw URI components encodes percent signs in values that
                // were already escaped, preserving URL-encoding compatibility.
                val encoded = URI(uri.scheme, uri.authority, uri.path, uri.query, null)
                val path = encoded.rawPath ?: ""
                val query = encoded.rawQuery
                return path + if (!StringUtils.isBlank(query)) "?$query" else ""
            } catch (ex: URISyntaxException) {
                throw IllegalArgumentException("Invalid URI: $rawUri", ex)
            }
        }

        private fun parseUri(rawUri: String?): RawUri {
            if (rawUri == null) throw IllegalArgumentException("URI must not be null")
            try {
                val uri = URI(rawUri)
                val path = if (uri.isOpaque) uri.rawSchemeSpecificPart else uri.rawPath
                return RawUri(uri.scheme, uri.rawAuthority, path, uri.rawQuery)
            } catch (ex: URISyntaxException) {
                // Preserve raw pieces here; createPathClaim performs the one encoding pass that
                // Spring's toUriString() performs, including for malformed percent escapes.
                var withoutFragment = rawUri
                val fragmentIndex = withoutFragment.indexOf('#')
                if (fragmentIndex >= 0) withoutFragment = withoutFragment.substring(0, fragmentIndex)
                val queryIndex = withoutFragment.indexOf('?')
                val rawQuery = if (queryIndex < 0) null else withoutFragment.substring(queryIndex + 1)
                var rawPath = if (queryIndex < 0) withoutFragment else withoutFragment.substring(0, queryIndex)
                var scheme: String? = null
                val colonIndex = rawPath.indexOf(':')
                if (colonIndex > 0) {
                    scheme = rawPath.substring(0, colonIndex)
                    rawPath = rawPath.substring(colonIndex + 1)
                }
                var authority: String? = null
                if (rawPath.startsWith("//")) {
                    val authorityEnd = rawPath.indexOf('/', 2)
                    authority = if (authorityEnd < 0) rawPath.substring(2) else rawPath.substring(2, authorityEnd)
                    rawPath = if (authorityEnd < 0) "" else rawPath.substring(authorityEnd)
                }
                return RawUri(scheme, authority, rawPath, rawQuery)
            }
        }

        private fun appendQueryParameter(rawUri: String, name: String, value: String): String {
            val fragmentIndex = rawUri.indexOf('#')
            val beforeFragment = if (fragmentIndex < 0) rawUri else rawUri.substring(0, fragmentIndex)
            val fragment = if (fragmentIndex < 0) "" else rawUri.substring(fragmentIndex)
            val queryIndex = beforeFragment.indexOf('?')
            val separator = when {
                queryIndex < 0 -> "?"
                queryIndex == beforeFragment.length - 1 -> ""
                else -> "&"
            }
            return "$beforeFragment$separator$name=$value$fragment"
        }

        private data class RawUri(val scheme: String?, val authority: String?, val path: String?, val query: String?)
    }
}
