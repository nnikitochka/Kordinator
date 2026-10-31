package org.airsonic.player.service;

import java.io.IOException;
import java.io.InputStream;

public final class DownloadResponse {
    private final DownloadStreamSupplier source;
    private final String proposedName;
    private final long size;
    private final long changed;
    private final String filename;

    public DownloadResponse(DownloadStreamSupplier source, String proposedName, long size, long changed) {
        this(source, proposedName, size, changed, null);
    }
    private DownloadResponse(DownloadStreamSupplier source, String proposedName, long size, long changed, String filename) {
        this.source = source;
        this.proposedName = proposedName;
        this.size = size;
        this.changed = changed;
        this.filename = filename;
    }
    public DownloadResponse withFilename(String filename) { return new DownloadResponse(source, proposedName, size, changed, filename); }
    public boolean hasContent() { return source != null; }
    public InputStream openStream() throws IOException { return source.open(); }
    public String getProposedName() { return proposedName; }
    public String getFilename() { return filename; }
    public long getSize() { return size; }
    public long getChanged() { return changed; }
}
