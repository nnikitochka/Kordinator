package org.airsonic.player.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * On application startup, computes the accent color for cover art rows that were
 * persisted before the accent color feature existed. A regular incremental scan
 * skips unchanged files, so such rows would otherwise stay without a color forever.
 */
public class CoverArtAccentColorBackfill {
    private static final Logger LOG = LoggerFactory.getLogger(CoverArtAccentColorBackfill.class);

    private final CoverArtService coverArtService;

    public CoverArtAccentColorBackfill(CoverArtService coverArtService) {
        this.coverArtService = coverArtService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillAccentColors() {
        try {
            int updated = coverArtService.backfillAccentColors();
            LOG.info("Cover art accent color backfill complete: {} rows updated", updated);
        } catch (Exception x) {
            LOG.warn("Cover art accent color backfill failed", x);
        }
    }
}
