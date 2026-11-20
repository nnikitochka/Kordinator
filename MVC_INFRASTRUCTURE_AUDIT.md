# Аудит HTTP-инфраструктуры после переноса контроллеров

Дата: 16 ноября 2026 года. Исходный аудит после `d18deda`; ниже учтён последующий перенос статики и общих HTTP-ошибок после `8110d21`.

## Прикладные обработчики

Поиск `@Controller`, `@RestController`, аннотаций mappings, `HttpRequestHandler`, `HandlerMapping`, `addViewController` и `RouterFunction` по всему `airsonic-main/src/main/java` не обнаруживает прикладных MVC-контроллеров: их число теперь равно 0. Все 14 контроллеров текущего плана и устаревший пакет `org.airsonic.player.removal` удалены. Ktor регистрируется отдельным servlet `ktor`; точные пути и семейства `/rest/*`, `/ext/*` перечислены в `KtorConfiguration`. JSP передаются через `RequestDispatcher.forward` из servlet adapter; Spring-контроллер для этого не вызывается.

`org.airsonic.player.removal` удалён целиком: `PodcastRemovalController` и `PodcastExportOPML`. Его восемь прежних адресов `/rest/getPodcasts`, `/rest/getNewestPodcasts`, `/rest/refreshPodcasts`, `/rest/createPodcastChannel`, `/rest/deletePodcastChannel`, `/rest/deletePodcastEpisode`, `/rest/downloadPodcastEpisode` и `/rest/exportPodcasts/opml` больше не зарегистрированы; совпадающих маршрутов в Ktor не найдено. `KtorServletIntegrationTest` проверяет для всех восьми ответ HTTP 404 без редиректа.

## Что ещё требует Spring MVC и servlet-контейнер

| Компонент | Потребитель и решение |
|---|---|
| `LegacyViewSuffixHandlerMapping` | Остаётся для совместимости `.view` адресов, перенесённых в Ktor, и разрешения неизвестных адресов до error dispatch. Удалённый пакет `removal` больше не является его потребителем. |
| `DispatcherServlet`, MVC resource handlers | MVC resource mappings отключены; статика и `/error` перенесены в Ktor. DispatcherServlet остаётся для разрешения неизвестных адресов до servlet error dispatch. `spring-boot-starter-web` пока сохраняется. |
| `jspViewResolver` / `JstlView` | Явный JSP ViewResolver, prefix/suffix properties и `error.jsp` удалены. Ktor отдаёт ошибку без JSP; остальные страницы используют прямой JSP forward. Boot может создавать свой неиспользуемый default resolver — это не объявляется полным отключением MVC. |
| `LoggingExceptionResolver` | Остаётся для исключений MVC при разрешении неизвестных адресов. Ktor-запросы не делегируются ему. |
| LocaleResolver, MessageSource, theme resolver/source, Spring tags, JSTL | Остаются: JSP используют `spring:theme`, `fmt:message`, security tags и custom tags. Servlet adapter выставляет locale/theme/context перед JSP forward. |
| `ServletConfiguration` | Остаётся: содержит также bootstrap, encoding, REST, cache/header и metrics filters, а также validator. Нельзя удалить класс целиком вместе с MVC. |
| Spring Security, DI, транзакции | Сохраняются за пределами объёма миграции контроллеров. Авторизация и CSRF выполняются до Ktor servlet; POST login/logout обслуживает security chain. |
| Tomcat/Jasper, Servlet API | Сохраняются для Ktor servlet engine, JSP и filters. Перенос на самостоятельный Ktor engine требует отдельной работы. |

Точный servlet mapping выбирается контейнером раньше MVC HandlerMapping. Ktor aliases поэтому не требуют suffix-forward. JSP выполняет servlet forward. Для общих ошибок теперь зарегистрирован ErrorPageRegistrar, направляющий контейнер на Ktor error endpoint; ErrorMvcAutoConfiguration исключён, BasicErrorController не создаётся. Фильтры и security chain продолжают использовать действующую конфигурацию dispatcher types. Удалённый `removal` больше не имеет INCLUDE-контрактов.

## Последующий перенос статики и ошибок

