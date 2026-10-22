package org.airsonic.player.ajax.playing;

import org.airsonic.player.service.StatusService;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class NowPlayingWSController {
    private final StatusService statusService;

    public NowPlayingWSController(StatusService statusService) {
        this.statusService = statusService;
    }

    @SubscribeMapping("/nowPlaying/current")
    public List<NowPlayingInfo> getActivePlays() {
        return statusService.getActivePlays();
    }

    @SubscribeMapping("/nowPlaying/recent")
    public List<NowPlayingInfo> getInactivePlays() {
        return statusService.getInactivePlays();
    }
}
