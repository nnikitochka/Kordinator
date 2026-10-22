package org.airsonic.player.ajax.queue;

import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.NetworkService;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.spring.WebsocketConfiguration;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@Controller
@MessageMapping("/playqueues/{playerId}")
public class PlayQueueWSController {
    private final PlayerService playerService;
    private final PlayQueueService playQueueService;

    public PlayQueueWSController(PlayerService playerService, PlayQueueService playQueueService) {
        this.playerService = playerService;
        this.playQueueService = playQueueService;
    }

    @SubscribeMapping("/get")
    public PlayQueueInfo getPlayQueue(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        String baseUrl = NetworkService.getBaseUrl(headers);
        return playQueueService.getPlayQueueInfo(player, baseUrl);
    }

    @MessageMapping("/start")
    public void start(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.start(player);
    }

    @MessageMapping("/stop")
    public void stop(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.stop(player);
    }

    @MessageMapping("/toggleStartStop")
    public void toggleStartStop(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.toggleStartStop(player);
    }

    @MessageMapping("/skip")
    public void skip(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.skip(player, req.getIndex(), req.getOffset());
    }

    @MessageMapping("/reloadsearch")
    public void reloadSearchCriteria(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.reloadSearchCriteria(player, headers.getSessionId());
    }

    @MessageMapping("/save")
    @SendToUser
    public int savePlayQueue(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        return playQueueService.savePlayQueue(player, req.getIndex(), req.getOffset());
    }

    @MessageMapping("/play/saved")
    public void loadSavedPlayQueue(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.loadSavedPlayQueue(player, headers.getSessionId());
    }

    @MessageMapping("/play/mediafile")
    public void playMediaFile(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playMediaFile(player, req.getId(), headers.getSessionId());
    }

    @MessageMapping("/play/playlist")
    public void playPlaylist(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playPlaylist(player, req.getId(), req.getIndex(), headers.getSessionId());
    }

    @MessageMapping("/play/topsongs")
    public void playTopSong(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playTopSong(player, req.getId(), req.getIndex(), headers.getSessionId());
    }

    @MessageMapping("/play/starred")
    public void playStarred(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playStarred(player, headers.getSessionId());
    }

    @MessageMapping("/play/shuffle")
    public void playShuffle(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playShuffle(player, req.getAlbumListType(), (int) req.getOffset(), req.getCount(),
                req.getGenre(), req.getDecade(), headers.getSessionId());
    }

    @MessageMapping("/play/random")
    public void playRandom(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playRandom(player, req.getId(), req.getCount(), headers.getSessionId());
    }

    @MessageMapping("/play/similar")
    public void playSimilar(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playSimilar(player, req.getId(), req.getCount(), headers.getSessionId());
    }

    @MessageMapping("/add")
    public void add(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.add(player, req.getIds(), req.getIndex(), player.isWeb(), true);
    }

    @MessageMapping("/add/playlist")
    public void addPlaylist(@DestinationVariable int playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.addPlaylist(player, req.getId(), player.isWeb());
    }

    @MessageMapping("/clear")
    public void clear(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.clear(player);
    }

    @MessageMapping("/shuffle")
    public void shuffle(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.shuffle(player);
    }

    @MessageMapping("/remove")
    public void remove(@DestinationVariable int playerId, List<Integer> indices, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.remove(player, indices);
    }

    @MessageMapping("/rearrange")
    public void rearrange(@DestinationVariable int playerId, List<Integer> indices, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.rearrange(player, indices);
    }

    @MessageMapping("/up")
    public void up(@DestinationVariable int playerId, int index, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.up(player, index);
    }

    @MessageMapping("/down")
    public void down(@DestinationVariable int playerId, int index, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.down(player, index);
    }

    @MessageMapping("/toggleRepeat")
    public void toggleRepeat(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.toggleRepeat(player);
    }

    @MessageMapping("/undo")
    public void undo(@DestinationVariable int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.undo(player);
    }

    @MessageMapping("/sort")
    public void sort(@DestinationVariable int playerId, PlayQueue.SortOrder order, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.sort(player, order);
    }

    //
    // Methods dedicated to jukebox
    //
    @MessageMapping("/jukebox/gain")
    public void setJukeboxGain(@DestinationVariable int playerId, float gain, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.setJukeboxGain(player, gain);
    }

    @MessageMapping("/jukebox/position")
    public void setJukeboxPosition(@DestinationVariable int playerId, int positionInSeconds, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.setJukeboxPosition(player, positionInSeconds);
    }

    //
    // End : Methods dedicated to jukebox
    //

    private Player getPlayer(int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        return playerService.getPlayer((HttpServletRequest) headers.getSessionAttributes().get(WebsocketConfiguration.UNDERLYING_SERVLET_REQUEST), null, playerId, true, false);
    }
}
