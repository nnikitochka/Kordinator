# Исходная база перед удалением Spring

Дата: 16 ноября 2026 года. Production-ревизия: `71b47a3167f96beb7f153cbe8642b16bea62723a`. В этом этапе изменяются только тесты, инструменты проверки и документация. Production-код, зависимости и схема БД не меняются.

Это исходная точка для [плана удаления Spring](../../../SPRING_REMOVAL_PLAN.md). Локальные проверки не подтверждают совместимость всех внешних БД и способов deployment. Полный этап 0 остаётся открытым в части явно перечисленных ниже проверок.

## Что сохранено

- [INVENTORY.md](INVENTORY.md) — статические привязки: DI, security, JDBC/транзакции, кеш, lifecycle, настройки, JSP и упаковка.
- [spring-inventory.json](spring-inventory.json) — пути/номера строк из фиксированной Git-ревизии; Markdown, локальные файлы и `target` исключены. 128 production Java/Kotlin-файлов содержат упоминания Spring; конфигурационные aliases учитываются отдельно.
- [dependencies-all.txt](dependencies-all.txt) — дерево зависимостей всех пяти модулей профиля `integration-test`, без запуска Docker.
- [dependencies-runtime.txt](dependencies-runtime.txt), [dependencies-test.txt](dependencies-test.txt) — графы `airsonic-main` по scope. Maven runtime scope не включает `provided`; состав исполняемого WAR проверяется дополнительно.
- [resolved-build.json](resolved-build.json) — разрешённые версии прямых зависимостей/плагинов и выбранные свойства из effective POM `airsonic-main`. Это снимок для будущего отказа от Boot parent, а не полный перечень inherited plugin configuration.

Деревья зависимостей отражают разрешение Maven в текущем checkout; source inventory читает именно архив указанной ревизии. Не подменять эти снимки результатами следующих этапов без указания новой ревизии.

## Окружение и воспроизведение

Фактически использованы Arch Linux amd64, OpenJDK `25.0.5.0.2`, Maven `3.9.16`, Node `24.13.0`, Python 3. Maven доступен по пути `/home/nnikitochka/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven-plugin/lib/maven3/bin/mvn`; в командах ниже он обозначен обычным `mvn`.

```bash
# Не затрагивает рабочее дерево; output можно сравнить с сохранённым JSON.
python3 docs/migration/spring-baseline/inventory.py --revision 71b47a3 --output /tmp/spring-inventory-check.json

# Metadata goals: не запускают сборку Docker/интеграционные контейнеры.
mvn -o -P integration-test -DskipTests dependency:tree
mvn -o -pl airsonic-main -DskipTests dependency:tree -Dscope=runtime
mvn -o -pl airsonic-main -DskipTests dependency:tree -Dscope=test
mvn -o -pl airsonic-main help:effective-pom -Doutput=/tmp/airsonic-effective-pom.xml

# Полный test reactor по умолчанию: subsonic-rest-api и airsonic-main.
XDG_DATA_HOME=/tmp mvn -o -fae -Dkotlin.compiler.daemon=false -Dcheckstyle.skip=true clean test
node --test airsonic-main/src/test/javascript/*.test.cjs
```

`-o` использует заранее заполненный Maven cache; для нового окружения сначала нужно разрешить зависимости. `HomeRule` создаёт отдельный временный `airsonic.home`; fixture cleanup сохраняется. HTTP-тестам нужны localhost-порты. В текущем sandbox для этого использовался разрешённый запуск вне сетевых ограничений. Не задавать в этих командах URL пользовательской БД или пользовательский `airsonic.home`.

## Проверки и точные границы доказательств

Статический inventory baseline создан из `71b47a3`; dependency trees всех пяти модулей и effective POM сняты offline. Первый полный `clean test` reactor baseline завершился **BUILD FAILURE**: 280 тестов (4 в `subsonic-rest-api`, 276 в `airsonic-main`), 5 failures, 1 error, 0 skipped. Из шести зафиксированных проблем:

