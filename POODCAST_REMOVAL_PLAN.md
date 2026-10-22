# План удаления подкастов (Podcast Removal Plan)

Дата: 2026-10-22
Ветка: `podcast-removal`

## 1. Цель

Полное удаление подкастовой функциональности из проекта Airsonic Advanced:
код приложения, Subsonic REST API, база данных, веб-интерфейс, инфраструктура,
тесты, упоминания в документации.

## 2. Зафиксированные решения (по ответам пользователя)

1. Эндпоинты Subsonic REST, относящиеся к подкастам
   (`/rest/getPodcasts`, `/rest/getNewestPodcasts`, `/rest/refreshPodcasts`,
   `/rest/createPodcastChannel`, `/rest/deletePodcastChannel`,
   `/rest/deletePodcastEpisode`, `/rest/downloadPodcastEpisode`,
   `/rest/exportPodcasts/opml`), а также user-facing атрибуты (`podcastRole`,
   `podcastIndexEnabled`, `podcastIndexUrl`) **оставляются рабочими**, но
   временно выносятсяся в **специальный пакет** для последующего удаления
   (quarantine / planned deletion).
2. Все остальные артефакты подкастов (сущности, DAO, сервисы, liquibase,
   фронтенд, настройки, docker) — **удаляются без замены**.
3. Интеграция Podcast Index (ListenBrainz/PodcastIndex, `App.PODCASTINDEX`,
   credentials) — **удаляется**.

## 3. Инвентарь мест, где встречается «podcast»

### 3.1. subsonic-rest-api (Kotlin, модуль `subsonic-rest-api`)

| Файл | Роль |
|---|---|
| `subsonic-rest-api/src/main/kotlin/org/subsonic/restapi/Podcasts.kt` | DTO `Podcasts` |
| `.../PodcastChannel.kt` | DTO `PodcastChannel` |
| `.../PodcastEpisode.kt` | DTO `PodcastEpisode` |
| `.../NewestPodcasts.kt` | DTO `NewestPodcasts` |
| `.../PodcastStatus.kt` | enum `PodcastStatus` |
| `.../MediaType.kt` | enum value `PODCAST` |
| `.../User.kt` | атрибут `podcastRole` |
| `.../Response.kt` | XML-элементы `podcasts`, `newestPodcasts` |
| `.../ObjectFactory.kt` | фабрики `createPodcasts` и др. |
| `.../Child.kt` | `@XmlSeeAlso(PodcastEpisode::class)` |
| `subsonic-rest-api/src/test/.../DtoContractTest.kt` | контрактный тест |

### 3.2. airsonic-main (Java, модуль `airsonic-main`)

**Контроллеры**
- `airsonic-main/src/main/java/org/airsonic/player/controller/SubsonicRESTController.java`
  — эндпоинты `/rest/getPodcasts` … `/rest/exportPodcasts/opml`;
    user-поля `podcastRole` в `getUser`/`updateUser`;
    ветка `case PODCAST` в `search`; использование `PodcastService`,
    `CoverArtController`, `PodcastStatus`.
- `airsonic-main/src/main/java/org/airsonic/player/controller/CoverArtController.java`
  — `PODCAST_COVERART_PREFIX = "pod-"`, `PodcastCoverArtRequest`.
- `airsonic-main/src/main/java/org/airsonic/player/controller/PersonalSettingsController.java`
  — `podcastIndexEnabled`, `podcastIndexUrl`, Podcast Index creds.
- `airsonic-main/src/main/java/org/airsonic/player/controller/UserSettingsController.java`
  — назначение `Role.PODCAST`, привязка podcast music folders.
- `airsonic-main/src/main/java/org/airsonic/player/security/GlobalSecurityConfig.java`
  — `/podcastReceiverAdmin*`, роли.

**Новый пакет для запланированного удаления**

- `org.airsonic.player.removal` — сюда переносится подкастный REST-контроллер
  (`org.airsonic.player.removal.PodcastRemovalController` и аналогичные классы),
  чтобы впоследствии его можно было просто удалить.

