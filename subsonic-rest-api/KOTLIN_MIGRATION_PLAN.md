# План миграции `subsonic-rest-api` на Kotlin без XJC

## Цель

Заменить генерацию JAXB-моделей из XSD через CXF XJC на явные вручную
поддерживаемые Kotlin-модели, не меняя внешний Subsonic REST-контракт.

После завершения должны сохраниться:

- пакет API `org.subsonic.restapi`;
- XML и JSON-представления Subsonic API;
- XML namespace `http://subsonic.org/restapi`;
- JSON root `subsonic-response`;
- совместимость существующего Java-кода в `airsonic-main`;
- поддержка XML, JSON и JSONP;
- явное использование EclipseLink MOXy для JAXB;
- сборка и тесты на Java 25.

Это **не** перенос `SubsonicRESTController` на Kotlin и не замена Spring MVC,
MOXy или JAXB. На первом этапе Kotlin используется только для API DTO-моделей
модуля `subsonic-rest-api`.

## Текущее состояние

Сейчас классы `org.subsonic.restapi` генерируются из
`src/main/resources/subsonic-rest-api.xsd` во время Maven-фазы
`generate-sources` посредством `org.apache.cxf:cxf-xjc-plugin`.

Контроллеры в `airsonic-main` опираются на XJC-поведение:

```java
response.setError(error);
songs.getSong().add(song);
child.setIsDir(true);
```

Поэтому Kotlin-модели являются wire DTO и JavaBean API, а не immutable domain
models.

## Обязательные правила Kotlin-моделей

### Общие правила

- Использовать обычные mutable `class`, а не `data class`.
- Сохранять public no-arg constructor.
- Использовать `var` для изменяемых scalar полей.
- Использовать `val ...: MutableList<T> = mutableListOf()` для XJC live-list
  коллекций.
- Помечать базовые классы с наследниками как `open`.
- Размещать JAXB-аннотации на backing field через `@field:`.
- Не использовать `@JvmField`: Java-коду нужны обычные JavaBean getters.
- Optional XML attributes должны быть nullable (`Int?`, `Long?`, `Boolean?`),
  иначе отсутствующие XML-атрибуты превратятся в `0` или `false`.
- Даты на первом этапе оставить как `XMLGregorianCalendar?`; не заменять на
  `Instant`, `OffsetDateTime` или `LocalDateTime`.

Пример простой модели:

```kotlin
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MusicFolder")
class MusicFolder {
    @field:XmlAttribute(required = true)
    var id: Int = 0

    @field:XmlAttribute
    var name: String? = null
}
```

### Live-list collections

XJC возвращает изменяемые списки, которые создаются по требованию. Этот
контракт необходим Java-контроллерам:

```java
songs.getSong().add(child);
```

Эквивалентный Kotlin-паттерн:

```kotlin
@XmlAccessorType(XmlAccessType.FIELD)
class Songs {
    @field:XmlElement(name = "song")
    val song: MutableList<Child> = mutableListOf()
}
```

Нельзя заменять такие поля на `List<T>`, immutable list или nullable list.

### Сохранение Java accessor API

Kotlin может генерировать другие JVM-имена accessor-методов. Особенно опасен
атрибут `isDir`: текущий XJC API содержит методы `isIsDir()` и `setIsDir(...)`.

Обычное Kotlin-свойство `var isDir: Boolean` не гарантирует такой ABI. Нужно
явно сохранить Java-имена:

```kotlin
@XmlAccessorType(XmlAccessType.FIELD)
open class Child {
    @field:XmlAttribute(name = "isDir", required = true)
    @get:JvmName("isIsDir")
    @set:JvmName("setIsDir")
    var directory: Boolean = false
}
```

### Enums

XML-значение enum не должно зависеть только от Kotlin-имени константы:

```kotlin
@XmlType(name = "ResponseStatus")
@XmlEnum
enum class ResponseStatus {
    @XmlEnumValue("ok")
    OK,

    @XmlEnumValue("failed")
    FAILED,
}
```

Если generated enum предоставлял public методы `value()` и `fromValue()`, их
нужно сохранить в JVM API, например с Kotlin methods и `@JvmStatic`.

## План работ

### Этап 0. Зафиксировать существующий wire contract

До изменения production-кода создать contract tests и golden fixtures для XML,
JSON и JSONP.

Минимально покрыть:

- `ping`;
- `getMusicFolders`;
- `getIndexes`;
- `getMusicDirectory`;
- `getArtists`, `getArtist`, `getAlbum`, `getSong`;
- `search`, `search2`, `search3`;
- playlists, bookmarks, shares и starred;
- `getPlayQueue`, `getNowPlaying`, `getScanStatus`;
- podcasts и jukebox;
- error response, в том числе authentication error с кодом `40`.

XML нужно сравнивать структурно, а не по форматированию. Проверять необходимо:

- root element `subsonic-response`;
- namespace;
- имена элементов и атрибутов;
- отсутствие optional атрибутов;
- порядок XML elements, заданный XSD;
- даты и timezone;
- пустые и непустые коллекции.

