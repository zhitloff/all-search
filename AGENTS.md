# AGENTS.md

## Язык
- В этом репозитории отвечай пользователю **только по-русски**.

## Контекст
- JavaFX desktop-приложение (JPMS), полнотекстовый поиск по файлам.
- Точки входа: `ru.sarteam.allsearch.MainApplication` (runtime для `javafx-maven-plugin`), `ru.sarteam.allsearch.Launcher` (для запуска из IDE вне модуля/плагина).
- Ключевые классы: `SearchEngine`, `TextFileReader`, `MainController`, `FileContentView`, `SearchTask`, `SearchPattern`.

## Требования и команды (Maven Wrapper)
- **Обязательно**: `JAVA_HOME` на **JDK 25** (`pom.xml`: `<java.version>25</java.version>`, компиляция с `release=25`).
- Перед проверками всегда `./mvnw clean ...` — в `target/` могут оставаться классы с другим major/minor (иначе `Unsupported major.minor version`).
- Запуск UI: `./mvnw clean javafx:run` (плагин `javafx-maven-plugin` использует модуль `ru.sarteam.allsearch/ru.sarteam.allsearch.MainApplication`).
- Тесты: `./mvnw clean test`.
- Один тест-класс: `./mvnw -Dtest=SearchEngineTest test`.
- Упаковка (нативный дистрибутив): `./mvnw clean package` (профили выбираются по ОС: `mac`/`linux` авто, Windows — `-Pwindows` или `-Pwindows-portable`). См. `README.md`.

## Критичные ограничения по структуре (JPMS/JavaFX)
- Не удаляй `opens ru.sarteam.allsearch to javafx.fxml` в `src/main/java/module-info.java` — иначе ломается FXML-инъекция. Также `opens ... to org.junit.platform.commons` нужно для запуска тестов по module path.
- При добавлении JavaFX/JPMS-зависимостей обновляй **и** `pom.xml`, **и** `module-info.java` (`requires ...`).
- FXML/CSS/иконки лежат в `src/main/resources/ru/sarteam/allsearch/` и грузятся относительно `MainApplication.class` (например `MainApplication.class.getResource("main-view.fxml")`).
- В `pom.xml`: `maven-shade-plugin` собирает fat-jar в `target/input/${project.build.finalName}.jar`, **исключая `org.openjfx:*`** (JavaFX копируется отдельно в `target/javafx-modules/` через `maven-dependency-plugin` для `jpackage`). Точка входа для jpackage — `${main.class}` (`ru.sarteam.allsearch.Launcher`).
- `jpackage` собирает пакеты только на целевой ОС (см. README).

## Поиск (важное)
- `SearchEngine`: рекурсивный обход, **не** заходит в симлинки, пропускает недоступные директории, ограничивает файлы по размеру `MAX_FILE_BYTES = 256 MiB`, поддерживает отмену `cancel()`, ограничение результатов по умолчанию `DEFAULT_MAX_RESULTS = 100_000` (можно переопределить в `Options`).
- Фильтрация по маскам через `glob:` (только по имени файла). `textOnly` в режиме "только текст" пропускает бинарные файлы, если нет UTF-16 BOM (проверка по NUL в первых 8192 байтах).
- Декодирование `TextFileReader`: UTF-8 с/без BOM, UTF-16 LE/BE с BOM, иначе fallback на `windows-1251`. `readPreview` аккуратно обрезает границы многобайтовых символов.
