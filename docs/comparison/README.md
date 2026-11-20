# Сравнительный архитектурный анализ: Airsonic Advanced vs Navidrome

Комплексное инженерное исследование двух ведущих медиасерверов экосистемы Subsonic, выполненное на основе детального изучения исходных кодов:
- **Проект А (наш форк Airsonic Advanced):** `/mnt/double/Проекты/kamp/airsonic-advanced-master`
- **Проект Б (Navidrome):** `/mnt/double/Проекты/go/navidrome`

---

## Структура модульной документации

Вся аналитическая работа, таблицы дифференциалов эндпоинтов, замеры ресурсов и архитектурные схемы сохранены в модульных документах:

| № | Документ | Основные темы |
|---|---|---|
| **01** | [01_architecture_and_stack.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/01_architecture_and_stack.md) | **Архитектурный стек и потребление ресурсов:** JVM/Spring Boot/Ktor против Go/Chi/Wire; профили RAM (400–800 МБ vs 40–80 МБ); время холодного старта (30–60 с vs 200–400 мс); модели многопоточности и компиляции; декомпозиция модулей. |
| **02** | [02_filesystem_and_cataloging.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/02_filesystem_and_cataloging.md) | **Философия работы с медиатекой и файловой системой:** Read-Only против Read-Write; редактирование тегов на диске и риски повреждения; Folder-based vs Tag-based каталогизация; сборники Various Artists, AlbumArtist и Box Sets; сканеры JAudioTagger против C++ TagLib; мониторы изменений Java WatchService (inotify) vs Go fsnotify; Persistent IDs (PID). |
| **03** | [03_features_matrix.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/03_features_matrix.md) | **Сравнительная функциональная матрица:** Поддержка видео, аудиокниг, подкастов и интернет-радио; аудиодвижки Gapless, ReplayGain, Jukebox (Java Sound vs MPV socket); сетевые сервисы (DLNA/UPnP, Sonos SMAPI, Chromecast, Jellyfin API); статические и умные плейлисты (Smart Playlists AST); скробблеры Last.fm, ListenBrainz, Spotify, MusicBrainz и Sonic Similarity. |
| **04** | [04_subsonic_api_diff.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/04_subsonic_api_diff.md) | **Совместимость с Subsonic API и дифференциал эндпоинтов:** Заявляемые версии (1.15.0 vs 1.16.1); **исчерпывающая таблица всех методов `/rest/...`** без пропусков; расширения OpenSubsonic (transcodeOffset, formPost, songLyrics, indexBasedQueue, transcoding, playbackReport, apiKeyAuthentication, sonicSimilarity); эксклюзивные эндпоинты нашего форка (`kord/vibe/next`, `kord/lyrics`, `kord/dislike`, `accentColor`, admin REST). |
| **05** | [05_transcoding_and_streaming.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/05_transcoding_and_streaming.md) | **Транскодирование и стриминг:** Динамические шаблоны из БД Airsonic (%s, %b, %o) против монолитного пайплайна FFmpeg Navidrome; входное позиционирование (-ss до -i); динамический патчинг заголовков FLAC (`patchFLACDuration`); HTTP Range запросы (Partial Content 206); дисковый LRU кэш транскодирования (`TranscodingCache`). |
| **06** | [06_database_and_metadata.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/06_database_and_metadata.md) | **База данных, схемы хранения и поисковые индексы:** Мульти-СУБД (HSQLDB, PostgreSQL, MySQL) против строго SQLite 3 WAL; детальное сравнение моделей треков, альбомов и артистов; единая таблица аннотаций `annotation` против денормализованных таблиц Airsonic; Apache Lucene vs SQLite FTS5; миграторы Liquibase vs Go/SQL. |
| **07** | [07_security_and_auth.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/07_security_and_auth.md) | **Безопасность, авторизация и права доступа:** Токены Subsonic, открытые пароли, JWT и API-ключи (`nds_`); внешняя авторизация (LDAP/AD в Airsonic vs Forward Auth / Reverse Proxy в Navidrome); гранулярность ролей (12 ролей и папочный RBAC в Airsonic vs IsAdmin и Multi-Library в Navidrome); встроенный rate-limiter против brute-force. |
| **08** | [08_web_interface_and_ux.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/08_web_interface_and_ux.md) | **Веб-интерфейс и пользовательский опыт (UX):** Легаси JSP/Tomcat Jasper и стратегический переход к Headless-демону под KMP-клиент (Youamp / Kord) в Airsonic против современного React SPA / Material-UI с поддержкой PWA в Navidrome. |
| **09** | [09_conclusions_and_recommendations.md](file:///mnt/double/Проекты/kamp/airsonic-advanced-master/docs/comparison/09_conclusions_and_recommendations.md) | **Итоговые выводы и дорожная карта форка:** SWOT-анализ обоих проектов; 5 ключевых фич, которые имеет смысл портировать из Navidrome (Transcode Cache, FLAC header patching, Fast Seeking, Persistent IDs, Smart Playlists AST); сценарии неоспоримого превосходства нашего форка; пошаговый роадмап. |

---

## Ключевая сравнительная выжимка

```text
┌────────────────────────┬─────────────────────────────┬─────────────────────────────┐
│ Критерий               │ Airsonic Advanced           │ Navidrome                   │
├────────────────────────┼─────────────────────────────┼─────────────────────────────┤
│ Стек                   │ Java / Kotlin (Ktor)        │ Go (чистый бинарник)        │
│ Потребление RAM        │ 400 – 800 МБ (Idle)         │ 35 – 65 МБ (Idle)           │
│ Холодный старт         │ 25 – 60 сек                 │ 0.2 – 0.4 сек               │
│ Доступ к файлам        │ Read-Write (правка тегов)   │ Строгий Read-Only           │
│ База данных            │ HSQLDB / PostgreSQL / MySQL │ SQLite 3 (WAL, FTS5)        │
│ Видео и HLS            │ Полная поддержка            │ Отсутствует (HTTP 410)      │
│ Домашние протоколы     │ DLNA / UPnP, Sonos SMAPI    │ Отсутствуют                 │
│ Кэш транскодирования   │ Отсутствует (0%)            │ Дисковый LRU кэш (100MB+)   │
│ Идентификаторы треков  │ Привязаны к путям файлов    │ Persistent IDs (хеш MBID)   │
│ Плейлисты              │ Статические M3U             │ Smart Playlists (JSON AST)  │
│ Клиентский фокус       │ Нативный Compose Kord/Youamp│ Встроенный React SPA / PWA  │
│ Алгоритм рекомендаций  │ «Моя волна» (3-осевой фильтр)│ Sonic Similarity (векторы)  │
└────────────────────────┴─────────────────────────────┴─────────────────────────────┘
```
