package su.nnedition.kord.service

import org.slf4j.LoggerFactory

/** Backfills accent colors for cover art rows created before that field existed. */
class CoverArtAccentColorBackfill(private val coverArtService: CoverArtService) {
    fun backfillAccentColors() {
        try {
            val updated = coverArtService.backfillAccentColors()
            LOG.info("Cover art accent color backfill complete: {} rows updated", updated)
        } catch (failure: Exception) {
            LOG.warn("Cover art accent color backfill failed", failure)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CoverArtAccentColorBackfill::class.java)
    }
}
