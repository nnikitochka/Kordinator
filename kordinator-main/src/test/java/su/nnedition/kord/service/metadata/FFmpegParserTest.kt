package su.nnedition.kord.service.metadata

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FFmpegParserTest {
    @Test
    fun readsFormatAndStreamTagsAndIgnoresEmbeddedCoverArt() {
        val json = jacksonObjectMapper().readTree("""
            {"format":{"duration":"42.5","bit_rate":"192000","tags":{"ARTIST":"Artist","track":"4","date":"2020"}},
             "streams":[
               {"index":0,"codec_type":"video","codec_name":"mjpeg","width":600,"height":600,"tags":{"title":"Cover"}},
               {"index":1,"codec_type":"audio","codec_name":"aac","tags":{"title":"Song","language":"eng"}},
               {"index":2,"codec_type":"video","codec_name":"h264","width":1920,"height":1080},
               {"index":3,"codec_type":"subtitle","codec_name":"srt","tags":{"language":"eng"}}
             ]}
        """.trimIndent())
        val result = MetaData()
        populateMetadata(json, result)
        assertEquals("Artist", result.artist)
        assertEquals("Song", result.title)
        assertEquals(4, result.trackNumber)
        assertEquals(2020, result.year)
        assertEquals(42.5, result.duration)
        assertEquals(192, result.bitRate)
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(listOf(1), result.audioTracks.map { it.id })
        assertEquals(listOf(2), result.videoTracks.map { it.id })
        assertEquals(listOf(3), result.subtitleTracks.map { it.id })
    }
}
