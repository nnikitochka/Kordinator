# План Kotlin rewrite Kordinator

Актуальное состояние: 25 ноября 2026. Этот существующий документ переиспользован для текущей цели; прежние указания о JSP, Spring servlet и совместимости Subsonic больше не применяются. Три удалённых пользователем документа миграции не восстанавливаются.

## Цель и ограничения

Полностью перевести собственный поддерживаемый код на Kotlin в основном модуле `airsonic-main`, сохранив работающие сценарии музыкального бэкенда. HTTP API оформляется как функции `Route.…Routes` в `su.nnedition.kord.api.module`, DTO лежат в отдельных файлах. Новые и переработанные классы не превышают 300 строк. Параллельное приложение не создаётся.

Совместимость Subsonic/Airsonic и импорт их старых БД не требуются. Интернет-радио и подкасты исключены. Сохранённые иконки остаются в проекте. Существующий код миграции схемы пока участвует в создании рабочей БД: его нельзя удалить без готовой замены схемы и проверки новой установки.

Каждый срез: прочитать исходную реализацию и потребителей, перенести поведение и обработку ошибок, подключить Kotlin-замену, удалить Java-дубликат, выполнить чистую компиляцию и подходящие тесты, проверить diff и закоммитить. Изменения поведения согласовываются отдельно. Firefox не используется.

## Порядок оставшейся работы

1. **Composition root — завершён.** Перенести `RuntimeCompositionRoot` на Kotlin, разделив infrastructure, security, settings, media, search/scanner, playlists, playback, UPnP и application services. Сохранить singleton identity, ленивый PlayerService supplier, общий reentrant lock, проверки порядка сборки графа и регистрацию lifecycle ровно один раз. Проверки: runtime graph, конкурентное создание компонентов, JDBC/Liquibase, запуск API и реальное сканирование.
2. **Метаданные и малые зависимости.** Перенести `MetaData`, parser contract и parser implementations; затем playlist import/export contracts и handlers, HLS session, audio player, scrobbler adapters и утилиты. Сохранить nullable поля, сортировки, ошибки чтения файлов, закрытие потоков и транскодирующих процессов. Проверки: существующие parser, playlist, HLS/transcoding и streaming tests.
3. **Каталог и сканирование.** Перенесены `CoverArtImageService` и `MediaFolderService`; далее разделить и перенести `MediaFileService`, `MediaScannerService`, `LastFmService`. Проверки: сканирование реальной папки, повторное сканирование, удалённые файлы, изоляция библиотек, каталог/поиск и обложки.
4. **Настройки и безопасность.** Перенести `VersionService` (завершён), затем разделить и перенести `SettingsService`, `SecurityService`, `DatabaseService`; сохранить права, кеши, обновления credentials, настройки и backup/import/export. Проверки: security/settings suites и соответствующие admin API.
5. **Воспроизведение и плейлисты.** Перенести `PlaylistService`, `TranscodingService`, `DownloadService`, `JukeboxLegacySubsonicService`. Сохранить порядок треков, доступ аккаунтов, streaming/Range/HEAD, параметры ffmpeg, завершение процессов и ресурсы jukebox. Проверки: playlist, streaming/transcoding, jukebox и download suites.
6. **DLNA/UPnP — завершён.** Перенесены processors, content directories, NAT-PMP router и регистрация media server. Проверены URL generation, дерево каталога, NAT-PMP параметры, lifecycle и DLNA API.
7. **Создание схемы и оставшиеся тесты.** Перенести нужные Liquibase custom changes и оставшиеся Java-тесты/fixtures вместе с их областями. Старые миграционные названия не означают активную зависимость от Spring; проверять реальные импорты и загрузку changesets. Не удалять действующую схему без замены.
8. **Завершающий аудит.** Сверить список Java-файлов, выполнить полную сборку и автоматические проверки, запустить packaged JAR с изолированным home, проверить поддерживаемые сценарии и отсутствие Spring/Servlet/Tomcat в runtime. Удалить ненужные Java-specific build steps после исчезновения потребителей. Завершить коммиты.

## Критерии завершения

- Поддерживаемые собственные production/test/helper исходники на Kotlin; исключения согласованы явно.
- Основной модуль запускается на Kotlin/Ktor без Spring, Servlet API и Tomcat в цепочке выполнения.
- Сохранены библиотека/сканирование, каталог/поиск, streaming/transcoding, плейлисты/очереди, пользователи/права, настройки и остальные действующие возможности.
- Нет активных Java-дубликатов и ссылок на удалённые реализации; новые/переработанные классы до 300 строк.
- Итоговая сборка и необходимые тесты проходят; непроверенные сценарии перечислены. План актуален, изменения закоммичены.

## Инвентаризация Java

