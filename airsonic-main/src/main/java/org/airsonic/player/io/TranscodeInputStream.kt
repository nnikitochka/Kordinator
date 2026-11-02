package org.airsonic.player.io;

import org.airsonic.player.util.FileUtil;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Subclass of {@link InputStream} which provides on-the-fly transcoding.
 * Instances of <code>TranscodeInputStream</code> can be chained together, for instance to convert
 * from OGG to WAV to MP3.
 */
public class TranscodeInputStream extends InputStream {

    private static final Logger LOG = LoggerFactory.getLogger(TranscodeInputStream.class);

    private final InputStream processInputStream;
    private final OutputStream processOutputStream;
    private final Process process;
    private final Path tmpFile;

    /**
     * Creates a transcoded input stream by executing an external process. If <code>in</code> is not null,
     * data from it is copied to the command.
     *
     * @param processBuilder Used to create the external process.
     * @param in Data to feed to the process.  May be {@code null}.
     * @param tmpFile Temporary file to delete when this stream is closed.  May be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    public TranscodeInputStream(ProcessBuilder processBuilder, final InputStream in, Path tmpFile) throws IOException {
        this.tmpFile = tmpFile;
        LOG.info("Starting transcoder: {}", processBuilder.command().stream().collect(Collectors.joining("][", "[", "]")));

        process = processBuilder.start();
        processOutputStream = process.getOutputStream();
        processInputStream = process.getInputStream();

        // Must read stderr from the process, otherwise it may block.
        final String name = processBuilder.command().getFirst();
        new InputStreamReaderThread(process.getErrorStream(), name, true).start();

        // Copy data in a separate thread
        if (in != null) {
            new Thread(name + " TranscodedInputStream copy thread") {
                @Override
                public void run() {
                    try {
                        IOUtils.copy(in, processOutputStream);
                    } catch (IOException x) {
                        // Intentionally ignored. Will happen if the remote player closes the stream.
                    } finally {
                        FileUtil.closeQuietly(in);
                        FileUtil.closeQuietly(processOutputStream);
                    }
                }
            }.start();
        }
    }

    public Process getProcess() {
        return process;
    }

    /**
     * @see InputStream#read()
     */
    @Override
    public int read() throws IOException {
        return processInputStream.read();
    }

    /**
     * @see InputStream#read(byte[])
     */
    @Override
    public int read(@NotNull byte[] bytes) throws IOException {
        return processInputStream.read(bytes);
    }

    /**
     * @see InputStream#read(byte[], int, int)
     */
    @Override
    public int read(@NotNull byte[] bytes, int off, int len) throws IOException {
        return processInputStream.read(bytes, off, len);
    }

    /**
     * @see InputStream#close()
     */
    @Override
    public void close() {
        FileUtil.closeQuietly(processInputStream);
        FileUtil.closeQuietly(processOutputStream);

        if (process != null) {
            process.destroy();
        }

        if (tmpFile != null) {
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException e) {
                LOG.warn("Failed to delete tmp file: {}", tmpFile);
            }
        }
    }
}
