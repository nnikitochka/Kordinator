package su.nnedition.kord.library

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import su.nnedition.kord.KordConfig
import su.nnedition.kord.data.domain.Genre
import su.nnedition.kord.data.domain.artist.Artist
import su.nnedition.kord.data.domain.artist.ArtistRepository
import su.nnedition.kord.data.domain.track.Track
import su.nnedition.kord.data.domain.track.TrackRepository
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant

class KordLibrary(
    private val config: KordConfig,
    private val loader: LibraryLoader,
    private val artistsRepository: ArtistRepository,
    private val tracksRepository: TrackRepository,
) {
    val logger = LoggerFactory.getLogger(javaClass)

    var lastFullScanStart: Instant? = null
        private set
    var lastFullScanEnd: Instant? = null
        private set

    var lastSuccessFullScan: Instant? = null

    init {
        DurationUnit.MILLISECONDS
        val lastFullScan = lastSuccessFullScan
        if (lastFullScan == null || lastFullScan - Clock.System.now() > config.libraryScanDelaySeconds) {
            runBlocking {
                fullScan()
            }
        }
    }

    suspend fun fullScan() {
        val startTime = Clock.System.now()
        val result = loader.scanLibrary()
        val scanResult = result.getOrNull()
        if (scanResult == null) {
            logger.error("Error while full library scan: {}", result.exceptionOrNull()?.message, result.exceptionOrNull())
            return
        }
        val endTime = Clock.System.now()
        val time = endTime - startTime
        logger.info("Сканирование успешно завершено: $time")
        scanResult.artists.forEach { artistFolder ->
            // 1. Сначала собираем уникальные жанры для артиста, как ты и делал
            val genres = mutableSetOf<Genre>()
            artistFolder.albumsFolders.forEach { albumFolder ->
                albumFolder.tracks.forEach { track ->
                    track.genre?.let { genres.add(Genre(it)) }
                }
            }

            // 2. Создаем/обновляем основного артиста (владельца папки)
            val bdArtist = artistsRepository.findArtistByName(artistFolder.name)
            val artistExist = bdArtist?.id != null
            val mainArtistId = bdArtist?.id ?: artistsRepository.createId()
            val mainArtist = Artist(mainArtistId, artistFolder.name, genres)
            artistsRepository.insertArtist(mainArtist)

            // 3. Проходим по трекам альбомов
            artistFolder.albumsFolders.forEach { albumFolder ->
                albumFolder.tracks.forEach { trackFile ->

                    // Собираем доменные модели Artist для ВСЕХ исполнителей этого трека
                    val trackArtists = mutableListOf<Artist>()

                    // Если тег ARTIST пустой, то дефолтом пишем владельца папки
                    val artistNames = trackFile.artists ?: listOf(artistFolder.name)

                    artistNames.forEach { name ->
                        val existingArtist = artistsRepository.findArtistByName(name)
                        if (existingArtist != null) {
                            trackArtists.add(existingArtist)
                        } else {
                            // Если это фит с артистом, которого еще нет в базе (и у него нет своей папки)
                            val newArtistId = artistsRepository.createId()
                            val newArtist = Artist(newArtistId, name, emptySet())
                            artistsRepository.insertArtist(newArtist)
                            trackArtists.add(newArtist)
                        }
                    }

                    // 4. Генерируем ID трека и готовим доменную модель к записи
                    val trackId = tracksRepository.createId()

                    val track = Track(
                        id = trackId,
                        // Если тега TITLE нет, берем имя файла без расширения (.mp3/.flac)
                        title = trackFile.title ?: "N/A",
//                        path = trackFile.file.absolutePath,
                        artists = trackArtists, // Передаем полный список артистов (включая фиты)
                        relativePath = trackFile.file.path.removePrefix(config.musicLibraryPath),
                        releaseDate = trackFile.date ?: "N/A",
                    )

                    // 5. Записываем трек со всеми связями в базу данных
                    tracksRepository.insertTrack(track)
                }
            }
        }


        lastSuccessFullScan = endTime
    }

    suspend fun getArtist(id: Long): Artist? = artistsRepository.findArtistById(id)

//    fun getTrack(id: Long): Track? = tracksRepository
}
