# Аудит покрытия прежних HTTP endpoints новым Ktor API

Дата аудита: 23 ноября 2026 г.

## Результат

Старые Spring MVC/servlet HTTP handlers в текущих production-исходниках отсутствуют. Ktor API зарегистрирован отдельно под `/api/v1`, использует bearer-сессии и собственные JSON DTO. Старые `/rest`, XML/JSONP и клиентские `u/p`/`t/s` контракты не сохраняются.

Сравнение сделано с последним состоянием REST controller tree до удаления Subsonic слоя (`c0cc6c0^`) и текущей регистрацией `standaloneModule`/Ktor route classes. Совпадение формы и параметров старых запросов намеренно не считается целью.

## Функции с новым Ktor API

| Область прежних handlers | Новая поверхность | Оценка |
|---|---|---|
| ping, каталог, дерево директорий, поиск, альбомы и исполнители, жанры, случайные/похожие/top tracks | `/api/v1/health`, `/libraries`, `/library/folders`, `/library/directories/{id}`, `/search`, `/tracks`, `/albums`, `/artists`, `/genres`, `/tracks/random`, `*/similar-tracks`, `*/top-tracks` | Основные сценарии есть; вместо Subsonic index/list type параметров используются Kordinator ресурсы, пагинация и фильтры. |
| song metadata, stream, download, HLS/video и captions | `/tracks/{id}`, `/tracks/{id}/metadata`, `/tracks/{id}/stream`, `/tracks/{id}/download`, `/videos`, video playback session routes, `/videos/{id}/captions` | Новые ресурсные routes; byte streaming, download, HLS и subtitles не используют старые `/rest` URLs. |
| playlists, saved queue, jukebox | `/playlists`, `/playlists/{id}`, `/me/play-queue`, `/jukebox` | CRUD и импорт playlist, сохранённая очередь и отдельное управление jukebox доступны; старые XML и live-player/Subsonic семантики не используются. |
| starred, bookmarks, rating, scrobble, lyrics | `/favorites/*`, `/tracks/{id}/favorite`, `/me/bookmarks`, `/tracks/{id}/bookmark`, `/tracks/{id}/rating`, `/tracks/{id}/scrobble`, `/lyrics` | Основные операции представлены bearer API. |
| shares/public playback, avatars | `/shares`, `/shares/{id}`, `/public/shares/{token}`, `/me/avatar` | Старые JWT share/avatar URLs заменены Kordinator token/resource URLs и доступом текущего аккаунта. |
| user/auth/password recovery | `/auth/login`, `/auth/logout`, `/me`, `/me/password`, `/auth/password-recovery`, `/admin/users/*` | Используются серверные bearer sessions; старые Subsonic credentials не поддерживаются. |
| administration/settings/health/statistics | `/admin/*` | Перенесены настройки general/transcoding/advanced/DLNA/database, libraries, players, users, health, scans, server info/log и статистика. |

## Выявленные расхождения и решения

| Старый сценарий | Текущее состояние | Следствие |
|---|---|---|
| Универсальный `getCoverArt` по media-file, album, artist и playlist ID | Есть `/tracks/{id}/artwork`, `/albums/{id}/artwork`, `/artists/{id}/artwork`, `/playlists/{id}/artwork`; у всех поддерживаются GET/HEAD | Обложки ищутся в папке трека, затем в embedded artwork, затем берётся default cover. Доступ к альбомам/исполнителям ограничен library scope, к плейлистам — владельцем и library scope. Интеграционные проверки: `KtorStandaloneRealScanIsolationTest`. |
| `getAlbumInfo*` и `getArtistInfo*` как отдельные сущности | `/tracks/{id}/metadata`, `/albums/{id}/metadata`, `/artists/{id}/metadata` | Независимые маршруты используют те же Last.fm метаданные и проверяют library scope. Интеграционные проверки: `KtorStandalonePlaylistTest`. |
| `getIndexes` и просмотр директорий | Каталог предоставляет `/libraries`, а directory routes доступны как `/library/folders`, `/library/folders/{id}`, `/library/directories/{id}` | Directory routes теперь подключены к standalone module и проверяют bearer principal/library scope. Ранее написанный `KordinatorLibraryRoutes` не был подключён, а его проверка доступа ссылалась на legacy-пользователей; этот audit ошибочно засчитал их активными. Отдельные буквенные индексы не предоставляются. |
| Поиск и установка внешней обложки (`searchCoverArt`, `setCoverArt`) | `GET /api/v1/artwork/search`, `PUT /api/v1/library/directories/{id}/artwork/from-search`, `PUT /api/v1/library/directories/{id}/artwork` | Поиск через Last.fm принимает исполнителя, альбом или оба поля; выбранный адрес можно сохранить только если он является точным результатом этого поиска и находится на разрешённом HTTPS-хосте Last.fm. Также доступна прямая загрузка JPEG/PNG. Сохранение ограничено администратором, размером 20 MiB, проверкой содержимого/размеров изображения и корнем библиотеки. Покрыто `KtorStandaloneArtworkTest` и `StandaloneArtworkServiceTest`. |
| Podcast и internet-radio endpoints | Удалены как не входящие в текущую область Kordinator | Не являются переносом в Ktor; функция отсутствует намеренно. |
| `getLicense`, XML/JSONP, `.view`, Subsonic `u/p` и `t/s` | Удалены | Соответствует принятому решению отказаться от Subsonic совместимости. |

Этот аудит сравнивает поведение, а не совпадение старых URL/параметров. Перенос нельзя считать завершённым только из-за отсутствия Spring/servlet-контроллеров: маршруты должны быть подключены в standalone Ktor pipeline, использовать новую bearer-модель доступа и иметь проверки поведения. Podcast/internet-radio пока не перенесены: это надо отдельно оценить как музыкальную backend-функциональность, а не считать выполненным переносом из-за отсутствия legacy-контрактов. XML/JSONP, старые auth-параметры и JSP-контракты заменены новой bearer/API моделью.

## Проверки источников

- Нет Spring MVC mapping annotations, `HttpServlet` handlers или `@WebServlet` в активном `airsonic-main/src/main/java`.
- Текущие Ktor registration points находятся в `KtorApplication.kt` и отдельных `Kordinator*Routes`/standalone route classes.
- `subsonic-rest-api`, JAXB response mapper и `JAXBWriter` удалены; production API DTO находятся в Ktor пакете.
- Main class shaded JAR — `org.airsonic.player.ktor.KtorApplicationKt`; он запускает `embeddedServer(Netty)` напрямую. История содержит отдельные срезы `6438f35` (удаление servlet bootstrap), `e623073` (удаление Spring Boot/Tomcat launch path) и `1db2296` (упаковка standalone Ktor JAR).
- Проверка packaged JAR обнаружила и исправила передачу `--airsonic.home` старым сервисам через JVM property. С исправлением jar запущен без Spring/servlet host: `GET /api/v1/health` вернул `200 {"status":"UP"}`, а старый `/rest/getLicense` вернул `404`.
- Maven `package -DskipTests` прошёл; `RuntimeConfigurationLoaderTest` и `KtorStandaloneModuleTest` прошли 22/22. Портовый smoke запуск делался на loopback и изолированном `/tmp` home; сервер завершён graceful shutdown.
