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
| Универсальный `getCoverArt` по media-file, album, artist и playlist ID | Есть `/tracks/{id}/artwork`; отдельного artwork ресурса для album/artist/playlist ID нет | Если новый клиент должен получать обложку непосредственно от album/artist/playlist resource, нужны явные `.../{id}/artwork` routes или единый artwork resource, принимающий типизированный catalog ID. Сейчас track artwork API не покрывает весь старый выбор объектов. |
| `getAlbumInfo*` и `getArtistInfo*` как отдельные сущности | `/tracks/{id}/metadata` возвращает внешние artist bio и album notes рядом с track | Для экрана артиста/альбома без выбранного трека нужен отдельный `GET /artists/{id}/metadata` и `/albums/{id}/metadata` либо другой явный контракт. |
| `getIndexes` с буквенными индексами | Заменено обходом `/library/folders` и `/library/directories/{id}` | Иерархический browse работает, но буквенная навигация отдельной ручкой не предоставляется; добавить только если это реальный UX сценарий нового клиента. |
| Старые upload/change-cover-art формы сайта | В API есть загрузка файлов в library и выдача track artwork, но нет отдельной операции загрузки пользовательской artwork на album/artist | Создание/замена artwork вручную сейчас не покрыто новым API. Не смешивать это с чтением embedded/scanned artwork. |
| Podcast и internet-radio endpoints | Удалены как не входящие в текущую область Kordinator | Не являются переносом в Ktor; функция отсутствует намеренно. |
| `getLicense`, XML/JSONP, `.view`, Subsonic `u/p` и `t/s` | Удалены | Соответствует принятому решению отказаться от Subsonic совместимости. |

Этот аудит фиксирует только функциональные расхождения, обнаруженные при сравнении API. Он не означает, что каждая из перечисленных возможностей должна быть возвращена: три artwork/metadata UX вопроса требуют продуктового приоритета до реализации. Остальные маршруты не требуют Spring/servlet переноса: их единственный HTTP runtime — Ktor.

## Проверки источников

- Нет Spring MVC mapping annotations, `HttpServlet` handlers или `@WebServlet` в активном `airsonic-main/src/main/java`.
- Текущие Ktor registration points находятся в `KtorApplication.kt` и отдельных `Kordinator*Routes`/standalone route classes.
- `subsonic-rest-api`, JAXB response mapper и `JAXBWriter` удалены; production API DTO находятся в Ktor пакете.
- Main class shaded JAR — `org.airsonic.player.ktor.KtorApplicationKt`; он запускает `embeddedServer(Netty)` напрямую. История содержит отдельные срезы `6438f35` (удаление servlet bootstrap), `e623073` (удаление Spring Boot/Tomcat launch path) и `1db2296` (упаковка standalone Ktor JAR).
- Проверка packaged JAR обнаружила и исправила передачу `--airsonic.home` старым сервисам через JVM property. С исправлением jar запущен без Spring/servlet host: `GET /api/v1/health` вернул `200 {"status":"UP"}`, а старый `/rest/getLicense` вернул `404`.
- Maven `package -DskipTests` прошёл; `RuntimeConfigurationLoaderTest` и `KtorStandaloneModuleTest` прошли 22/22. Портовый smoke запуск делался на loopback и изолированном `/tmp` home; сервер завершён graceful shutdown.
