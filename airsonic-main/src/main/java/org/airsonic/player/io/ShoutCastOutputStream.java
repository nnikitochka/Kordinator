package org.airsonic.player.io;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Implements SHOUTcast support by decorating an existing output stream.
 * <p/>
 * Based on protocol description found on
 * <em>http://www.smackfu.com/stuff/programming/shoutcast.html</em>
 */
public class ShoutCastOutputStream extends OutputStream {

    /**
     * Number of bytes between each SHOUTcast metadata block.
     */
    public static final int META_DATA_INTERVAL = 20480;

    /**
     * The underlying output stream to decorate.
     */
    private final OutputStream out;

    /**
     * Keeps track of the number of bytes written (excluding meta-data).  Between 0 and {@link #META_DATA_INTERVAL}.
     */
    private int byteCount;

    /**
     * The last stream title sent.
     */
    private String previousStreamTitle;

    private final Supplier<String> titleSupplier;

    /**
     * Creates a new SHOUTcast-decorated stream for the given output stream.
     *
     * @param out           The output stream to decorate.
     * @param titleSupplier Meta-data title is fetched from this supplier.
     */
    public ShoutCastOutputStream(OutputStream out, Supplier<String> titleSupplier) {
        this.out = out;
        this.titleSupplier = titleSupplier;
    }

    /**
     * Writes the given byte array to the underlying stream, adding SHOUTcast meta-data as necessary.
     */
    @Override
    public void write(@Nonnull byte[] bytes, int off, int len) throws IOException {

        int bytesWritten = 0;
        while (bytesWritten < len) {

            // 'n' is the number of bytes to write before the next potential meta-data block.
            int n = Math.min(len - bytesWritten, ShoutCastOutputStream.META_DATA_INTERVAL - byteCount);

            out.write(bytes, off + bytesWritten, n);
            bytesWritten += n;
            byteCount += n;

            // Reached meta-data block?
            if (byteCount % ShoutCastOutputStream.META_DATA_INTERVAL == 0) {
                writeMetaData();
                byteCount = 0;
            }
        }
    }

    /**
     * Writes the given byte array to the underlying stream, adding SHOUTcast meta-data as necessary.
     */
    @Override
    public void write(@Nonnull byte[] bytes) throws IOException {
        write(bytes, 0, bytes.length);
    }

    /**
     * Writes the given byte to the underlying stream, adding SHOUTcast meta-data as necessary.
     */
    @Override
    public void write(int b) throws IOException {
        byte[] buf = new byte[]{(byte) b};
        write(buf);
    }

    /**
     * Flushes the underlying stream.
     */
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Closes the underlying stream.
     */
    @Override
    public void close() throws IOException {
        out.close();
    }

    private void writeMetaData() throws IOException {
        String streamTitle = titleSupplier.get();
//                StringUtils.trimToEmpty(settingsService.getWelcomeTitle());

//        MediaFile result;
//        synchronized (playQueue) {
//            result = playQueue.getCurrentFile();
//        }
//        MediaFile mediaFile = result;
//        if (mediaFile != null) {
//            streamTitle = mediaFile.getArtist() + " - " + mediaFile.getTitle();
//        }

        byte[] bytes;

        if (streamTitle.equals(previousStreamTitle)) {
            bytes = new byte[0];
        } else {
            previousStreamTitle = streamTitle;
            bytes = createStreamTitle(streamTitle);
        }

        // Length in groups of 16 bytes.
        int length = bytes.length / 16;
        if (bytes.length % 16 > 0) {
            length++;
        }

        // Write the length as a single byte.
        out.write(length);

        // Write the message.
        out.write(bytes);

        // Write padding zero bytes.
        int padding = length * 16 - bytes.length;
        for (int i = 0; i < padding; i++) {
            out.write(0);
        }
    }

    public static byte[] createStreamTitle(String title) {
        // Remove any quotes from the title.
        title = title.replaceAll("'", "");

        // Convert non-ascii characters to similar ascii characters.
        for (char[] chars : ShoutCastOutputStream.CHAR_MAP) {
            title = title.replace(chars[0], chars[1]);
        }

        title = "StreamTitle='" + title + "';";
        return title.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Maps from miscellaneous accented characters to similar-looking ASCII characters.
     */
    public static final char[][] CHAR_MAP = {
            {'À', 'A'}, {'Á', 'A'}, {'Â', 'A'}, {'Ã', 'A'}, {'Ä', 'A'}, {'Å', 'A'}, {'Æ', 'A'},
            {'È', 'E'}, {'É', 'E'}, {'Ê', 'E'}, {'Ë', 'E'},
            {'Ì', 'I'}, {'Í', 'I'}, {'Î', 'I'}, {'Ï', 'I'},
            {'Ò', 'O'}, {'Ó', 'O'}, {'Ô', 'O'}, {'Õ', 'O'}, {'Ö', 'O'},
            {'Ù', 'U'}, {'Ú', 'U'}, {'Û', 'U'}, {'Ü', 'U'},
            {'ß', 'B'},
            {'à', 'a'}, {'á', 'a'}, {'â', 'a'}, {'ã', 'a'}, {'ä', 'a'}, {'å', 'a'}, {'æ', 'a'},
            {'ç', 'c'},
            {'è', 'e'}, {'é', 'e'}, {'ê', 'e'}, {'ë', 'e'},
            {'ì', 'i'}, {'í', 'i'}, {'î', 'i'}, {'ï', 'i'},
            {'ñ', 'n'},
            {'ò', 'o'}, {'ó', 'o'}, {'ô', 'o'}, {'õ', 'o'}, {'ö', 'o'}, {'ø', 'o'},
            {'ù', 'u'}, {'ú', 'u'}, {'û', 'u'}, {'ü', 'u'},
            {'–', '-'}
    };
}