**Сервисы**
- `service/PodcastService.java` — каналы, эпизоды, refresh, download, export.
- `service/podcast/PodcastIndexService.java` — Podcast Index клиент.
- `service/PlayQueueService.java` — `playPodcastChannel`, `playPodcastEpisode`.
- `service/MediaFileService.java` — определение podcast media type.
- `service/MediaFolderService.java` — `enablePodcastFolder`.
- `service/SecurityService.java` — запись в podcast folder.
- `service/SettingsService.java` — `PodcastUpdateInterval`, retention/download,
  shortcut `New Incoming Podcast`.
- `service/DatabaseService.java` — cleanup lists (`podcast_channel`, ...).

**DAO**
- `dao/PodcastDao.java` (+ RowMappers/inserts).
- `dao/MediaFileDao.java` — фильтры по `MediaType.PODCAST/AUDIOBOOK`.

**Домен**
- `domain/PodcastChannel.java`, `PodcastChannelRule.java`, `PodcastEpisode.java`,
  `PodcastStatus.java`, `PodcastExportOPML.java`.
- `domain/UserCredential.java` — `App.PODCASTINDEX`.
- `domain/User.java`, `domain/UserSettings.java` — podcast-атрибуты.
- `domain/MediaFile.java` — `isAudio` (MUSIC/AUDIOBOOK/PODCAST), enum `MediaType`.
- `domain/MusicFolder.java` — enum `Type.MEDIA, PODCAST`.

**Spring / инфраструктура**
- `spring/ServletConfiguration.java` — mappings `/podcast*`, `/podcastReceiverAdmin*`.
- `spring/migrations/AddMediaFileIdToPodcastChannels.java`,
  `spring/migrations/AddPodcastMediaFolderUsers.java`.

**Стриминг / realtime**
- `controller/StreamController.java` — `isPodcast` по `playlist` request param.
- `ajax/PlayQueueWSController.java` — `/play/podcastchannel`, `/play/podcastepisode`.
- `ajax/MediaFileWSController.java` — упоминание podcast-папки.

**Прочее**
- `util/Util.java` — `getDefaultPodcastFolder()`.

### 3.3. Веб (`airsonic-main/src/main/webapp`)

- JSP: `WEB-INF/jsp/podcast.jsp` (RSS), `podcastChannels.jsp`, `podcastChannel.jsp`,
  `podcastSettings.jsp`, `top.jsp`, `more.jsp`, `settingsHeader.jsp`, `playQueue.jsp`,
  `index.jsp`, `musicFolderSettings.jsp`, `personalSettings.jsp`, `userSettings.jsp`,
  `coverArt.jsp`, `databaseSettings.jsp`.
- `script/keyboard_shortcuts.js` — shortcut `g o` → `podcastChannels.view`.
- Темы: `src/main/resources/org/airsonic/player/theme/*.properties` — ключи
  `podcastImage`, `podcastLargeImage` (≈30 файлов).
- Иконки: `src/main/webapp/icons/**/podcast*`.

### 3.4. База данных, liquibase, инфраструктура

- `src/main/resources/liquibase/10.6/timestamp-precision.xml`
- `src/main/resources/liquibase/11.0/changelog.xml`, `explicit-fk-indices.xml`,
  `hsqldb-timestamp-precision.xml`, `podcast-channel-rules.xml`,
  `podcast-description-text.xml`, `podcast-episode-guid.xml`,
  `podcast-episode-indices.xml`, `relative-media-file-paths.xml`
- `src/main/resources/liquibase/legacy/schema32.xml`, `legacy/schema53.xml`
- `install/docker/Dockerfile` (`VOLUME .../podcasts`), `install/docker/run.sh`
  (`airsonic.defaultPodcastFolder`).

### 3.5. Тесты

