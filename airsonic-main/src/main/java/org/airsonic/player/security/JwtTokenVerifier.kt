package org.airsonic.player.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.net.URISyntaxException
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Objects
import java.util.function.Consumer

/** Framework-neutral signature and expiry verification for signed JWTs. */
class JwtTokenVerifier {
    private val additionalChecks = HashMap<String, MutableList<Consumer<DecodedJWT>>>()

    fun verify(signingKey: String, rawToken: String?): Result {
        Objects.requireNonNull(signingKey, "signingKey")
        if (rawToken.isNullOrBlank()) return Result.failed(Failure.INVALID)
        return try {
            Result.verified(JWT.require(Algorithm.HMAC256(signingKey)).build().verify(rawToken))
        } catch (_: TokenExpiredException) {
            Result.failed(Failure.EXPIRED)
        } catch (_: RuntimeException) {
            Result.failed(Failure.INVALID)
        }
    }

    /** Verifies token signature/expiry, request URL binding, then registered claim checks. */
    fun verifyRequest(signingKey: String, rawToken: String?, requestedPath: String?): Result {
        val result = verify(signingKey, rawToken)
        if (!result.isVerified()) return result
        val token = result.getToken()
        val claimedPath = token.getClaim(PATH_CLAIM).asString()
        if (!roughlyEqual(claimedPath, requestedPath)) return Result.failed(Failure.PATH_MISMATCH)

        val checks = additionalChecks[parseUri(requestedPath).path]
        checks?.forEach { check -> check.accept(token) }
        return result
    }

    fun addAdditionalCheck(path: String, check: Consumer<DecodedJWT>) {
        additionalChecks.computeIfAbsent(path) { ArrayList() }.add(check)
    }

    enum class Failure { EXPIRED, INVALID, PATH_MISMATCH }

    class Result private constructor(private val token: DecodedJWT?, private val failure: Failure?) {
        fun isVerified(): Boolean = token != null

        fun getToken(): DecodedJWT = token ?: throw IllegalStateException("JWT verification failed: $failure")

        fun getFailure(): Failure? = failure

        companion object {
            @JvmStatic
            fun verified(token: DecodedJWT): Result =
                Result(Objects.requireNonNull(token, "token"), null)

            @JvmStatic
            fun failed(failure: Failure): Result =
                Result(null, Objects.requireNonNull(failure, "failure"))
        }
    }

    companion object {
        private const val PATH_CLAIM = "path"
        private const val JWT_PARAMETER = "jwt"

        @JvmStatic
        fun roughlyEqual(expectedRaw: String?, requestedPathRaw: String?): Boolean {
            if (StringUtils.isEmpty(expectedRaw)) return false
            return try {
                val expected = parseUri(expectedRaw)
                val requested = parseUri(requestedPathRaw)
                if (expected.path != requested.path) return false
                val left = HashMap(expected.queryParams)
                val right = HashMap(requested.queryParams)
                left.remove("size")
                right.remove("size")
                val difference: MapDifference<String, List<String?>> = Maps.difference(left, right)
                difference.entriesDiffering().isEmpty() || difference.entriesOnlyOnLeft().isEmpty() ||
                    (difference.entriesOnlyOnRight().size == 1 &&
                        difference.entriesOnlyOnRight()[JWT_PARAMETER] != null)
            } catch (_: Exception) {
                false
            }
        }

        private fun parseUri(rawUri: String?): ParsedUri {
            if (rawUri == null) throw IllegalArgumentException("URI must not be null")
            try {
                val uri = URI(rawUri)
                return ParsedUri(uri.rawPath ?: "", parseQuery(uri.rawQuery))
            } catch (_: URISyntaxException) {
                var withoutFragment = rawUri
                val fragmentIndex = withoutFragment.indexOf('#')
                if (fragmentIndex >= 0) withoutFragment = withoutFragment.substring(0, fragmentIndex)
                val queryIndex = withoutFragment.indexOf('?')
                var rawPath = if (queryIndex < 0) withoutFragment else withoutFragment.substring(0, queryIndex)
                val rawQuery = if (queryIndex < 0) null else withoutFragment.substring(queryIndex + 1)
                val schemeIndex = rawPath.indexOf(':')
                if (schemeIndex > 0) rawPath = rawPath.substring(schemeIndex + 1)
                if (rawPath.startsWith("//")) {
                    val pathIndex = rawPath.indexOf('/', 2)
                    rawPath = if (pathIndex < 0) "" else rawPath.substring(pathIndex)
                }
                return ParsedUri(rawPath, parseQuery(rawQuery))
            }
        }

        private fun parseQuery(rawQuery: String?): Map<String, List<String?>> {
            val queryParams = LinkedHashMap<String, MutableList<String?>>()
            if (rawQuery != null) {
                for (parameter in rawQuery.split("&")) {
                    val equalsIndex = parameter.indexOf('=')
                    val name = if (equalsIndex < 0) parameter else parameter.substring(0, equalsIndex)
                    val value = if (equalsIndex < 0) null else parameter.substring(equalsIndex + 1)
                    queryParams.computeIfAbsent(name) { ArrayList() }.add(value)
                }
            }
            return queryParams
        }

        private data class ParsedUri(val path: String, val queryParams: Map<String, List<String?>>)
    }
}
