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
| `getIndexes` и просмотр директорий | Каталог предоставляет `/libraries`, `/library/folders`, `/library/folders/{id}`, `/library/directories/{id}` и `/artists?prefix={text}` | Directory routes подключены к standalone module и проверяют bearer principal/library scope. `/artists` отсортирован и поддерживает literal prefix для быстрого перехода к нужной части списка; поиск по префиксу также учитывается в total. Отдельный Subsonic-подобный список буквенных корзин не нужен новому JSON API. Ранее написанный `KordinatorLibraryRoutes` не был подключён, а его проверка доступа ссылалась на legacy-пользователей; этот audit ошибочно засчитал их активными. |
| Поиск и установка внешней обложки (`searchCoverArt`, `setCoverArt`) | `GET /api/v1/artwork/search`, `PUT /api/v1/library/directories/{id}/artwork/from-search`, `PUT /api/v1/library/directories/{id}/artwork` | Поиск через Last.fm принимает исполнителя, альбом или оба поля; выбранный адрес можно сохранить только если он является точным результатом этого поиска и находится на разрешённом HTTPS-хосте Last.fm. Также доступна прямая загрузка JPEG/PNG/GIF. Сохранение ограничено администратором, размером 20 MiB, проверкой содержимого/размеров изображения и корнем библиотеки. Покрыто `KtorStandaloneArtworkTest` и `StandaloneArtworkServiceTest`. |
| Podcast controllers и podcast REST actions | Не имеют Ktor routes | Не переносились вместе с podcast backend: сервисы, модели, DAO, настройки и JSP были отдельно удалены в `56494f2` (“Фаза 2: удаление подкастного бэкенда”). Эти handlers считаются de-scope, а не выполненным переносом. |
| Internet-radio settings, stations и очередь | Не имеют Ktor routes | Функция и связанные сервисы/DAO/модели/UI были отдельно удалены в `300688c` (“Искоренение интернет-радио…”), до Ktor-переноса. Это намеренно исключённая функция. |
| Общий `/proxy` endpoint | Не имеет общего Ktor аналога | Удалён отдельным `5c2e829` после записи использования legacy endpoint в `7d0bc59`. Новый API не предоставляет произвольную прокси-загрузку; внешняя обложка загружается отдельным Last.fm-сервисом с проверкой источника. |
| Media-file comment endpoint | Не имеет Ktor route | Удалён как obsolete в `563d96c`; пользовательские заметки трека обслуживаются `/tracks/{id}/note`. |
| `getLicense`, XML/JSONP, `.view`, Subsonic `u/p` и `t/s` | Удалены | Соответствует принятому решению отказаться от Subsonic совместимости. |

Этот аудит сравнивает поведение, а не совпадение старых URL/параметров. Перенос нельзя считать завершённым только из-за отсутствия Spring/servlet-контроллеров: маршруты должны быть подключены в standalone Ktor pipeline, использовать новую bearer-модель доступа и иметь проверки поведения. Подкасты, интернет-радио, общий proxy и устаревший комментарий не считаются перенесёнными: их функции были отдельно выведены из продукта указанными коммитами. `getLicense`, XML/JSONP, старые auth-параметры и JSP-контракты заменены новой bearer/API моделью или явно удалены по принятым решениям.

## Проверки источников

- Нет Spring MVC mapping annotations, `HttpServlet` handlers или `@WebServlet` в активном `airsonic-main/src/main/java`.
- Текущие Ktor registration points находятся в `KtorApplication.kt` и отдельных `Kordinator*Routes`/standalone route classes.
- `subsonic-rest-api`, JAXB response mapper и `JAXBWriter` удалены; production API DTO находятся в Ktor пакете.
- Main class shaded JAR — `org.airsonic.player.ktor.KtorApplicationKt`; он запускает `embeddedServer(Netty)` напрямую. История содержит отдельные срезы `6438f35` (удаление servlet bootstrap), `e623073` (удаление Spring Boot/Tomcat launch path) и `1db2296` (упаковка standalone Ktor JAR).
- Проверка packaged JAR обнаружила и исправила передачу `--airsonic.home` старым сервисам через JVM property. С исправлением jar запущен без Spring/servlet host: `GET /api/v1/health` вернул `200 {"status":"UP"}`, а старый `/rest/getLicense` вернул `404`.
- Maven `package -DskipTests` прошёл; `RuntimeConfigurationLoaderTest` и `KtorStandaloneModuleTest` прошли 22/22. Портовый smoke запуск делался на loopback и изолированном `/tmp` home; сервер завершён graceful shutdown.
- После восстановления обложек в offline Maven прогоне прошли 76 Ktor/standalone tests; изолированный `KtorStandaloneScanTest` был повторно прогнан с разрешением на loopback и тоже прошёл. PMD отключался из-за уже известных ошибок обработки исходников; Kotlin-компиляция и Surefire завершились успешно.
- Catalog browse validation: `KtorStandaloneRealScanIsolationTest` passed with prefix results, literal `%` escaping, pagination totals, and per-library visibility checks.
