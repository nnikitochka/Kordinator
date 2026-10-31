package org.airsonic.player.controller.rest.lyrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.ajax.lyrics.LyricsInfo;
import org.airsonic.player.ajax.lyrics.LyricsWSController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Lyrics;
import org.subsonic.restapi.Response;

@Controller("restLyricsController")
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class LyricsController {
    private final RestPlayerResolver resolver;
    private final LyricsWSController lyricsWSController;
    private final JAXBWriter writer;

    public LyricsController(RestPlayerResolver resolver, LyricsWSController lyricsWSController, JAXBWriter writer) {
        this.resolver = resolver;
        this.lyricsWSController = lyricsWSController;
        this.writer = writer;
    }

    @RequestMapping("/getLyrics")
    public void getLyrics(HttpServletRequest request, HttpServletResponse response) {
        resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        String artist = request.getParameter("artist");
        String title = request.getParameter("title");
        LyricsInfo lyrics = lyricsWSController.getLyrics(artist, title);

        Lyrics result = new Lyrics();
        result.setArtist(lyrics.getArtist());
        result.setTitle(lyrics.getTitle());
        result.setContent(lyrics.getLyrics());

        Response res = writer.createResponse(true);
        res.setLyrics(result);
        writer.writeResponse(request, response, res);
    }
}