В Ktor прямо зарегистрированы `/script/*`, `/style/*`, `/icons/*`, `/favicon.ico`, `/browserconfig.xml`, `/index.html` и настроенный `server.error.path` (по умолчанию `/error`). Пустой servlet mapping `""` обслуживает только корень контекста `/airsonic/`, отдавая прежний `index.html`; это не default mapping `/` и не перехватывает остальные MVC-пути. Доступ к корневым файлам остаётся под действующими правилами Security. Ресурсы читаются через ServletContext URL и один URLConnection/stream, без зависимости от unpacked WAR/getRealPath. Закрытие потока освобождает соответствующий ресурс, включая JarURLConnection. Сохранены MIME, существующий cache filter, Last-Modified, условный GET и одиночные Range/If-Range. Для нескольких диапазонов возвращается полное представление HTTP 200. Каталоги не перечисляются; пути с переходами, backslash, повторным percent encoding, WEB-INF/META-INF и управляющими символами не выдаются.

Общий error route сохраняет статус из servlet ERROR attributes и исходный путь без query string, согласует HTML/JSON с Accept, выставляет no-store и учитывает политики `server.error.include-message`, `include-stacktrace`, `include-path`, `include-exception`. JSON строится ObjectMapper; HTML экранирует динамические поля. Стандартные Subsonic envelope и явные ответы media routes не переоформляются. Внешний вид общей HTML-ошибки теперь минимальный и не использует JSP/runtime diagnostics таблицу.

Особенность Ktor servlet engine 3.6: native ERROR dispatch завершается раньше асинхронной записи ответа. Поэтому adapter выбирает встроенный blockingService Ktor только для error endpoint с ERROR dispatch/attributes; сама обработка остаётся в Ktor route. Обычная статика и REST продолжают использовать асинхронный engine. Данные инфраструктурного запроса копируются в Setup до routing wrapper; servlet facade внутри маршрута не приводится и не удерживается.

Отключение MVC resource mappings выявило зависимость выбора security chain от MVC: `MvcRequestMatcher` при отсутствии matchable MVC handler переходит на `UrlPathHelper.getLookupPathForRequest`, который для prefix servlet mappings видит только pathInfo. Поэтому `/ext/share/PublicTest` и `/ext/hls/segment/segment.ts` переставали выбирать внешнюю цепочку. Только selector внешней цепочки заменён на явный `AntPathRequestMatcher("/ext/**")`, сравнивающий servletPath + pathInfo. JWT-проверка и правила ролей не менялись. Источник механизма: установленный Spring Security 6.5.3, `MvcRequestMatcher.DefaultMatcher` и `AntPathRequestMatcher.getRequestPath`; отказ и исправление проверяются реальными signed share/HLS запросами.

Проверки последующего этапа:

- Финальный регрессионный запуск: **34/34 HTTP-теста** в `StaticAndErrorIntegrationTest`, `MvcInfrastructureIntegrationTest`, `KtorServletIntegrationTest`, `SettingsEntryIntegrationTest`, `PublicShareIntegrationTest`, `IndexPageIntegrationTest`, `HlsPlaybackIntegrationTest`, `LoginPageIntegrationTest`; Maven BUILD SUCCESS. После последней правки игнорирования Range у HEAD повторно прошёл весь `StaticAndErrorIntegrationTest` (9/9).
- Ещё 9 сценариев в `PlayerSettingsIntegrationTest`, `LiveQueueIntegrationTest`, `StreamIntegrationTest`, `WebsiteDownloadIntegrationTest`, `AvatarRestIntegrationTest`, `CoverArtRestIntegrationTest`, `WebsiteDirectoryIntegrationTest` прошли в расширенном прогоне. Итого проверены **43 различных HTTP-сценария**. Первые расширенные прогоны обнаружили ошибки lifecycle/routing/security и противоречивую проверку include-path; они исправлены, затронутые классы повторно прошли. Это выбранная регрессия, а не все тесты проекта.
- Все 29 JS-файлов прошли; PMD включён и прошёл. Checkstyle отключён из-за подтверждённого несовместимого parser для существующего `switch` в PlayQueue.java:430.
- Новый класс проверяет точные байты JS/CSS/SVG/index, анонимный доступ к трём каталогам, welcome page, MIME/Content-Length GET/HEAD, Cache-Control, Last-Modified/304, одиночные/suffix/open-ended/invalid Range/If-Range, игнорирование Range у HEAD, запрет каталогов/traversal, отсутствие BasicErrorController, native ERROR после GET/POST, FORWARD с ERROR attributes, HTML escaping, JSON, Accept q и политики диагностических полей. Внешний WAR-контейнер отдельно не разворачивался: его FORWARD-сценарий воспроизведён test servlet.
- Воспроизведение: тот же Maven, `XDG_DATA_HOME=/tmp`, `-pl airsonic-main -Dkotlin.compiler.daemon=false -Dcheckstyle.skip=true -Dtest=<классы через запятую> test`. Проверки выполнялись без Firefox.

