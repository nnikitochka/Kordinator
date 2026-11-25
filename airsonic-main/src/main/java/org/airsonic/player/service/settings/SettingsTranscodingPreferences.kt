package org.airsonic.player.service.settings

/** Owns the configured external commands used for audio, HLS, video, and caption processing. */
internal class SettingsTranscodingPreferences(private val values: SettingsValueStore) {
    fun getDownsamplingCommand(): String = value(KEY_DOWNSAMPLING_COMMAND, DEFAULT_DOWNSAMPLING_COMMAND)
    fun setDownsamplingCommand(command: String?) = values.setProperty(KEY_DOWNSAMPLING_COMMAND, command)
    fun getHlsCommand(): String = value(KEY_HLS_COMMAND, DEFAULT_HLS_COMMAND)
    fun setHlsCommand(command: String?) = values.setProperty(KEY_HLS_COMMAND, command)
    fun getJukeboxCommand(): String = value(KEY_JUKEBOX_COMMAND, DEFAULT_JUKEBOX_COMMAND)
    fun setJukeboxCommand(command: String?) = values.setProperty(KEY_JUKEBOX_COMMAND, command)
    fun getVideoImageCommand(): String = value(KEY_VIDEO_IMAGE_COMMAND, DEFAULT_VIDEO_IMAGE_COMMAND)
    fun setVideoImageCommand(command: String?) = values.setProperty(KEY_VIDEO_IMAGE_COMMAND, command)
    fun getSubtitlesExtractionCommand(): String = value(KEY_SUBTITLES_EXTRACTION_COMMAND, DEFAULT_SUBTITLES_EXTRACTION_COMMAND)
    fun setSubtitlesExtractionCommand(command: String?) = values.setProperty(KEY_SUBTITLES_EXTRACTION_COMMAND, command)

    private fun value(key: String, defaultValue: String): String = requireNotNull(values.getString(key, defaultValue))

    private companion object {
        const val KEY_DOWNSAMPLING_COMMAND = "DownsamplingCommand4"
        const val KEY_HLS_COMMAND = "HlsCommand4"
        const val KEY_JUKEBOX_COMMAND = "JukeboxCommand2"
        const val KEY_VIDEO_IMAGE_COMMAND = "VideoImageCommand"
        const val KEY_SUBTITLES_EXTRACTION_COMMAND = "SubtitlesExtractionCommand"
        const val DEFAULT_DOWNSAMPLING_COMMAND = "ffmpeg -i %s -map 0:0 -b:a %bk -v 0 -f mp3 -"
        const val DEFAULT_HLS_COMMAND = "ffmpeg -ss %o -i %s -s %wx%h -async 1 -c:v libx264 -flags +cgop -b:v %vk -maxrate %bk -preset superfast -copyts -b:a %rk -bufsize 256k -map 0:0 -map 0:%i -ac 2 -ar 44100 -v 0 -threads 0 -force_key_frames expr:gte(t,n_forced*10) -start_number %j -hls_time %d -hls_list_size 0 -hls_segment_filename %n %p"
        const val DEFAULT_JUKEBOX_COMMAND = "ffmpeg -ss %o -i %s -map 0:0 -v 0 -ar 44100 -ac 2 -f s16be -"
        const val DEFAULT_VIDEO_IMAGE_COMMAND = "ffmpeg -r 1 -ss %o -t 1 -i %s -s %wx%h -v 0 -f mjpeg -"
        const val DEFAULT_SUBTITLES_EXTRACTION_COMMAND = "ffmpeg -i %s -map 0:%i -f %f -"
    }
}