JSON нужно сравнивать как распарсенное дерево. Обязательно закрепить root:

```json
{"subsonic-response":{"status":"ok"}}
```

Полученный XML должен валидироваться против
`src/main/resources/subsonic-rest-api.xsd`.

**Критерий готовности:** тесты покрывают каждое семейство endpoint'ов, XML,
JSON и JSONP, а не только happy-path `ping`.

### Этап 1. Подключить Kotlin к Maven

В `subsonic-rest-api/pom.xml` добавить:

- Kotlin standard library;
- `kotlin-maven-plugin`;
- compilation `src/main/kotlin` и `src/test/kotlin`;
- JVM target 25;
- Kotlin compiler version, подтверждённо работающую с Java 25 и Jakarta JAXB 4.

На этом этапе не требуются Spring Kotlin plugin, JPA plugins, coroutines или
Jackson Kotlin module: в модуле нет Spring beans, JPA entities и web handlers.

Добавить небольшой Kotlin source и test только для проверки Maven lifecycle,
Surefire, Java 25 и Jakarta JAXB annotations. XJC остаётся включённым.

**Критерий готовности:** проходят:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl subsonic-rest-api test
JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl subsonic-rest-api,airsonic-main test
```

без изменения REST output.

### Этап 2. Зафиксировать legacy JVM ABI

Перед заменой generated classes сохранить snapshot публичного API текущего JAR:

- FQCN классов;
- public constructors;
- superclass и наследование;
- getter/setter методы;
- generic return types коллекций;
- enum constants и методы;
- `ObjectFactory`.

Добавить reflection-based ABI test. Он должен минимум гарантировать:

- no-arg constructor каждого публичного DTO;
- `getX()`/`setX(...)` API, используемый Java-контроллерами;
- live-list getters;
- `NowPlayingEntry extends Child` и `PodcastEpisode extends Child`;
- enum constants `MediaType.MUSIC`, `PODCAST`, `AUDIOBOOK`, `VIDEO`;
- compatibility `ObjectFactory`.

**Критерий готовности:** `airsonic-main` и `integration-test` компилируются
против Kotlin-моделей без массового переписывания Java-контроллеров.

### Этап 3. Реализовать общие Kotlin JAXB conventions

Сначала создать в staging package, например `org.subsonic.restapi.manual`,
небольшой репрезентативный набор моделей и serialization tests. Проверить:

- `@field:XmlAttribute` и `@field:XmlElement`;
- nullable optional scalars;
- mutable live lists;
- inheritance и `@XmlSeeAlso`;
- `XMLGregorianCalendar`;
- enums с `@XmlEnumValue`;
- mixed content у `Genre` и `Lyrics`;
- XML order через `@XmlType(propOrder = [...])`.

Это позволит отладить Kotlin/JAXB/MOXy семантику до конфликта FQCN с XJC.

### Этап 4. Root element и namespace

Сейчас root создаётся через `ObjectFactory` и `JAXBElement`. Рекомендуемая
целевая модель — добавить к Kotlin `Response`:

```kotlin
@XmlRootElement(
    name = "subsonic-response",
    namespace = "http://subsonic.org/restapi",
)
```

и после contract-проверки сериализовать `Response` напрямую.

Package-level `@XmlSchema` должен сохранить namespace и qualified elements.
Если Kotlin package annotation окажется нестабильной для JAXB, допустимо оставить
единственный технический Java-файл:

```text
src/main/java/org/subsonic/restapi/package-info.java
```

с `@XmlSchema`. Это package metadata, а не Java-реализация модуля; лучше один
такой файл, чем дублирование namespace у каждого элемента.

Сохранить Kotlin-версию `ObjectFactory` как deprecated compatibility adapter,
включая `createResponse()` и `createSubsonicResponse(Response)`, если ABI tests
подтверждают их необходимость.

### Этап 5. Реализовать модели группами

Нельзя одновременно держать XJC и Kotlin-класс с одним FQCN. Поэтому модели
удобно реализовывать и тестировать в staging package, а затем переключать одним
атомарным commit.

Порядок групп:

1. Инфраструктура: `Response`, `ResponseStatus`, `Error`, `ObjectFactory`,
   namespace metadata.
2. Общие модели: `MusicFolder`, `MusicFolders`, `Artist`, `ArtistID3`,
   `AlbumID3`, `Child`, `MediaType`, `Index`, `IndexID3`, `Indexes`,
   `Directory`.
3. Browse/search: `ArtistsID3`, `ArtistWithAlbumsID3`, `AlbumWithSongsID3`,
   `SearchResult*`, `Songs`, `Videos`, `VideoInfo`, `NowPlaying*`.
4. Playlists and queue: `Playlist*`, `Playlists`, `PlayQueue`,
   `JukeboxStatus`, `JukeboxPlaylist`.
5. Podcasts/social: podcast types, bookmarks, shares, starred, chat types.
6. Metadata/service: album/artist info, similar/top songs, lyrics, captions,
   audio/video conversion, scan status, license, user types.

Для каждого типа перенести XSD-информацию точно:

- class name;
- inheritance;
- field name;
- XML element/attribute name;
- XML type;
- `required` и optional semantics;
- list item element name;
- enum values;
- `propOrder`.

### Этап 6. Минимально изменить `JAXBWriter`

В `airsonic-main/src/main/java/org/airsonic/player/controller/JAXBWriter.java`
сохранить:

- явный `JAXBContextFactory` MOXy;
- XML и JSON marshaller settings;
- `MarshallerProperties.JSON_INCLUDE_ROOT`;
- JSONP behavior;
- `DatatypeFactory` и `XMLGregorianCalendar` conversion;
- чтение REST protocol version из XSD.

После появления `@XmlRootElement` у `Response`:

- заменить `new ObjectFactory().createResponse()` на `new Response()`;
- заменить marshalling `JAXBElement` на marshalling самого `Response`;
- прогнать все XML/JSON/JSONP fixtures.

Не менять JAXB provider, не вводить Jackson и не заменять JAXB на
`kotlinx.serialization` в рамках этой работы.

### Этап 7. Атомарно удалить XJC

Только после полной реализации и зелёных contract/ABI tests:

1. Переместить Kotlin-модели из staging package в `org.subsonic.restapi`.
2. Удалить `cxf-xjc-plugin` из `subsonic-rest-api/pom.xml`.
3. Убедиться, что `target/generated/src/main/java` больше не участвует в build.
4. Оставить XSD как публичную спецификацию и средство schema validation.

Обязательная проверка через `clean`, чтобы stale generated classes не скрыли
пропущенные Kotlin-классы:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl subsonic-rest-api clean test
JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl subsonic-rest-api,airsonic-main test
JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl subsonic-rest-api,airsonic-main package
```

