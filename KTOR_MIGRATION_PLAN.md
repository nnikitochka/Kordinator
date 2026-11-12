# План полного перехода со Spring на Ktor и вывода старого стека

Дата составления: Ноябрь 2026  
Проект: Airsonic Advanced  
Клиент нового поколения: Youamp Desktop (Kotlin Multiplatform / Compose Desktop)

---

## 1. Цель и видение архитектуры

**Конечная цель:** Полное устранение зависимостей Spring (`org.springframework.*`), встроенного контейнера сервлетов Tomcat, JSP-интерфейса и переход на чистый, легковесный, асинхронный сервер на **Ktor** (Netty / CIO engine) на 100% Kotlin.

### Целевое состояние системы:
* **Бэкенд:** Headless-демон на Ktor Server + Kotlin Coroutines.
* **API для плееров:** 100% совместимый Subsonic REST API (XML/JSON).
* **API для управления:** Лаконичный Admin JSON API (управление папками, транскодерами, настройками).
* **Архитектура протокола:** 100% Stateless HTTP REST. Никаких постоянных WebSocket/STOMP соединений. Опрос статусов (сканирование, очередь) через стандартный REST polling.
* **Фронтенд:** Автономный десктопный клиент **Youamp** (Compose Multiplatform), не требующий хостинга веб-страниц сервером.

---

## 2. Текущее состояние (Status Quo)

* [x] **Subsonic REST API (100% на Ktor):**
  * Все методы вынесены из монолита в 34 контроллера в `controller/rest/`.
  * `SubsonicRESTController.java` полностью удалён.
  * Стриминг, HLS, скачивание, обложки, плейлисты, пользователи — работают на Ktor.
* [x] **Ликвидация Spring STOMP / WebSocket (пакет `ajax/`):**
  * Все 11 контроллеров удалены (`BookmarksWSController`, `LyricsWSController`, `PlaylistWSController`, `StarWSController`, `NowPlayingWSController`, `CoverArtWSController`, `UserSettingsWSController`, `PlayQueueWSController`, `MediaFileWSController`, `ArtistWSController`, `TagWSController`).
  * 0 аннотаций `@MessageMapping` в кодовой базе.
  * Вызовы в 53 JSP заменены на 7 компактных Vanilla JS клиентов в `webapp/script/` с поддержкой CSRF.
  * 12 тестовых наборов (98 unit-тестов) для JS-клиентов проходят через Bun за ~180 мс.
* [~] **Ktor Runtime:**
  * Работает как сервлет (`AirsonicKtorServlet`) внутри встроенного Tomcat под управлением Spring Boot.
* [ ] **Веб-интерфейс и легаси JSP:**
  * 53 JSP страницы (временный переходный слой до готовности Youamp Desktop);
  * 30 файлов локализации `ResourceBundle_*.properties` (~25 000 строк).
* [ ] **Ядро (Сервисы и DAO):**
  * Активно переписываются на Kotlin, но всё ещё содержат аннотации Spring (`@Service`, `@Transactional`, `@Autowired`).
  * Безопасность завязана на Spring Security `SecurityContextHolder`.

---

## 3. Поэтапный план работ

