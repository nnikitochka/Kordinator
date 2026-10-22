package org.airsonic.player.ajax.lyrics;

import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.io.StringReader;
import java.net.SocketException;

import static org.airsonic.player.util.XMLUtil.createSAXBuilder;

/**
 * Provides services for retrieving song lyrics from chartlyrics.com.
 * <p/>
 * See http://www.chartlyrics.com/api.aspx for details.
 *
 */
@Controller
@MessageMapping("/lyrics")
public class LyricsWSController {

    private static final Logger LOG = LoggerFactory.getLogger(LyricsWSController.class);

    /**
     * Returns lyrics for the given song and artist.
     *
     * @return The lyrics, never <code>null</code> .
     */
    @MessageMapping("/get")
    @SendToUser(broadcast = false)
    public LyricsInfo getLyrics(LyricsGetRequest req) {
        return getLyrics(req.getArtist(), req.getSong());
    }

    public LyricsInfo getLyrics(String artist, String song) {
        LyricsInfo lyrics = new LyricsInfo();
        try {

            artist = StringUtil.urlEncode(artist);
            song = StringUtil.urlEncode(song);

            String url = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect?artist=" + artist + "&song=" + song;
            String xml = executeGetRequest(url);
            lyrics = parseSearchResult(xml);

        } catch (HttpResponseException x) {
            LOG.warn("Failed to get lyrics for song '{}'. Request failed: {}", song, x.toString());
            if (x.getStatusCode() == 503) {
                lyrics.setTryLater(true);
            }
        } catch (SocketException | ConnectTimeoutException x) {
            LOG.warn("Failed to get lyrics for song '{}': {}", song, x.toString());
            lyrics.setTryLater(true);
        } catch (Exception x) {
            LOG.warn("Failed to get lyrics for song '" + song + "'.", x);
        }
        return lyrics;
    }

    private LyricsInfo parseSearchResult(String xml) throws Exception {
        SAXBuilder builder = createSAXBuilder();
        Document document = builder.build(new StringReader(xml));

        Element root = document.getRootElement();
        Namespace ns = root.getNamespace();

        String lyric = StringUtils.trimToNull(root.getChildText("Lyric", ns));
        String song = root.getChildText("LyricSong", ns);
        String artist = root.getChildText("LyricArtist", ns);

        return new LyricsInfo(lyric, artist, song);
    }

    private String executeGetRequest(String url) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15000)
                .setSocketTimeout(15000)
                .build();
        HttpGet method = new HttpGet(url);
        method.setConfig(requestConfig);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            return client.execute(method, responseHandler);
        }
    }

}