### Этап 8. Runtime и внешний client smoke

На собранном WAR проверить каждый важный endpoint в XML и `f=json`, включая
ошибки авторизации, `Content-Type`, UTF-8, namespace, nullable fields,
`accentColor`, timestamps и list responses с 0/1/N элементами.

Проверить реальные Subsonic-клиенты, API scripts, JSONP-клиентов, deployment за
reverse proxy и старые клиенты, чувствительные к формату ответов.

## Основные риски и меры

| Приоритет | Риск | Предотвращение |
| --- | --- | --- |
| Критический | Изменение XML/JSON wire contract | Golden fixtures, structural XML comparison, JSON tree tests, XSD validation |
| Критический | Kotlin генерирует другой JavaBean API | ABI snapshot, reflection tests, `@JvmName` для нестандартных accessor'ов |
| Критический | Optional поля становятся `0`/`false` вместо отсутствующих | Nullable boxed Kotlin types |
| Высокий | Теряется XJC live-list semantics | `val items: MutableList<T> = mutableListOf()` |
| Высокий | Сломано наследование `Child` | `open class`, `@XmlSeeAlso`, tests |
| Высокий | Меняется root name или namespace | `@XmlRootElement`, package `@XmlSchema`, XML fixtures |
| Высокий | MOXy JSON иначе интерпретирует Kotlin annotations | Сохранять MOXy, тестировать XML/JSON параллельно |
| Средний | Меняется date/time serialization | Пока оставить `XMLGregorianCalendar` |
| Средний | Изменяется порядок XML elements | Полный `@XmlType(propOrder = [...])` по XSD |
| Средний | Внешний код использует `ObjectFactory` | Оставить compatibility adapter |
| Средний | Старые XJC classes в `target` маскируют ошибку | Всегда проверять через `mvn clean` |
| Низкий | Java Checkstyle не форматирует Kotlin | Подключить отдельный Kotlin formatter/linter |

## Что не входит в эту миграцию

- перенос REST-контроллеров на Kotlin;
- замена Spring MVC или переход на Ktor;
- замена MOXy на Jackson;
- замена JAXB на `kotlinx.serialization`;
- удаление XSD;
- изменение API version;
- рефакторинг DTO в immutable domain-модели;
- изменение nullable/empty-field поведения.

Каждый из этих пунктов может быть самостоятельной задачей после стабилизации
Kotlin DTO-модуля, но их нельзя объединять с удалением XJC без резкого роста
риска регрессий.

## Целевая структура

```text
subsonic-rest-api/
├── src/main/kotlin/org/subsonic/restapi/
│   ├── Response.kt
│   ├── Child.kt
│   ├── Artist.kt
│   ├── Playlist.kt
│   ├── ObjectFactory.kt
│   └── ...
├── src/main/resources/
│   └── subsonic-rest-api.xsd
└── src/test/
    ├── kotlin/                 # JAXB, ABI и contract tests
    └── resources/
        ├── rest-contract/      # XML/JSON/JSONP golden fixtures
        └── legacy-jaxb-api.txt
```

Итог: `subsonic-rest-api` становится Kotlin-first и читаемым модулем без
CXF/XJC в production build, но сохраняет существующий Java API и Subsonic wire
compatibility.