| Тесты | Наблюдение | Следующее действие |
|---|---|---|
| `MediaScannerServiceUnitTest.neverScanned` | Старый Mockito `@InjectMocks` не передавал новый обязательный `scannerParallelism` параметр конструктора. Исправлена только тестовая fixture: явный constructor и mock-зависимости. | Отдельный повторный unit run прошёл: вместе с `CredentialCompatibilityBaselineTest` всего 3/3. |
| `CORSTest.corsHeadersShouldBeAddedToSuccessResponses` | Старый тест использовал несуществующий и не поддерживаемый маршрут вместо проверки CORS. | Переписан на настоящий HTTP к `/rest/ping`, сохранив проверки success/error/preflight и добавив CORS header assertion. Повторный HTTP run: 3/3 прошли. |
| `AirsonicRestApiIntTest.getMusicDirectoryExposesAccentColor` | MockMvc отправлял запрос в Ktor servlet и получал 404 вместо проверки каталога. | Переведён на embedded localhost HTTP; все прежние проверки accent color сохранены. Повторный HTTP run прошёл. |
| `TopSongsIntegrationTest.preservesTopSongsContract`, `SimilarSongsIntegrationTest.preservesBothClientContracts` | Ответы Subsonic REST корректны, но тесты требовали `Set-Cookie`, который не является частью контракта этих stateless запросов. | Два требования cookie удалены; покрытие ответа и кода ошибки сохранено. Проверка отсутствия cookie на ошибочном запросе оставлена. Повторный запуск обоих тестов прошёл: 2/2. |

Повторный запуск изменённых fixtures и HTTP-тестов: **8 тестов всего, 7 прошли и 1 упал**. Прошли `MediaScannerServiceUnitTest` (1), `CredentialCompatibilityBaselineTest` (2), `CORSTest` (3) и directory assertion из `AirsonicRestApiIntTest` (1). Единственное падение было вызвано устаревшим тестом на удалённый endpoint; этот тест удалён после прогона, остальные семь проверок прошли. После этого удалены два assertion-а на наличие cookie в REST-ответах; оба затронутых integration-теста повторно прошли (2/2). Полный reactor итог исходного запуска и его test XML остаются исторической записью.

После удаления устаревшего endpoint-теста и cookie assertions повторно выполнен полный чистый reactor:

```bash
XDG_DATA_HOME=/tmp mvn -o -fae -Dkotlin.compiler.daemon=false -Dcheckstyle.skip=true clean test
```

Результат **BUILD SUCCESS**: 279 тестов (4 в `subsonic-rest-api`, 275 в `airsonic-main`), 0 failures, 0 errors, 0 skipped. HSQLDB DAO, startup/migration, REST и servlet integration suites прошли. PMD включён и прошёл. Checkstyle намеренно отключён: отдельный запуск падает на существующем `PlayQueue.java:430:20`; полная проверка без обходов не объявляется успешной. Отдельно прошли все **29/29** JS файлов. Сборка исполняемого WAR (`mvn ... -DskipTests package`) прошла, размер 110 MB, SHA-256 `3d310a51a802335e25de0196f5d5d88ccf36677deaa3ce2c46653e7ff63fba5c`. Исходные XML результаты первого запуска сохранены в [test-results-initial.json](test-results-initial.json); они остаются историческими и отражают прежние тестовые ожидания.

### Credentials

`CredentialCompatibilityBaselineTest` содержит фиксированные значения восьми форматов: bcrypt, PBKDF2, scrypt, SHA-256, noop, hex, legacynoop, legacyhex; отдельный пример AES-GCM использует только тестовые ключ/соль. Они однократно получены текущими реализациями и больше не генерируются при тесте. Проверяются правильный/неправильный пароль, decode доступных форматов, расшифровка сохранённого AES ciphertext и отказ при его повреждении.

Это образцы для будущих новых codec-реализаций. Пока тест обращается к существующему Spring registry; при переносе менять проверяемый adapter, сохраняя сами fixtures и assertions. Тест не доказывает совместимость каждой исторической комбинации/повреждённого формата и сетевой LDAP-аутентификации.

### Транзакции, кеш, задачи и старые данные

`UserDaoTestCase.testCreateUserTransactionalError` проверяет отсутствие частично созданного пользователя после ошибки; его private `REQUIRES_NEW`-метод также является важным примером текущего AspectJ weaving. DAO-тесты работают на HSQLDB. Это не проверка каждого isolation/propagation/commit-failure сценария на всех SQL engines.