```
┌────────────────────────────────────────────────────────────────────────┐
│ Фаза 1: Перевод JSP на REST и удаление WebSocket/STOMP (Завершено)    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Фаза 2: Admin JSON API на Ktor (Управление папками, транскодерами)     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Фаза 3: Очистка ядра (Сервисы, DAO, транзакции без Spring)             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Фаза 4: Ktor Authentication (Автономная авторизация без Spring Security)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Фаза 5: Интеграция с Youamp Desktop (Воспроизведение + Админка)        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Фаза 6: «День X» — Удаление JSP, Spring MVC и Tomcat (Чистый Ktor)     │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Фаза 1. Перевод JSP на REST и ликвидация Spring STOMP / WebSockets [ВЫПОЛНЕНО]

**Цель:** Ликвидировать монструозный Spring STOMP WebSocket и удалить пакет `ajax/`, переведя оставшийся веб на уже готовый REST API.

1. **[x] Перевод команд в JSP на HTTP fetch (12 файлов):**
   * Заменены все вызовы `top.StompClient.send(...)` на стандартный `fetch('/rest/...')` и вспомогательные клиенты.
2. **[x] Недостающие вспомогательные ручки на Ktor:**
   * Реализован `POST /api/tags` (`TagRestController.kt` для `editTags.jsp`).
   * Реализован `POST /api/coverart/set` и `search` (для `changeCoverArt.jsp`).
   * Реализован `UserSettingsRestController.kt` (`/api/usersettings`).
3. **[x] Архитектурное решение по WebSockets:**
   * Kord признан 100% Stateless HTTP REST сервером — WebSockets не нужны (ни Spring STOMP, ни Ktor WebSockets).
   * Статусы сканирования и очередь опрашиваются клиентами через REST polling.
4. **[x] Удаление легаси WebSockets:**
   * [x] Полностью удалён пакет `org.airsonic.player.ajax` (30 классов).
   * [x] Удалён `WebsocketConfiguration.java` (-587 строк), `websocket.jsp` (-174 строки), библиотеки `sockjs` и `stomp`.
   * [x] Исключён `spring-boot-starter-websocket` из `pom.xml`.
   * [x] 0 упоминаний `SimpMessagingTemplate` и `StompClient` во всем `src/main`.
   * [x] Все 126 unit-тестов в 16 сьютах JavaScript проходят за ~280 мс через Bun.

---

### Фаза 2. Реализация Admin JSON API на Ktor

**Цель:** Предоставить API для администрирования сервера новому клиенту (Youamp Desktop).

Создать модуль/пакет `org.airsonic.player.controller.api.admin`:
1. **Папки медиатеки (`/api/admin/folders`):**
   * `GET /api/admin/folders` — список папок с путями и статусами;
   * `POST /api/admin/folders` — добавление новой папки;
   * `PUT /api/admin/folders/{id}` — включение/выключение, переименование;
   * `DELETE /api/admin/folders/{id}` — удаление папки;
   * *(Делегирует в `MediaFolderService`).*
2. **Транскодирование (`/api/admin/transcoding`):**
   * `GET/POST/PUT/DELETE /api/admin/transcoding` — управление правилами и параметрами `ffmpeg`;
   * *(Делегирует в `TranscodingService`).*
3. **Системные настройки (`/api/admin/settings`):**
   * `GET/PUT /api/admin/settings` — серверные лимиты, кэш, интервалы;
   * *(Делегирует в `SettingsService`).*
4. **Обслуживание БД (`/api/admin/database`):**
   * `POST /api/admin/database/backup`, `/clear-cache`.

---

### Фаза 3. Очистка ядра (Сервисы и DAO) от Spring

**Цель:** Сделать бизнес-логику полностью независимой от Spring ApplicationContext.

1. **Рефакторинг сервисов на Kotlin:**
   * Завершить конвертацию оставшихся Java-сервисов в Kotlin.
   * Удалить аннотации `@Service`, `@Component`, `@Autowired`.
   * Перевести все зависимости на **чистый конструкторный DI** (`class MediaFolderService(private val musicFolderDao: MusicFolderDao)`).
2. **Создание легаси-моста (`LegacySpringBridgeConfig.kt`):**
   * Чтобы старый JSP и Spring MVC продолжали работать до конца:
     ```kotlin
     @Configuration
     open class LegacySpringBridgeConfig(private val coreContext: CoreContext) {
         @Bean open fun mediaFolderService() = coreContext.mediaFolderService
         @Bean open fun transcodingService() = coreContext.transcodingService
         @Bean open fun settingsService() = coreContext.settingsService
     }
     ```
   * Старый Spring MVC получает сервисы из моста, а Ktor — напрямую из ядра.
3. **Отвязка от Spring Transactions и JDBC:**
   * Заменить `@Transactional` на легковесный блок `transaction { ... }` (через собственный хелпер транзакций / Exposed / чистый JDBC).
   * Изолировать или вытеснить `JdbcTemplate`.

---

### Фаза 4. Автономная аутентификация в Ktor (Ktor Auth)

**Цель:** Устранить зависимость Ktor от сервлетных фильтров и `SecurityContextHolder`.

1. **Плагин Subsonic Auth:**
   * Реализовать Ktor Authentication Provider для Subsonic-протокола:
     * Проверка `u` + `p` (пароль);
     * Проверка `u` + `t` + `s` (токен и соль);
     * Поддержка HTTP Basic Auth.
2. **Плагин Admin Auth:**
   * Bearer-токен или API-ключ для роутов `/api/admin/*`.
3. **Cookie / Session Support (для браузера):**
   * Ktor Sessions для авторизованных сессий веб-интерфейса.
4. Запросы в Ktor больше не трогают цепочку фильтров Spring Security.

---

### Фаза 5. Интеграция с Youamp Desktop

**Цель:** Довести новый десктопный фронтенд до функциональной независимости от старого веба.

1. **Общий модуль моделей (Shared KMP):**
   * Переиспользовать DTO из модуля `subsonic-rest-api` в Youamp через Ktor Client.
2. **Экран управления сервером в Youamp:**
   * Реализовать в Youamp UI добавление/удаление папок медиатеки и запуск сканирования через `/api/admin/*`.
3. **Подключение Ktor WebSocket (`/ws/events`):**
   * Слушать прогресс сканирования и события `nowPlaying` прямо в Compose Desktop.

---

### Фаза 6. «День X» — Удаление Spring и запуск чистого Ktor

**Цель:** Окончательное освобождение кодовой базы от легаси.

1. **Удаление JSP и старого веба:**
   * Удалить папку `airsonic-main/src/main/webapp/` целиком (53 JSP, CSS, JS, tld).
   * Удалить 51 старый Spring MVC контроллер (`controller/*Controller.kt`).
   * Удалить 30 файлов локализации `src/main/resources/org/airsonic/player/i18n/ResourceBundle_*.properties`.
2. **Удаление Spring Boot и Tomcat:**
   * Удалить `AirsonicApplication.java`, `AirsonicKtorServlet.kt`, `KtorConfiguration.java`, `LegacySpringBridgeConfig.kt`.
   * Вычистить из `pom.xml`:
     * `spring-boot-starter-web`
     * `spring-boot-starter-tomcat`
     * `spring-security-*`
     * `apache-mime4j`, `jasper`, `jstl`.
3. **Перевод на Standalone Ktor:**
   * Создать чистый `Main.kt`:
     ```kotlin
     fun main() {
         embeddedServer(Netty, port = 8080) {
             install(WebSockets)
             install(ContentNegotiation) { json() }
             install(Authentication) { ... }
             configureSubsonicRoutes()
             configureAdminRoutes()
             configureEventRoutes()
         }.start(wait = true)
     }
     ```
4. **Результат:**
   * Запуск сервера за < 1 секунды.
   * Потребление памяти снижено в 3–4 раза.
   * 100% Kotlin-стек: чистое ядро + Ktor бэкенд + Compose Desktop клиент (Youamp).
