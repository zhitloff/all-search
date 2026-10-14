# AGENTS.md

Десктоп-приложение на JavaFX 21 (Java 25, JPMS) — аналог AVSearch: полнотекстовый поиск по файлам (папка, текст/рег. выражение, маски, регистр). Ядро поиска — `SearchEngine`, UI — `MainController` + `main-view.fxml`.

## Сборка и запуск

- Требуется JDK 25: `pom.xml` компилирует с `<source>/<target>=25`, IntelliJ настроен на temurin-25. JavaFX 21.0.6 — не «обновлять» под версию JDK.
- Запуск приложения — **только** `./mvnw clean javafx:run` (плагин `org.openjfx:javafx-maven-plugin:0.0.8`, Maven wrapper 3.8.5). Shade/jar-плагинов нет, поэтому `java -jar` не работает.
- Тесты: `./mvnw test` (JUnit 5.12.1 + surefire 3.5.2). Surefire пришлось закрепить явно: дефолтный surefire в Maven 3.8.5 не умеет JUnit 5.
- `./mvnw` требует, чтобы `JAVA_HOME` указывал на JDK 25.

## Структура и подводные камни

- Модуль JPMS `ru.sarteam.allsearch` (`module-info.java`). Не убирать `opens ru.sarteam.allsearch to javafx.fxml` — FXML-инъекция полей в контроллер работает через рефлексию. `java.desktop` требуется для открытия файлов через `java.awt.Desktop`.
- FXML-файлы лежат в `src/main/resources/ru/sarteam/allsearch/` (путь повторяет пакет) и загружаются через `getResource("main-view.fxml")` относительно класса — новые view класть по этому же пути.
- `Launcher.java` — не-модульная точка входа (`Application.launch(...)`), но в `pom.xml` mainClass плагина указывает на `MainApplication`, а не на `Launcher`.
- `SearchEngine` — чистый Java (без JavaFX), рекурсивный обход, кодировки UTF-8 / UTF-16 (BOM) / windows-1251 (fallback), пропуск бинарных файлов по NUL-байтам, лимит `maxResults` (по умолчанию 100 000), кооперативная отмена через `cancel()`. UI-контроллер проверяет рег. выражение до запуска `Task`.
- Новые внешние библиотеки JavaFX должны попадать и в `pom.xml`, и в `requires` модуля `module-info.java` (как controlsfx 11.2.1 и ikonli-javafx 12.3.1).
- Кодировка проекта: UTF-8 (задана в `pom.xml`).
