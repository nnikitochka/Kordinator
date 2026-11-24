package su.nnedition.kord.api.module.user.playback

data class PlayQueueTrackResponse(val id: Long, val title: String, val artist: String?, val album: String?, val durationSeconds: Int)
