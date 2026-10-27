package org.airsonic.player.controller.rest.genres

import org.airsonic.player.domain.Genre

fun interface GenreLookup {
    fun getGenres(): List<Genre>
}