Пути ниже относительно корня репозитория. Обновлять после каждого законченного среза; количество файлов не является процентом функциональной готовности.

### Production: 20 файлов

- `airsonic-main/src/main/java/org/airsonic/player/service/DatabaseService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/DownloadService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/JukeboxLegacySubsonicService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/LastFmService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/MediaFileService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/MediaScannerService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/PlaylistService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/SecurityService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/SettingsService.java`
- `airsonic-main/src/main/java/org/airsonic/player/service/TranscodingService.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/AddMediaFileIdToPodcastChannels.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/AddPodcastMediaFolderUsers.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/AddUnknownMediaFolders.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/DropConstraintWithoutName.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/ExtractCustomAvatars.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/UniqueMediaFolders.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/UserRolesJsonColumnPopulation.java`
- `airsonic-main/src/main/java/org/airsonic/player/spring/migrations/UserSettingsJsonTablePopulation.java`
- `airsonic-main/src/main/java/org/airsonic/player/util/LegacyHsqlMigrationUtil.java`
- `airsonic-main/src/main/java/org/airsonic/player/util/Util.java`

### Тесты и fixtures: 73 файла

- `airsonic-main/src/test/java/org/airsonic/player/TestCaseUtils.java`
- `airsonic-main/src/test/java/org/airsonic/player/api/ScanningTestUtils.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/BookmarkDaoTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/DaoTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/JdbcHelperTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/MusicFolderDaoTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/PlayerDaoTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/TranscodingDaoTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/UserDaoTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/domain/MediaFileComparatorTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/domain/PlayQueueTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/domain/SortableArtistTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/domain/TranscodeSchemeTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/domain/VersionTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/filter/ParameterDecodingPolicyTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/filter/RequestEncodingPolicyTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/filter/StaticResponseHeadersTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/health/HealthFrameworkTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/runtime/LifecycleRegistryTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/runtime/RuntimeCompositionRootGraphTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/runtime/RuntimeCompositionRootTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/runtime/RuntimeConfigurationLoaderTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/AesGcmPasswordEncoderTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/AuthorizationPolicyTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/CallerContextTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/CredentialCompatibilityBaselineTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/JndiLdapDirectoryAuthenticatorTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/JwtTokenVerifierTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/JwtTokenVerifierUriTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/LdapAuthenticatorTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/SubsonicAuthenticatorTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/security/UserIdentityTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/CoverArtImageServiceTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/CoverArtServiceAccentColorTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/DatabaseStatusTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/JWTSecurityServiceTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/JukeboxServiceUnitTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/LegacyDatabaseStartupTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/LyricsServiceTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/MediaScannerServiceTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/MediaScannerServiceUnitTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/MusicIndexServiceTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/PlaylistServiceHandlerOrderTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/PlaylistServiceTestExport.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/PlaylistServiceTestImport.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/ScheduledTaskSnapshotTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/SecurityServiceTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/ServerInfoServiceTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/SettingsServiceTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/StatusServiceTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/TagEditServiceTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/TaskSchedulingServiceTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/TranscodingServiceIntTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/metadata/MetaDataFactoryTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/metadata/MetaDataParserTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/AbstractAirsonicHomeTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/AnalyzerFactoryTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/IndexManagerTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/QueryFactoryTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/SearchServiceSpecialGenreTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/SearchServiceSpecialPathTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/SearchServiceStartWithStopwardsTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/search/SearchServiceTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/upnp/AlbumUpnpProcessorUrlTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/upnp/CustomContentDirectoryUrlTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/upnp/DispatchingContentDirectoryUrlTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/upnp/FolderBasedContentDirectoryUrlTest.java`
- `airsonic-main/src/test/java/org/airsonic/player/util/EmbeddedTestCategory.java`
- `airsonic-main/src/test/java/org/airsonic/player/util/FileUtils.java`
- `airsonic-main/src/test/java/org/airsonic/player/util/HomeRule.java`
- `airsonic-main/src/test/java/org/airsonic/player/util/MusicFolderTestData.java`
- `airsonic-main/src/test/java/org/airsonic/player/util/StringUtilTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/util/UtilTest.java`

## Результаты текущей работы

