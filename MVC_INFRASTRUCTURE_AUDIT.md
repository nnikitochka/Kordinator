# Аудит HTTP-инфраструктуры после переноса контроллеров

Дата: 16 ноября 2026 года. Основание: исходники после `d18deda` и регрессии завершающего этапа.

## Прикладные обработчики

Поиск `@Controller`, `@RestController`, аннотаций mappings, `HttpRequestHandler`, `HandlerMapping`, `addViewController` и `RouterFunction` по всему `airsonic-main/src/main/java` не обнаружил прикладных MVC-контроллеров вне `org.airsonic.player.removal`. Все 14 контроллеров текущего плана удалены. Ktor регистрируется отдельным servlet `ktor`; точные пути и семейства `/rest/*`, `/ext/*` перечислены в `KtorConfiguration`. JSP передаются через `RequestDispatcher.forward` из servlet adapter; Spring-контроллер для этого не вызывается.

Единственный оставшийся исходный `@Controller` — `removal/PodcastRemovalController`. Пакет исключён владельцем проекта из работ. Его исходники и поведение не менялись.

## Что ещё требует Spring MVC и servlet-контейнер

| Компонент | Потребитель и решение |
|---|---|
| `LegacyViewSuffixHandlerMapping` | Остаётся: обеспечивает suffix-совместимость оставшихся MVC-заглушек из `removal`. Перенесённые адреса зарегистрированы прямо в Ktor, включая необходимые `.view`. Неизвестные `.view` по-прежнему заканчиваются 404. |
| `DispatcherServlet`, MVC resource handlers | Остаются для Boot `/error`, статики и исключённого `removal`. Нельзя отключить `spring-boot-starter-web` без замены этих потребителей. |
| `jspViewResolver` / `JstlView` | Остаются для framework error view `error.jsp`. Прикладные Ktor-страницы используют прямой JSP forward. |
| `LoggingExceptionResolver` | Остаётся для исключений в MVC/framework/resource handling. Ktor-запросы не делегируются ему; удалить его сейчас означало бы изменить журналирование оставшегося MVC. |
| LocaleResolver, MessageSource, theme resolver/source, Spring tags, JSTL | Остаются: JSP используют `spring:theme`, `fmt:message`, security tags и custom tags. Servlet adapter выставляет locale/theme/context перед JSP forward. |
| `ServletConfiguration` | Остаётся: содержит также bootstrap, encoding, REST, cache/header и metrics filters, а также validator. Нельзя удалить класс целиком вместе с MVC. |
| Spring Security, DI, транзакции | Сохраняются за пределами объёма миграции контроллеров. Авторизация и CSRF выполняются до Ktor servlet; POST login/logout обслуживает security chain. |
| Tomcat/Jasper, Servlet API | Сохраняются для Ktor servlet engine, JSP и filters. Перенос на самостоятельный Ktor engine требует отдельной работы. |

Точный servlet mapping выбирается контейнером раньше MVC HandlerMapping. Ktor aliases поэтому не требуют suffix-forward. JSP выполняет servlet forward; Boot обрабатывает error dispatch отдельно. Фильтры и security chain продолжают использовать действующую конфигурацию dispatcher types. Контракты INCLUDE в исключённом `removal` не изменялись и отдельно не тестировались.

## Локализация и клиентские проверки

Причина `???accessDenied.title???` в предыдущих тестах — `src/test/resources/application.properties` перекрывал production-файл и не задавал `spring.messages.basename`. Прямая проверка `MessageSource` воспроизвела `NoSuchMessageException` до HTTP. В тестовую конфигурацию добавлен тот же basename, что уже был в production. Production wiring не менялся. Новый HTTP-тест требует английский и русский текст, а также проверяет статику, servlet mappings и статусы ошибок при context path `/airsonic`.

Две устаревшие JS-проверки обновлены после прежних миграций: popup lyrics получает значения из escaped request parameters, а избранное изменяет состояние и изображение внутри текущего `actionCell`. Проверки по-прежнему исполняют код и требуют обновления состояния только после успешного REST callback.

## Проверки

- Все 29 JS-файлов прошли: `node --test airsonic-main/src/test/javascript/*.test.cjs`.
- HTTP-регрессия прошла: **34 теста в 14 классах**, 0 failures/errors/skipped, Maven `BUILD SUCCESS`. Классы: `MvcInfrastructureIntegrationTest`, `KtorServletIntegrationTest`, `LoginPageIntegrationTest`, `IndexPageIntegrationTest`, `LiveQueueIntegrationTest`, `PlayerSettingsIntegrationTest`, `StreamIntegrationTest`, `HlsPlaybackIntegrationTest`, `WebsiteDownloadIntegrationTest`, `PublicShareIntegrationTest`, `AvatarRestIntegrationTest`, `CoverArtRestIntegrationTest`, `WebsiteDirectoryIntegrationTest`, `SettingsEntryIntegrationTest`. Включены login/logout/CSRF, очередь, права, media responses, HLS сегменты, файлы, public shares, изображения, локализация и Subsonic XML/JSON. Это выбранная регрессия, а не запуск всех тестов репозитория.
- Воспроизведение: Maven `-pl airsonic-main -Dkotlin.compiler.daemon=false -Dcheckstyle.skip=true -Dtest=<перечисленные классы через запятую> test`, `XDG_DATA_HOME=/tmp`. PMD не отключался и завершился успешно.
- PMD включён в регрессионном Maven-запуске. Checkstyle пришлось отключить: его parser падает на существующем `switch` в `domain/PlayQueue.java:430`, до компиляции тестов.
- Поиск в production-исходниках и POM не обнаружил восстановленных STOMP/SockJS/WebSocket endpoints или прямых зависимостей. Транзитивный `ktor-websockets` в библиотечном classpath сам по себе не регистрирует WebSocket-маршруты.
- Firefox не запускался. Это HTTP/JS-проверки, а не ручной браузерный прогон.

## Следующий объём работ

1. Заменить JSP и Spring tags клиентским интерфейсом, сохранив данные и действия в Ktor REST.
2. Реализовать статику и общие HTTP-ошибки в Ktor; затем убрать MVC ViewResolver/resource/error infrastructure после проверки всех потребителей.
3. Решение об окончательном удалении `removal` принимает владелец проекта; до него suffix adapter и MVC-заглушки сохраняются.
4. Отдельно перенести authentication/session/CSRF и servlet filters, если требуется самостоятельный Ktor engine.
5. После этого отключать servlet engine и Spring MVC зависимости; DI и транзакции рассматривать отдельно.

Перенос контроллеров не равен полному удалению Spring: текущие остаточные зависимости перечислены выше.
