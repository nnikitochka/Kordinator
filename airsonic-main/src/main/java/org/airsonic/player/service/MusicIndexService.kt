package org.airsonic.player.service

import org.airsonic.player.domain.*
import org.airsonic.player.domain.MusicIndex.SortableArtist
import org.apache.commons.lang3.tuple.Pair
import java.io.Serializable
import java.nio.file.Paths
import java.text.Collator
import java.util.*
import java.util.stream.Collectors

/** Provides services for grouping artists by index. */
open class MusicIndexService(
    private var settingsService: SettingsService,
    private var mediaFileService: MediaFileService
) {
    open fun getIndexedArtists(
        folders: List<MusicFolder>,
        refresh: Boolean
    ): SortedMap<MusicIndex, MutableList<MusicIndex.SortableArtistWithMediaFiles>> =
        sortArtists(createSortableArtists(folders, refresh))

    open fun getIndexedArtists(
        artists: List<Artist>
    ): SortedMap<MusicIndex, MutableList<MusicIndex.SortableArtistWithArtist>> =
        sortArtists(createSortableArtists(artists))

    open fun getMusicFolderContent(musicFoldersToUse: List<MusicFolder>, refresh: Boolean): MusicFolderContent {
        val indexedArtists = getIndexedArtists(musicFoldersToUse, refresh)
        val singleSongs = getSingleSongs(musicFoldersToUse, refresh)
        return MusicFolderContent(indexedArtists, ArrayList(singleSongs))
    }

    private fun getSingleSongs(folders: List<MusicFolder>, refresh: Boolean): List<MediaFile> =
        folders.stream().flatMap { folder ->
            mediaFileService.getChildrenOf(
                mediaFileService.getMediaFile(Paths.get(""), folder, !refresh),
                true, false, true, !refresh
            ).stream()
        }.collect(Collectors.toList())

    open fun getShortcuts(musicFoldersToUse: List<MusicFolder>): List<MediaFile> =
        Arrays.stream(settingsService.getShortcutsAsArray())
            .flatMap { shortcut ->
                musicFoldersToUse.parallelStream().map { folder -> Pair.of(Paths.get(shortcut), folder) }
            }
            .map { pair -> mediaFileService.getMediaFile(pair.left, pair.right, true) }
            .filter(Objects::nonNull)
            .collect(Collectors.toList())

    private fun <T : SortableArtist> sortArtists(artists: List<T>): SortedMap<MusicIndex, MutableList<T>> {
        val indexes = createIndexesFromExpression(settingsService.indexString)
        val result = TreeMap<MusicIndex, MutableList<T>>(MusicIndexComparator(indexes))

        for (artist in artists) {
            val index = getIndex(artist, indexes)
            result.computeIfAbsent(index) { ArrayList() }.add(artist)
        }

        result.values.forEach(Collections::sort)
        return result
    }

    /** Parses a single index expression such as `A(AÅÆ)` or `X-Z(XYZ)`. */
    protected open fun createIndexFromExpression(expr: String): MusicIndex {
        val separatorIndex = expr.indexOf('(')
        if (separatorIndex == -1) {
            return MusicIndex(expr).also { it.addPrefix(expr) }
        }

        return MusicIndex(expr.substring(0, separatorIndex)).also { index ->
            val prefixString = expr.substring(separatorIndex + 1, expr.length - 1)
            for (i in prefixString.indices) {
                index.addPrefix(prefixString.substring(i, i + 1))
            }
        }
    }

    /** Parses the space-separated index expressions from the user settings. */
    protected open fun createIndexesFromExpression(expr: String): List<MusicIndex> {
        val result = ArrayList<MusicIndex>()
        val tokenizer = StringTokenizer(expr, " ")
        while (tokenizer.hasMoreTokens()) {
            result.add(createIndexFromExpression(tokenizer.nextToken()))
        }
        return result
    }

    private fun createSortableArtists(
        folders: List<MusicFolder>,
        refresh: Boolean
    ): List<MusicIndex.SortableArtistWithMediaFiles> {
        val ignoredArticles = settingsService.ignoredArticlesAsArray
        val shortcuts = settingsService.shortcutsAsArray
        val artistMap = TreeMap<String, MusicIndex.SortableArtistWithMediaFiles>()
        val shortcutSet = HashSet(Arrays.asList(*shortcuts))
        val collator = createCollator()

        for (folder in folders) {
            val root = mediaFileService.getMediaFile(Paths.get(""), folder, !refresh)
            val children = mediaFileService.getChildrenOf(root, false, true, true, !refresh)
            for (child in children) {
                if (shortcutSet.contains(child.name)) continue

                val sortableName = createSortableName(child.name, ignoredArticles)
                val artist = artistMap[sortableName]
                    ?: MusicIndex.SortableArtistWithMediaFiles(child.name, sortableName, collator).also {
                        artistMap[sortableName] = it
                    }
                artist.addMediaFile(child)
            }
        }
        return ArrayList(artistMap.values)
    }

    private fun createSortableArtists(artists: List<Artist>): List<MusicIndex.SortableArtistWithArtist> {
        val result = ArrayList<MusicIndex.SortableArtistWithArtist>()
        val ignoredArticles = settingsService.ignoredArticlesAsArray
        val collator = createCollator()
        for (artist in artists) {
            val sortableName = createSortableName(artist.name!!, ignoredArticles)
            result.add(MusicIndex.SortableArtistWithArtist(artist.name!!, sortableName, artist, collator))
        }
        return result
    }

    private fun createCollator(): Collator = Collator.getInstance(settingsService.locale)

    private fun createSortableName(name: String, ignoredArticles: Array<String>): String {
        val uppercaseName = name.uppercase(Locale.getDefault())
        for (article in ignoredArticles) {
            if (uppercaseName.startsWith(article.uppercase(Locale.getDefault()) + " ")) {
                return name.substring(article.length + 1) + ", " + article
            }
        }
        return name
    }

    private fun getIndex(artist: SortableArtist, indexes: List<MusicIndex>): MusicIndex {
        val sortableName = artist.sortableName.uppercase(Locale.getDefault())
        for (index in indexes) {
            for (prefix in index.prefixes) {
                if (sortableName.startsWith(prefix.uppercase(Locale.getDefault()))) return index
            }
        }
        return MusicIndex.OTHER
    }

    open fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
    }

    open fun setMediaFileService(mediaFileService: MediaFileService) {
        this.mediaFileService = mediaFileService
    }

    private class MusicIndexComparator(private val indexes: List<MusicIndex>) : Comparator<MusicIndex>, Serializable {
        override fun compare(a: MusicIndex, b: MusicIndex): Int {
            val indexA = indexes.indexOf(a).let { if (it == -1) Int.MAX_VALUE else it }
            val indexB = indexes.indexOf(b).let { if (it == -1) Int.MAX_VALUE else it }
            return Integer.compare(indexA, indexB)
        }
    }
}
