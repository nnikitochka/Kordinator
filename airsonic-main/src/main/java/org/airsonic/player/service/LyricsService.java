package org.airsonic.player.service;

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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static org.airsonic.player.util.XMLUtil.createSAXBuilder;

/**
 * Provides services for retrieving song lyrics from chartlyrics.com.
 * <p/>
 * See http://www.chartlyrics.com/api.aspx for details.
 *
 */
@Service
public class LyricsService {
    private static final Logger LOG = LoggerFactory.getLogger(LyricsService.class);
    private final Fetcher fetcher;

    public LyricsService() {
        this(LyricsService::executeGetRequest);
    }

    LyricsService(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @FunctionalInterface
    interface Fetcher {
        String get(String url) throws IOException;
    }

    public record Result(String lyrics, String artist, String title, boolean tryLater) {
        static Result empty(boolean tryLater) {
            return new Result(null, null, null, tryLater);
        }
    }

    public Result getLyrics(String artist, String song) {
        if (artist == null || song == null) {
            return Result.empty(false);
        }
        try {

            artist = StringUtil.urlEncode(artist);
            song = StringUtil.urlEncode(song);

            String url = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect?artist=" + artist + "&song=" + song;
            String xml = fetcher.get(url);
            return parseSearchResult(xml);

        } catch (HttpResponseException x) {
            LOG.warn("Failed to get lyrics for song '{}'. Request failed: {}", song, x.toString());
            if (x.getStatusCode() == 503) {
                return Result.empty(true);
            }
        } catch (SocketException | SocketTimeoutException | ConnectTimeoutException x) {
            LOG.warn("Failed to get lyrics for song '{}': {}", song, x.toString());
            return Result.empty(true);
        } catch (Exception x) {
            LOG.warn("Failed to get lyrics for song '{}'.", song, x);
        }
        return Result.empty(false);
    }

    private Result parseSearchResult(String xml) throws Exception {
        SAXBuilder builder = createSAXBuilder();
        Document document = builder.build(new StringReader(xml));

        Element root = document.getRootElement();
        Namespace ns = root.getNamespace();

        String lyric = StringUtils.trimToNull(root.getChildText("Lyric", ns));
        String song = root.getChildText("LyricSong", ns);
        String artist = root.getChildText("LyricArtist", ns);

        return new Result(lyric, artist, song, false);
    }

    private static String executeGetRequest(String url) throws IOException {
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