- `airsonic-main/src/test/java/org/airsonic/player/dao/PodcastDaoTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/service/SettingsServiceTestCase.java`
- `airsonic-main/src/test/java/org/airsonic/player/dao/UserDaoTestCase.java`
- `subsonic-rest-api/src/test/java/org/subsonic/restapi/DtoContractTest.kt`

### 3.6. Документация

- `README.md`, `CHANGELOG.md` (исторические записи — не переписывать).

## 4. Пошаговый план

### Фаза 0. Подготовка

- [ ] Ветка `podcast-removal` создана (текущая точка — от `master`).
- [ ] Зафиксировать baseline-команду сборки:
      `mvn -pl airsonic-main -am clean verify` (или минимум `compile` + `checkstyle`).

### Фаза 1. Вынос подкастных REST-эндпоинтов в «пакет для запланированного удаления»

1.1. Создать пакет `org.airsonic.player.removal`.
1.2. Создать контроллер в этом пакете, например
      `org.airsonic.player.removal.PodcastRemovalController`
      (`@RestController` + `@RequestMapping("/rest", method = {GET, POST})`)
      с эндпоинтами:
      - `/getPodcasts`
      - `/getNewestPodcasts`
      - `/refreshPodcasts`
      - `/createPodcastChannel`
      - `/deletePodcastChannel`
      - `/deletePodcastEpisode`
      - `/downloadPodcastEpisode`
      - `/exportPodcasts/opml`
      
      и вспомогательными методами (`createJaxbPodcastEpisode`, создание JAXB-объектов,
      проверка `user.isPodcastRole()`).
1.3. Из `SubsonicRESTController` **удалить** эти методы, ставшие неиспользуемыми
      поля и вспомогательные методы.
1.4. Убедиться в отсутствии `Ambiguous mapping` при старте (после переноса путей
      дубликатов быть не должно — старый контроллер методов больше не объявляет).
1.5. Сборка: `mvn -pl subsonic-rest-api,airsonic-main -am compile`.

### Фаза 2. Удаление подкастного бэкенда (main)

Последовательно, в мелких шагах:

2.1. **Security**: `GlobalSecurityConfig` — убрать `/podcastReceiverAdmin*`;
      `User`/`UserSettings`/`UserCredential` — удалить podcast-атрибуты;
      `SecurityService` — убрать проверку доступа в podcast folder.
2.2. **Domain**: удалить классы `PodcastChannel`, `PodcastChannelRule`,
      `PodcastEpisode`, `PodcastStatus`, `PodcastExportOPML`; почистить
      `User`, `UserSettings`, `UserCredential` (`App.PODCASTINDEX`);
      `MediaFile` — убрать `PODCAST` из `isAudio` и enum `MediaType`;
      `MusicFolder` — убрать `Type.PODCAST`.
      > ВАЖНО: удаление enum-значения `MediaType.PODCAST` — финальный шаг после
      > вычищения всех потребителей (SQL в `MediaFileDao`, REST DTO, фронтенд).
2.3. **DAO**: удалить `PodcastDao` (+ RowMappers, insert-регистрации);
      почистить `MediaFileDao` от podcast-фильтров.
2.4. **Services**: удалить `PodcastService`, `PodcastIndexService`;
      почистить `PlayQueueService`, `MediaFileService`, `MediaFolderService`,
      `SettingsService`, `StreamController`, `PlayQueueWSController`,
      `MediaFileWSController`.
2.5. **Spring**: удалить mappings в `ServletConfiguration`;
      удалить миграции `AddMediaFileIdToPodcastChannels`,
      `AddPodcastMediaFolderUsers`.

### Фаза 3. Удаление подкастной схемы БД

3.1. Добавить **новые** liquibase changesets (не править исторические):
      - drop таблиц/индексов/FK: `podcast_episode`, `podcast_channel_rules`,
        `podcast_channel` (в корректном порядке зависимостей);
      - удалить ставшие неактуальными колонки (`path`, `media_file_id` и т.п.).
