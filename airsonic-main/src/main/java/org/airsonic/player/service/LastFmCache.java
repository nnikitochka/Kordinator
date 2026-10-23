package org.airsonic.player.service;

import de.umass.lastfm.cache.Cache;
import de.umass.lastfm.cache.FileSystemCache;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.LambdaUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Based on {@link FileSystemCache}, but properly closes files and enforces
 * time-to-live (by ignoring HTTP header directives).
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class LastFmCache extends Cache {

    private final Path cacheDir;
    private final long ttl;

    public LastFmCache(Path cacheDir, final long ttl) {
        this.cacheDir = cacheDir;
        this.ttl = ttl;

        setExpirationPolicy((method, params) -> ttl);
    }

    @Override
    public boolean contains(String cacheEntryName) {
        return Files.exists(getXmlFile(cacheEntryName));
    }

    @Override
    public InputStream load(String cacheEntryName) {
        try (InputStream in = Files.newInputStream(getXmlFile(cacheEntryName))) {
            //Have to read into a byte array otherwise underlying stream is closed at the return of this method
            return new ByteArrayInputStream(IOUtils.toByteArray(in));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void remove(String cacheEntryName) {
        FileUtil.delete(getXmlFile(cacheEntryName));
        FileUtil.delete(getMetaFile(cacheEntryName));
    }

    @Override
    public void store(String cacheEntryName, InputStream inputStream, long expirationDate) {
        createCache();

        Path xmlFile = getXmlFile(cacheEntryName);
        Path metaFile = getMetaFile(cacheEntryName);

        try (InputStream is = inputStream; Writer mw = Files.newBufferedWriter(metaFile)) {
            Files.copy(is, xmlFile, StandardCopyOption.REPLACE_EXISTING);

            Properties properties = new Properties();

            // Note: Ignore the given expirationDate, since Last.fm sets it to just one day ahead.
            properties.setProperty("expiration-date", Long.toString(getExpirationDate()));
            properties.store(mw, null);
        } catch (Exception e) {
            // we ignore the exception. if something went wrong we just don't cache it.
        }
    }

    private long getExpirationDate() {
        return System.currentTimeMillis() + ttl;
    }

    private void createCache() {
        if (!Files.exists(cacheDir)) {
            try {
                Files.createDirectories(cacheDir);
            } catch (IOException ignore) {}
        }
    }

    @Override
    public boolean isExpired(String cacheEntryName) {
        Path f = getMetaFile(cacheEntryName);
        if (!Files.exists(f)) {
            return false;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            Properties p = new Properties();
            p.load(r);
            long expirationDate = Long.valueOf(p.getProperty("expiration-date"));
            return expirationDate < System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void clear() {
        try (Stream<Path> walk = Files.list(cacheDir)) {
            walk.filter(Files::isRegularFile)
                .forEach(LambdaUtils.uncheckConsumer(Files::deleteIfExists));
        } catch (Exception ignore) {}
    }

    private Path getXmlFile(String cacheEntryName) {
        return cacheDir.resolve(cacheEntryName + ".xml");
    }

    private Path getMetaFile(String cacheEntryName) {
        return cacheDir.resolve(cacheEntryName + ".meta");
    }
}