- `ec23534`: все оставшиеся HTTP routes и DTO перенесены из `org.airsonic.player.ktor` в функциональные API-модули. Чистая сборка и 38 тестов прошли; после финального разделения admin player/database повторно прошли 2 теста. В старом пакете остались только session services.
- Kotlin composition root: Java-класс заменён Kotlin-фасадом и компонентами по областям. Чистая компиляция прошла; 53 теста runtime, конкурентного доступа, lifecycle, БД и Ktor API прошли. Следующий срез — модели и парсеры метаданных.
- `MetaData` переведён на Kotlin: сохранены Java getters/setters, live read-only список дорожек и выборки потоков; 6 тестов metadata, scanner и captions прошли.
- `DefaultMetaDataParser` переведён на Kotlin; порядок выбора парсера, scanner и runtime graph проверены: 11 тестов прошли.
- `FFmpegParser` переведён на Kotlin; parsing ffprobe JSON, выбор parser, scanner и captions: 8 тестов прошли.
- `JaudiotaggerParser` переведён на Kotlin; запись, чтение и удаление тегов на копии MP3, scanner, API тегов и обложки: 14 тестов прошли.
- `MetaDataParser` переведён на Kotlin; fallback метаданных, номера/годы, парсеры, scanner и редактирование: 14 тестов прошли.
- `PlaylistImportHandler`, `DefaultPlaylistImportHandler`, `XspfPlaylistImportHandler` переведены на Kotlin; импорт M3U/PLS, порядок обработчиков, разрешение путей и Ktor API: 9 тестов прошли.
- `DefaultPlaylistExportHandler` и `XspfPlaylistExportHandler` переведены на Kotlin; M3U/XSPF, порядок обработчиков и Ktor: 7 тестов прошли.
- `HlsSession` переведён на Kotlin; проверены короткоживущий процесс, повторное получение сегмента и очистка, video и captions API: 3 теста прошли.
- `AudioPlayer` переведён на Kotlin; состояния, полный/частичный аудиобуфер, завершение, закрытие ресурсов и jukebox: 7 тестов прошли.
- `ListenBrainzScrobbler` переведён на Kotlin; payload и HTTP-заголовок проверены локальным сервером, API и runtime graph: 6 тестов прошли.
- `LastFMScrobbler` переведён на Kotlin; локальным HTTP-сервером проверены handshake, подпись и формы submission/now playing, runtime и API: 7 тестов прошли.
- `LambdaUtils` переведён на Kotlin; адаптеры исключений, конкурентная проверка ключей, database API и runtime: 7 тестов прошли. Java-вызовы этих интерфейсов в проекте используют `Exception`; Kotlin не сохраняет `throws E` в JVM-сигнатуре, объявляется `throws Exception`.
- `RootUpnpProcessor` и DLNA media receiver registrar переведены на Kotlin; свойства корня, шесть категорий, URL и настройки DLNA: 9 тестов прошли.
- `ArtistUpnpProcessor` и `RecentAlbumUpnpProcessor` переведены на Kotlin; сортировка, агрегатные альбомы, ограничения, DLNA API и runtime: 8 тестов прошли.
- `AlbumUpnpProcessor` переведён на Kotlin; URL обложки с подписью, DLNA-контейнеры, граф runtime и standalone DLNA settings: 8 тестов прошли.
- `GenreUpnpProcessor` переведён на Kotlin; проверены индексация контейнеров, получение жанра, выборка треков и DLNA API: 7 тестов прошли.
- `PlaylistUpnpProcessor` переведён на Kotlin; контейнеры и получение треков, composition root и playlist API: 12 тестов прошли.
- `MediaFileUpnpProcessor` переведён на Kotlin; нормализация сортировки дочерних путей, композиция и связанные DLNA/API сценарии: 13 тестов прошли.
- `CustomContentDirectory` переведён на Kotlin; подписанные audio/video URL, форматирование ресурсов, DLNA-настройки и runtime: 8 тестов прошли.
- `DispatchingContentDirectory` переведён на Kotlin; выбор DLNA processors, browse/search-разбор, UPnP ошибки, URL и runtime: 19 тестов прошли.
- `UpnpContentProcessor` переведён на Kotlin; общий browse/metadata, pagination и generic name-search: 2 focused теста прошли.
- `Router` и `NATPMPRouter` переведены на Kotlin; проверены NAT-PMP add/delete, срок аренды и ожидание завершения запросов: 2 теста прошли.
- `UPnPService` переведён на Kotlin; проверены конфигурация устройства и иконки, выключенный DLNA при старте, идемпотентная остановка, composition root и DLNA API: 17 тестов прошли.
- `VersionService` переведён на Kotlin; локальные build.properties, кеширование версии, server-info API и runtime composition проверены: 9 тестов прошли.
- `CoverArtImageService` переведён на Kotlin; чтение embedded-artwork, обложки видео, default cover, resize, artwork API и UPnP вызовы: 11 тестов прошли.
- `MediaFolderService` переведён на Kotlin; доступ пользователей, фильтрация и кеш папок, иерархия путей, создание/удаление и scanner/playlist/UPnP/library API: 36 тестов прошли.
- `FolderBasedContentDirectory` переведён на Kotlin; корневой и playlist browse, статистика библиотеки, подписанный artwork URL и runtime: 22 теста прошли.
