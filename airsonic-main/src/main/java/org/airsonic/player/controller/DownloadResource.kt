package org.airsonic.player.controller;

import org.airsonic.player.service.DownloadResponse;
import org.springframework.core.io.AbstractResource;
import java.io.IOException;
import java.io.InputStream;

final class DownloadResource extends AbstractResource {
    private final DownloadResponse response;
    DownloadResource(DownloadResponse response) { this.response = response; }
    @Override public InputStream getInputStream() throws IOException { return response.openStream(); }
    @Override public long contentLength() { return response.getSize(); }
    @Override public String getDescription() { return response.getFilename(); }
}