3.2. Обновить `DatabaseService.TABLE_ORDER` (cleanup lists).
3.3. Проверить legacy changesets (`liquibase/legacy/schema32.xml`,
      `schema53.xml`) — исторические, не правать; новые drops должны работать
      на чистой схеме.

### Фаза 4. Удаление подкастного веб-кода

4.1. Удалить JSP: `podcast.jsp`, `podcastChannels.jsp`, `podcastChannel.jsp`,
      `podcastSettings.jsp`.
4.2. Подправить JSP: `top.jsp`, `more.jsp`, `settingsHeader.jsp`, `playQueue.jsp`,
      `index.jsp`, `musicFolderSettings.jsp`, `personalSettings.jsp`,
      `userSettings.jsp`, `coverArt.jsp`, `databaseSettings.jsp`.
4.3. `webapp/script/keyboard_shortcuts.js` — удалить shortcut `g o`.
4.4. Темы: удалить ключи `podcastImage`, `podcastLargeImage` из всех
      `theme/*.properties`.
4.5. Иконки: удалить `podcast*` файлы из `webapp/icons/`.
4.6. i18n: удалить podcast-ключи из всех `ResourceBundle_*.properties`.

### Фаза 5. Тесты

- [ ] Удалить `PodcastDaoTestCase`.
- [ ] Обновить `SettingsServiceTestCase` (podcast settings/shortcuts).
- [ ] Обновить `UserDaoTestCase`.
- [ ] Обновить `DtoContractTest` (podcast DTO/enum/поля).

### Фаза 6. Инфраструктура и документация

- Docker: удалить VOLUME `podcasts`, `airsonic.defaultPodcastFolder`,
      упоминания в `run.sh`.
- README.md: удалить/обновить разделы про подкасты.

## 5. Возможные проблемы и решения

| # | Проблема | Решение |
|---|---|---|
| 1 | `MediaType.PODCAST` соседствует с `AUDIOBOOK`; используется в enum, JAXB/REST-контракте, SQL | Удалять значение `PODCAST` только после полной вычистки потребителей; проверить `/rest/search` и `getSongsByGenre`/`getTopSongs` SQL. |
| 2 | Ambiguous mapping при переносе эндпоинтов | Новый контроллер получает `@RequestMapping("/rest", ...)`; методы объявляются с прежними путями; старые методы удаляются из `SubsonicRESTController`. |
| 3 | Liquibase: historical changesets уже применены | Не править историю; добавлять новые drop-changesets с preconditions (`tableExists`). |
| 4 | FK/drop порядок | `podcast_episode` → `podcast_channel`; отдельно `media_file_id` FK. |
| 5 | `user_roles` хранится как JSON | При удалении `Role.PODCAST` — миграция JSON-значений (`UserRolesJsonColumnPopulation`). |
| 6 | HSQLDB vs MySQL syntax | Проверить `liquibase` changesets на обеих СУБД. |
| 7 | i18n fallback | Удалять ключи во всех языках; проверить зависимости других ключей. |
| 8 | checkstyle | Запускать `mvn checkstyle:check` после правок. |
| 9 | `StreamController`: `playlist` request param | Перед удалением убедиться, что параметр не используется обычными плейлистами (`PlaylistService.getFilesInPlaylist`). |
| 10 | Docker volume и env-параметры. |
| 11 | GettingStarted/Home/MediaFileWSController — проверить упоминания после регрессии. |
| 12 | Accented? — нет. |

## 6. Критерии готовности

- [ ] `mvn -pl airsonic-main -am clean verify` (compile + checkstyle + tests) проходит без подкастов.
- [ ] Podcast REST-эндпоинты отсутствуют или находятся в quarantine-пакете.
- [ ] Схема БД без podcast-таблиц.
- [ ] Веб-интерфейс и REST-контракт без подкастов.

## 7. Следующий шаг

После утверждения плана — начать **Фазу 1** (вынос REST-эндпоинтов в пакет `org.airsonic.player.removal`).
