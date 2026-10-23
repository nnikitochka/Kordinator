package org.airsonic.player.upload;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Extension of Commons FileUpload for monitoring the upload progress.
 *
 * @author Pierre-Alexandre Losson -- http://www.telio.be/blog -- plosson@users.sourceforge.net
 */
public class MonitoredOutputStream extends OutputStream {
    private final OutputStream target;
    private final UploadListener listener;

    public MonitoredOutputStream(OutputStream target, UploadListener listener) {
        this.target = target;
        this.listener = listener;
    }

    public void write(@NotNull byte[] bytes, int off, int len) throws IOException {
        target.write(bytes, off, len);
        listener.bytesRead(len);
    }

    public void write(@NotNull byte[] bytes) throws IOException {
        target.write(bytes);
        listener.bytesRead(bytes.length);
    }

    public void write(int bytes) throws IOException {
        target.write(bytes);
        listener.bytesRead(1);
    }

    public void close() throws IOException {
        target.close();
    }

    public void flush() throws IOException {
        target.flush();
    }
}
