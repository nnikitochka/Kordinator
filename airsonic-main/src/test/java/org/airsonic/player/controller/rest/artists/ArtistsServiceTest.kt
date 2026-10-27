package org.airsonic.player.controller.rest.artists

import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.MusicFolder.Type
import org.airsonic.player.domain.MusicIndex
import org.airsonic.player.service.MusicIndexService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.subsonic.restapi.ArtistID3
import java.nio.file.Paths
import java.text.Collator
import java.time.Instant
import java.util.TreeMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtistsServiceTest {

    @Test
    fun `groups artists into indexes and maps them for the user`() {
        val folders = listOf(MusicFolder(1, Paths.get("/music"), "Music", Type.MEDIA, true, Instant.now()))
        val alpha = Artist(7, "Alpha", 3, Instant.now(), true, 1)
        val bravo = Artist(9, "Bravo", 1, Instant.now(), true, 1)
        val suppliedFolders = mutableListOf<List<MusicFolder>>()
        val mapperUsernames = mutableListOf<String>()

        val indexedArtists = TreeMap<MusicIndex, List<MusicIndex.SortableArtistWithArtist>>(
                Comparator.comparing(MusicIndex::getIndex))
        indexedArtists[MusicIndex("A")] = listOf(
                MusicIndex.SortableArtistWithArtist("Alpha", "alpha", alpha, Collator.getInstance()))
        indexedArtists[MusicIndex("B")] = listOf(
                MusicIndex.SortableArtistWithArtist("Bravo", "bravo", bravo, Collator.getInstance()))

        val musicIndexService = Mockito.mock(MusicIndexService::class.java)
        Mockito.`when`(musicIndexService.getIndexedArtists(Mockito.anyList()))
                .thenReturn(indexedArtists)

        val service = ArtistsService(
                ArtistLookup { foldersSupplied -> suppliedFolders.add(foldersSupplied); listOf(alpha, bravo) },
                musicIndexService,
                { "The El La" },
                UserMusicFolders { username ->
                    assertEquals("admin", username)
                    folders
                },
                ArtistId3Mapper { target, artist, username ->
                    mapperUsernames.add(username)
                    target.apply {
                        id = artist.id.toString()
                        name = artist.name
                    }
                },
        )

        val result = service.getArtists("admin")

        assertEquals(listOf(folders), suppliedFolders)
        assertEquals("The El La", result.ignoredArticles)
        assertEquals(listOf("A", "B"), result.index.map { it.name })
        assertEquals(listOf("7", "9"), result.index.flatMap { it.artist }.map { it.id })
        assertEquals(listOf("Alpha", "Bravo"), result.index.flatMap { it.artist }.map { it.name })
        assertEquals(listOf("admin", "admin"), mapperUsernames)
    }

    @Test
    fun `returns an empty index for an empty library`() {
        val musicIndexService = Mockito.mock(MusicIndexService::class.java)
        Mockito.`when`(musicIndexService.getIndexedArtists(Mockito.anyList()))
                .thenReturn(TreeMap<MusicIndex, List<MusicIndex.SortableArtistWithArtist>>())

        val service = ArtistsService(
                ArtistLookup { emptyList() },
                musicIndexService,
                { "The El La" },
                UserMusicFolders { emptyList() },
                ArtistId3Mapper { target, _, _ -> target },
        )

        val result = service.getArtists("admin")

        assertEquals("The El La", result.ignoredArticles)
        assertTrue(result.index.isEmpty())
    }
}
