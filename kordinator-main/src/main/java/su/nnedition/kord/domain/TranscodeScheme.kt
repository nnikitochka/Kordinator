package su.nnedition.kord.domain

/**
 * Enumeration of transcoding schemes. Transcoding is the process of
 * converting an audio stream to a lower bit rate.
 */
enum class TranscodeScheme(
    /**
     * Returns the maximum bit rate for this transcoding scheme.
     * 
     * @return The maximum bit rate for this transcoding scheme.
     */
    val maxBitRate: Int
) {
    OFF(0),
    MAX_32(32),
    MAX_40(40),
    MAX_48(48),
    MAX_56(56),
    MAX_64(64),
    MAX_80(80),
    MAX_96(96),
    MAX_112(112),
    MAX_128(128),
    MAX_160(160),
    MAX_192(192),
    MAX_224(224),
    MAX_256(256),
    MAX_320(320);

    /**
     * Returns the strictest transcode scheme (i.e., the scheme with the lowest max bitrate).
     * 
     * @param other The other transcode scheme. May be `null`, in which case 'this' is returned.
     * @return The strictest scheme.
     */
    fun strictest(other: TranscodeScheme?): TranscodeScheme {
        if (other == null || other == OFF) {
            return this
        }

        if (this == OFF) return other

        return if (maxBitRate < other.maxBitRate) this else other
    }

    /**
     * Returns a human-readable string representation of this object.
     * 
     * @return A human-readable string representation of this object.
     */
    override fun toString(): String {
        return if (this == OFF) "No limit"
        else "$maxBitRate Kbps"
    }

    companion object {
        /**
         * Returns the enum constant which corresponds to the given max bit rate.
         * 
         * @param maxBitRate The max bit rate.
         * @return The corresponding enum, or `null` if not found.
         */
        fun valueOf(maxBitRate: Int): TranscodeScheme? {
            for (scheme in entries) {
                if (scheme.maxBitRate == maxBitRate) {
                    return scheme
                }
            }
            return null
        }

        @JvmStatic
        fun fromMaxBitRate(maxBitRate: Int): TranscodeScheme? {
            for (transcodeScheme in entries) {
                if (maxBitRate == transcodeScheme.maxBitRate) {
                    return transcodeScheme
                }
            }
            return null
        }
    }
}
