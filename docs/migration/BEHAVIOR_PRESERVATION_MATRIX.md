# Матрица сохранения поведения при удалении Spring и Servlet

Матрица сравнивает функции, которые старый `AirsonicKtorServlet` передавал Spring-контроллерам, с API standalone Ktor. Внешний формат Subsonic не является требованием; пользовательское поведение и операции над медиатекой остаются требованиями, если явно не решено иное.

## Фактически зарегистрированные маршруты

Проверено 21 ноября 2026 года по точке входа `main` и регистрации `standaloneModule` в `airsonic-main/src/main/java/org/airsonic/player/ktor/KtorApplication.kt`. Это список активного `/api/v1`, не перечень всех Kotlin-файлов с обработчиками.

| Доступ | Метод и путь | Старое поведение, которое сопоставляется | Проверки активной регистрации |
|---|---|---|---|
| Публичный | `GET /api/v1/health` | Новый health endpoint | `KtorStandaloneModuleTest` |
| Публичный | `POST /api/v1/auth/login` | Вход по старым логину/паролю, заменён серверной сессией bearer token | `KtorStandaloneAuthTest` |
| Bearer | `POST /api/v1/auth/logout`, `GET /api/v1/me`, `PUT /api/v1/me/password` | Login/password/user settings; новая сессионная семантика | `KtorStandaloneAuthTest` |
| Bearer | `GET /api/v1/search`; `GET /api/v1/albums`, `/albums/{id}`, `/artists`, `/artists/{id}`, `/artists/{id}/top-tracks`, `/artists/{id}/similar-tracks`, `/tracks`, `/tracks/{id}`, `/tracks/{id}/similar-tracks`, `/genres` | `SearchController`, `IndexesController`, `AlbumsController`, `ArtistsController`, `SongController`; Last.fm-топ и похожие треки ограничиваются библиотеками пользователя, `/tracks` фильтруется по жанру, жанры учитывают доступ пользователя | `KtorStandaloneScanTest`, `KtorStandaloneRealScanIsolationTest`, `KtorStandalonePlaylistTest` |
| Bearer | `GET /api/v1/tracks/{id}/stream`, `GET /api/v1/tracks/{id}/artwork` | `StreamRestController`, `CoverArtRestController` | `KtorStandaloneScanTest` проверяет artwork, `200`, `206`, `416`, `Content-Range`, отсутствие трека и отсутствие доступа; `KtorStandaloneRealScanIsolationTest` проверяет поток и границы доступа на реальном сканере |
| Bearer | `GET /api/v1/favorites/tracks`, `/favorites/albums`, `/favorites/artists`; `PUT` и `DELETE /api/v1/tracks/{id}/favorite`, `/albums/{id}/favorite`, `/artists/{id}/favorite` | Избранное треков, альбомов и исполнителей с фильтрацией по библиотекам аккаунта | `KtorStandaloneScanTest`, `KtorStandalonePlaylistTest`, `KtorStandaloneAuthTest`, `KtorStandaloneRealScanIsolationTest` |
| Bearer | `GET` и `POST /api/v1/playlists`; `GET`, `PUT`, `DELETE /api/v1/playlists/{id}` | Основные операции `PlaylistsController`; импорт/экспорт и sharing в этой регистрации отсутствуют | `KtorStandalonePlaylistTest`, `KtorStandaloneRealScanIsolationTest` |
| Bearer | `GET`, `PUT`, `DELETE /api/v1/me/play-queue` | Персональная сохраняемая очередь треков с текущим треком и позицией; очередь не управляет аудиодекодером и не зависит от WebSocket-соединения | `KtorStandalonePlaylistTest`, `KtorStandaloneAuthTest` проверяет очистку при удалении пользователя |
| Bearer | `GET /api/v1/me/bookmarks`; `PUT`, `DELETE /api/v1/tracks/{id}/bookmark` | Персональные позиции продолжения и комментарии; позиция ближе минуты к концу удаляет закладку | `KtorStandalonePlaylistTest` |
| Bearer | `GET`, `PUT /api/v1/tracks/{id}/rating` | Персональная оценка 1–5; значение `0` удаляет оценку; чтение доступно только для треков в библиотеке пользователя | `KtorStandalonePlaylistTest` |
| Bearer | `POST /api/v1/tracks/{id}/scrobble` | Обновление счётчика прослушиваний, статуса проигрывания и вызов настроенного внешнего скробблера | `KtorStandalonePlaylistTest` |
| Bearer | `GET /api/v1/lyrics?artist=…&title=…` | Поиск текста песни через существующий `LyricsService`; `tryLater` включается по запросу | `KtorStandalonePlaylistTest`, `LyricsServiceTest` |
| Bearer | `GET /api/v1/tracks/random` | Случайные треки с теми же жанровым, годовым и библиотечным фильтрами старого `RandomSongsController` | `KtorStandalonePlaylistTest` |
| Bearer, администратор | `GET` и `POST /api/v1/admin/users`; `PUT` и `DELETE /api/v1/admin/users/{id}`; `PUT /role` и `/enabled`; `GET` и `PUT /libraries` | Список, создание, переименование и удаление аккаунтов; удаление очищает сессии, плейлисты, избранное и назначения библиотек; роль, включение и доступ к библиотекам | `KtorStandaloneAuthTest`, `KtorStandaloneScanTest`, `KtorStandaloneRealScanIsolationTest` |
| Bearer, администратор | `GET`, `POST /api/v1/admin/libraries`, `PUT /api/v1/admin/libraries/{id}` (переименование); `POST` и `GET /api/v1/admin/libraries/{id}/scan` | Music folder administration и `ScanController`, в новой модели библиотек | `KtorStandaloneLibraryTest`, `KtorStandaloneLibraryCreationTest`, `KtorStandaloneScanTest`, `KtorStandaloneRealScanIsolationTest` |

