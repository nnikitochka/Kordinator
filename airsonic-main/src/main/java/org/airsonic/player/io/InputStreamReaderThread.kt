package org.airsonic.player.io;

import org.airsonic.player.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Utility class which reads everything from an input stream and optionally logs it.
 *
 * @see TranscodeInputStream
 */
public class InputStreamReaderThread extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(InputStreamReaderThread.class);

    private final InputStream input;
    private final String name;
    private final boolean log;

    public InputStreamReaderThread(InputStream input, String name, boolean log) {
        super(name + " InputStreamLogger");
        this.input = input;
        this.name = name;
        this.log = log;
    }

    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (log) {
                    LOG.info('(' + name + ") " + line);
                }
            }
        } catch (IOException ignored) {
        } finally {
            FileUtil.closeQuietly(input);
        }
    }
}