## Локализация и клиентские проверки

Причина `???accessDenied.title???` в предыдущих тестах — `src/test/resources/application.properties` перекрывал production-файл и не задавал `spring.messages.basename`. Прямая проверка `MessageSource` воспроизвела `NoSuchMessageException` до HTTP. В тестовую конфигурацию добавлен тот же basename, что уже был в production. Production wiring не менялся. Новый HTTP-тест требует английский и русский текст, а также проверяет статику, servlet mappings и статусы ошибок при context path `/airsonic`.

Две устаревшие JS-проверки обновлены после прежних миграций: popup lyrics получает значения из escaped request parameters, а избранное изменяет состояние и изображение внутри текущего `actionCell`. Проверки по-прежнему исполняют код и требуют обновления состояния только после успешного REST callback.

## Проверки исходного аудита (`8110d21`)

- Все 29 JS-файлов прошли: `node --test airsonic-main/src/test/javascript/*.test.cjs`.
- HTTP-регрессия прошла: **34 теста в 14 классах**, 0 failures/errors/skipped, Maven `BUILD SUCCESS`. Классы: `MvcInfrastructureIntegrationTest`, `KtorServletIntegrationTest`, `LoginPageIntegrationTest`, `IndexPageIntegrationTest`, `LiveQueueIntegrationTest`, `PlayerSettingsIntegrationTest`, `StreamIntegrationTest`, `HlsPlaybackIntegrationTest`, `WebsiteDownloadIntegrationTest`, `PublicShareIntegrationTest`, `AvatarRestIntegrationTest`, `CoverArtRestIntegrationTest`, `WebsiteDirectoryIntegrationTest`, `SettingsEntryIntegrationTest`. Включены login/logout/CSRF, очередь, права, media responses, HLS сегменты, файлы, public shares, изображения, локализация и Subsonic XML/JSON. Это выбранная регрессия, а не запуск всех тестов репозитория.
- Воспроизведение: Maven `-pl airsonic-main -Dkotlin.compiler.daemon=false -Dcheckstyle.skip=true -Dtest=<перечисленные классы через запятую> test`, `XDG_DATA_HOME=/tmp`. PMD не отключался и завершился успешно.
- PMD включён в регрессионном Maven-запуске. Checkstyle пришлось отключить: его parser падает на существующем `switch` в `domain/PlayQueue.java:430`, до компиляции тестов.
- Поиск в production-исходниках и POM не обнаружил восстановленных STOMP/SockJS/WebSocket endpoints или прямых зависимостей. Транзитивный `ktor-websockets` в библиотечном classpath сам по себе не регистрирует WebSocket-маршруты.
- Firefox не запускался. Это HTTP/JS-проверки, а не ручной браузерный прогон.

## Следующий объём работ

1. Заменить JSP и Spring tags клиентским интерфейсом, сохранив данные и действия в Ktor REST.
2. Статика и общие HTTP-ошибки перенесены в Ktor; явная MVC ViewResolver/resource/error infrastructure удалена или отключена. Далее проверить возможность убрать оставшийся DispatcherServlet после отдельной оценки обработки неизвестных адресов.
3. Пакет `removal` удалён; его stub-маршруты больше не зарегистрированы. `LegacyViewSuffixHandlerMapping` и DispatcherServlet пока остаются как общая инфраструктура неизвестных адресов и suffix-совместимости.
4. Отдельно перенести authentication/session/CSRF и servlet filters, если требуется самостоятельный Ktor engine.
5. После этого отключать servlet engine и Spring MVC зависимости; DI и транзакции рассматривать отдельно.

Перенос контроллеров не равен полному удалению Spring: текущие остаточные зависимости перечислены выше.
