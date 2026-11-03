package org.airsonic.player.service;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface DownloadStreamSupplier {
    InputStream open() throws IOException;
}