`KtorApplication.main` запускает именно `standaloneModule`. Метод `Application.module()` регистрирует только внутренний `GET /internal/ktor/ping` и не является production API. В активной регистрации ещё нет полного редактирования аккаунтов, удаления библиотек, смены пути библиотеки, upload/download, shares, HLS/transcoding, captions или статистических admin endpoints. Запись тегов в аудиофайлы намеренно исключена как небезопасная для read-only медиатеки. Редактирование библиотеки пока ограничено безопасным переименованием: смена пути требует переиндексации, а удаление непустой библиотеки в старом сервисе уничтожает её индексированные записи. Их судьба должна быть решена по функциональной потребности, а не по наличию старого URL.

Отдельные `controller/rest/**` Ktor-контроллеры и `Kordinator*Routes` не считаются подключёнными только потому, что у них есть метод `routes()` или тесты. Поиск вызовов `routes()` в production source не обнаружил регистрацию этих обработчиков из `main`. Набор `Kordinator*RoutesTest` проверяет эти классы изолированно; он не подтверждает доступность операций в запущенном приложении. Перед удалением старых обработчиков сначала нужно перенести необходимое поведение в активные `/api/v1` маршруты и проверить через `standaloneModule`.

| Функциональная область | Старый путь | Standalone Ktor | Что проверить до удаления старого кода |
|---|---|---|---|
| Вход и учётные записи | Spring Security, пользовательские REST-контроллеры и servlet principal | Есть login, bearer session, `/me`, смена пароля, создание и отключение пользователей | Сценарии входа/выхода, роли, блокировка и отзыв сессии; проверить, что все оставшиеся вызовы используют новую сессию |
| Библиотеки и сканирование | `MediaFolderService`, scanner, DAO, Spring lifecycle | Есть создание библиотеки, список администратора, запуск/status сканирования через `StandaloneLibrary*Service` | Повторное сканирование, удаление пропавших файлов, запуск/остановка задач; сверить доступ к папкам и связь с основными DAO |
| Каталог и поиск | `MediaFileService`, DAO, Lucene и REST-контроллеры | Ktor читает треки, альбомы, исполнителей и поиск из core `media_file`; публичный ID — `media_file.id` | Теги, сортировка, пагинация и соответствие старому индексу; Ktor пока читает core-таблицы напрямую через JDBC |
| Аудио и обложки | `/rest/stream`, `/ext/stream`, HLS, cover art и транскодирование | Есть прямой поток с HTTP Range и artwork для трека | MIME, диапазоны, перемотка, обложки, права доступа; HLS/транскодирование пока не перенесены в standalone |
| Очередь и плеер | Play queue, jukebox и связанные действия через servlet-путь | В standalone API отдельной очереди воспроизведения нет | Сохранить живую очередь, порядок, shuffle/repeat/skip и работу плеера; формат Subsonic можно заменить |
| Плейлисты и избранное | Плейлисты, импорт/экспорт, избранные треки/альбомы/исполнители | Есть базовый CRUD пользовательских плейлистов и избранное треков | Порядок треков, границы владельца, import/export и избранное альбомов/исполнителей |
| Остальные медиа-сценарии | Shares, captions, uploads, tag edit (намеренно исключён), avatars и statistics | Random tracks, жанры и top tracks перенесены; остальные перечисленные сценарии пока без аналога | Перенести поддерживаемую продуктом логику; не переносить намеренно исключённую запись тегов |
| JSP и HTTP-инфраструктура | JSP, servlet filters, Spring Security redirects, статические webapp resources | JSP удалён; основной JAR стартует на Ktor Netty | Для UI-only функций допустимо удаление; отдельно проверить отсутствие скрытой бизнес-логики в смешанных обработчиках |

## Правило миграции

Для каждой области перед удалением Spring/Servlet-кода:

1. Найти старый маршрут, controller, service и DAO, где выполняется операция.
2. Отделить бизнес-логику и работу с данными от HTTP/Servlet/Spring glue.
3. Подключить существующую логику к standalone Ktor либо перенести её без сужения поведения.
4. Добавить тесты на успешный сценарий, ошибки и права доступа.
5. Только после этого удалить старую точку входа и её framework-зависимости.

Текущие standalone endpoint-ы и их тесты подтверждают только перечисленный функционал. Они сами по себе не доказывают перенос всего приложения или совместное использование прежней базы данных.
