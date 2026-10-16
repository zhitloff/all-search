# AGENTS.md

## Язык и контекст
- В этом репозитории отвечай пользователю **только по-русски**.
- Это JavaFX desktop-приложение (JPMS) для полнотекстового поиска; ядро — `SearchEngine`, UI — `MainController` + `main-view.fxml`.

## Проверенные команды (Maven Wrapper)
- Обязательное условие: `JAVA_HOME` должен указывать на **JDK 25** (`pom.xml` компилирует с `source/target=25`).
- Перед проверками всегда делай очистку: `./mvnw clean test`.
  - Без `clean` можно поймать `Unsupported major.minor version 69.0`, если в `target/` остались классы от другого JDK.
- Запуск UI: `./mvnw clean javafx:run`.
- Запуск одного тест-класса: `./mvnw -Dtest=SearchEngineTest test`.

## Критичные ограничения по структуре
- Не удаляй `opens ru.sarteam.allsearch to javafx.fxml` в `src/main/java/module-info.java` — иначе ломается FXML-инъекция.
- При добавлении JavaFX/JPMS-библиотеки обновляй **и** `pom.xml`, **и** `module-info.java` (`requires ...`).
- FXML и связанные ресурсы размещаются в `src/main/resources/ru/sarteam/allsearch/` и грузятся относительно `MainApplication.class`.
- Runtime-точка входа в Maven-плагине: `ru.sarteam.allsearch.MainApplication` (а `Launcher` — вспомогательный класс для IDE).

## Что важно знать про поиск
- `SearchEngine` рекурсивно обходит директории, не заходит в симлинки, ограничивает большие файлы (`MAX_FILE_BYTES = 256 MiB`), умеет отмену через `cancel()`.
- Декодирование: UTF-8 (в т.ч. BOM), UTF-16LE/BE (BOM), fallback — `windows-1251`.
- Опция `textOnly` отсекает бинарные файлы по NUL-байтам; лимит результатов по умолчанию — `100_000`.
