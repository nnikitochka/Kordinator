package org.airsonic.player.removal;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.NewestPodcasts;
import org.subsonic.restapi.Podcasts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;

/**
 * PLANNED REMOVAL.
 * <p>
 * Podcast support has been removed from this fork. The Subsonic REST endpoints below were
 * kept, mapped to stub responses, only so that existing clients do not break outright:
 * <ul>
 * <li>the getters ({@code getPodcasts}, {@code getNewestPodcasts}, {@code exportPodcasts/opml})
 * still answer with valid but empty payloads,</li>
 * <li>the state-changing endpoints answer with {@code NOT_AUTHORIZED}.</li>
 * </ul>
 * <p>
 * Any further change to the REST API contract may delete this controller and the whole
 * {@code org.airsonic.player.removal} package (including {@link PodcastExportOPML}).
 */
@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class PodcastRemovalController {

    private static final String PODCASTS_NOT_SUPPORTED = "Podcast support has been removed from this Airsonic instance";

    private final JAXBWriter jaxbWriter = new JAXBWriter();

    @RequestMapping("/getPodcasts")
    public void getPodcasts(HttpServletRequest request, HttpServletResponse response) {
        org.subsonic.restapi.Response res = jaxbWriter.createResponse(true);
        res.setPodcasts(new Podcasts());
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/getNewestPodcasts")
    public void getNewestPodcasts(HttpServletRequest request, HttpServletResponse response) {
        org.subsonic.restapi.Response res = jaxbWriter.createResponse(true);
        res.setNewestPodcasts(new NewestPodcasts());
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/refreshPodcasts")
    public void refreshPodcasts(HttpServletRequest request, HttpServletResponse response) {
        notSupported(request, response);
    }

    @RequestMapping("/createPodcastChannel")
    public void createPodcastChannel(HttpServletRequest request, HttpServletResponse response) {
        notSupported(request, response);
    }

    @RequestMapping("/deletePodcastChannel")
    public void deletePodcastChannel(HttpServletRequest request, HttpServletResponse response) {
        notSupported(request, response);
    }

    @RequestMapping("/deletePodcastEpisode")
    public void deletePodcastEpisode(HttpServletRequest request, HttpServletResponse response) {
        notSupported(request, response);
    }

    @RequestMapping("/downloadPodcastEpisode")
    public void downloadPodcastEpisode(HttpServletRequest request, HttpServletResponse response) {
        notSupported(request, response);
    }

    @RequestMapping("/exportPodcasts/opml")
    public ResponseEntity<PodcastExportOPML> exportPodcastOPML(HttpServletRequest request, HttpServletResponse response) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.builder("attachment").filename("airsonic.opml", StandardCharsets.UTF_8).build());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_XML);
        return ResponseEntity.ok().headers(headers).body(new PodcastExportOPML());
    }

    private void notSupported(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeErrorResponse(request, response, ErrorCode.NOT_AUTHORIZED,
                "This operation is not authorized: " + PODCASTS_NOT_SUPPORTED + ".");
    }
}
