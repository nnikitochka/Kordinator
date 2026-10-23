package org.airsonic.player.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.airsonic.player.util.LambdaUtils.uncheckConsumer;

/**
 * Miscellaneous file utility methods.
 */
public final class FileUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FileUtil.class);

    /**
     * Disallow external instantiation.
     */
    private FileUtil() {
    }

    public static Instant lastModified(final Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            LOG.warn("Could not get file modify date for {}", file, e);
            return Instant.now();
        }
    }

    public static long size(final Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            LOG.warn("Could not get file size for {}", file, e);
            return 0;
        }
    }

    public static boolean delete(Path fileOrFolder) {
        try (Stream<Path> walk = Files.walk(fileOrFolder)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(uncheckConsumer(Files::deleteIfExists));

            return true;
        } catch (NoSuchFileException e) {
            LOG.debug("Could not delete file/folder {}", fileOrFolder, e);
            return false;
        } catch (Exception e) {
            LOG.warn("Could not delete file/folder {}", fileOrFolder, e);
            return false;
        }
    }

    /**
     * Returns a short path for the given file.  The path consists of the name
     * of the parent directory and the given file.
     */
    public static String getShortPath(Path file) {
        if (file == null) {
            return null;
        }

        Path parent = file.getParent();
        if (parent == null) {
            return file.getFileName().toString();
        }
        return parent.getFileName().toString() + File.separator + file.getFileName().toString();
    }

    /**
     * Closes the "closable", ignoring any excepetions.
     *
     * @param closeable The Closable to close, may be {@code null}.
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignored
            }
        }
    }
}