`CacheStatisticsIntegrationTest` проверяет счётчики кеша и адресную очистку через HTTP с авторизацией/CSRF. `ScheduledTaskSnapshotTest` проверяет диагностические снимки расписаний без выполнения job; `ScheduledTasksIntegrationTest` — HTTP-контракт. Эти проверки не доказывают TTL всех регионов, порядок invalidation относительно commit, отсутствие гонок и корректную остановку каждого scheduler.

`LegacyDatabaseStartupTestCase` копирует существующую fixture `db/pre-liquibase/db` во временный home и поднимает контекст с миграциями. Его assertion проверяет создание DataSource; это полезный старый startup-сценарий, но не полная сверка всех мигрированных данных или всех исторических версий HSQLDB.

### Исполняемый WAR

[packaged_smoke.py](packaged_smoke.py) запускает настоящий WAR отдельным JVM-процессом на `127.0.0.1`, port 0 и context `/airsonic`. Используются отдельный временный home и файловая HSQLDB с явно заданным JDBC URL. Переменные окружения приложения и JVM options извне не наследуются. Проверяются XML/JSON ping, точные байты статического JS, redirect анонимного пользователя на login (с `Accept: text/html`), форма входа, authenticated 404, SIGTERM/освобождение порта и повторный запуск той же БД. Smoke прошёл оба запуска. Первый цикл подтвердил XML/JSON `ping` (200), статический JS (200 и совпадение байтов), `/index` (302 на `/airsonic/login`), `/login` (200), неизвестный authenticated resource (404); SIGTERM завершил процесс с кодом 143, порт освободился. Второй запуск на той же временной БД подтвердил XML `ping` (200), затем также завершился с освобождением порта. SHA-256 WAR `3d310a51a802335e25de0196f5d5d88ccf36677deaa3ce2c46653e7ff63fba5c`; в JAR metadata не было Git revision, поэтому отчет привязывает бинарник его digest и отдельно записывает checkout HEAD `e2d8312e5a6449adba8e20c1c4ddd0403516a2b6`.

```bash
# После успешно проверенных тестов; package без их повторения.
XDG_DATA_HOME=/tmp mvn -o -DskipTests -Dkotlin.compiler.daemon=false -Dcheckstyle.skip=true package
python3 docs/migration/spring-baseline/packaged_smoke.py \
  --war airsonic-main/target/airsonic.war \
  --output /tmp/packaged-smoke.json --log-dir /tmp/airsonic-packaged-smoke-logs
```

Выход JVM после SIGTERM не подтверждает по отдельности закрытие каждого executor/watcher/транскодера. У `TaskSchedulingService` статически не найден путь закрытия самостоятельно созданного daemon scheduler. Исправлять это следует в этапе lifecycle, сохраняя различие между исходным наблюдением и новой гарантией.

## Открытые проверки и ограничения

| Область | Что ещё нужно до соответствующего переноса |
|---|---|
| PostgreSQL, MySQL, MariaDB | Повторить DAO/миграционные и транзакционные сценарии на отдельных fixtures. В окружении нет Docker/Podman, socket Docker и CLI этих БД; матрица не запускалась. |
| Внешний servlet-контейнер и Docker image | Отдельные deploy/redeploy/undeploy и запуск image. Исполняемый WAR на встроенном Tomcat не заменяет эти проверки. |
| JDK/CI | POM требует Java 25, старая CI-матрица содержит 11/16/17, Docker декларирует Java 17. Последние варианты не подтверждены этим прогоном. |
| Checkstyle | Отдельный запуск воспроизвёл сбой parser на `domain/PlayQueue.java:430:20` (`unexpected token: switch`). В Maven test/package он явно отключён. PMD остаётся включённым; полная сборка без обходов не объявляется успешной. |
| LDAP/remember-me/config | Реальный LDAP, все старые cookies/credentials, полный приоритет config aliases и конкурирующих источников требуют дополнительных fixtures до переноса этих подсистем. |
| Транзакции/кеш/жизненный цикл | Дополнить характеристики self-invocation, commit-failure, конкурентных запросов, cache invalidation и cleanup при частичной ошибке запуска. |
| HTTP и UI | Существующие HTTP/JS-тесты сохраняются; Firefox и ручной браузерный прогон не выполнялись. Набор не объявляется доказательством работы каждого UI-сценария. |

Перед этапами 3/4/5/6/9 обязательны недостающие характеристики соответствующего поведения. Публичные контракты нельзя менять по результату одного успешного smoke-теста.
