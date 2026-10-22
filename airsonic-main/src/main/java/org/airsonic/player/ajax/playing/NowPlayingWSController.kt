package org.airsonic.player.ajax.playing

import org.airsonic.player.service.StatusService
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller

@Controller
class NowPlayingWSController(
    private val statusService: StatusService
) {

    @SubscribeMapping("/nowPlaying/current")
    fun getActivePlays(): MutableList<NowPlayingInfo> = statusService.activePlays

    @SubscribeMapping("/nowPlaying/recent")
    fun getInactivePlays(): MutableList<NowPlayingInfo> = statusService.inactivePlays
}
