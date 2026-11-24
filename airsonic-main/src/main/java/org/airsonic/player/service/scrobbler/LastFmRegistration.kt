package org.airsonic.player.service.scrobbler

import java.time.Instant

internal data class LastFmRegistration(
    val username: String?,
    val password: String?,
    val artist: String?,
    val album: String?,
    val title: String?,
    val duration: Int,
    val time: Instant,
    val submission: Boolean,
) {
    fun parameters(session: String): Map<String, String?> = if (submission) mapOf(
        "s" to session, "a[0]" to artist, "t[0]" to title, "i[0]" to time.epochSecond.toString(),
        "o[0]" to "P", "r[0]" to "", "l[0]" to duration.toString(), "b[0]" to album,
        "n[0]" to "", "m[0]" to "",
    ) else mapOf(
        "s" to session, "a" to artist, "t" to title, "b" to album, "l" to duration.toString(), "n" to "", "m" to "",
    )
}
