# AGENTS.md

Десктоп-приложение на JavaFX 21 (Java 25, JPMS). Пока это дефолтный шаблон JavaFX-архетипа (`HelloApplication` / `HelloController` / `hello-view.fxml`) — продукт «all-search» ещё не написан.

## Сборка и запуск

- Требуется JDK 25: `pom.xml` компилирует с `<source>/<target>=25`, IntelliJ настроен на temurin-25. JavaFX 21.0.6 — не «обновлять» под версию JDK.
- Запуск приложения — **только** `./mvnw clean javafx:run` (плагин `org.openjfx:javafx-maven-plugin:0.0.8`, Maven wrapper 3.8.5). Shade/jar-плагинов нет, поэтому `java -jar` не работает.
- `./mvnw` требует, чтобы `JAVA_HOME` указывал на JDK 25.
- JUnit 5.12.1 подключён (scope `test`), но каталога `src/test` пока нет — тестов в репозитории не существует.

## Структура и подводные камни

- Модуль JPMS `ru.sarteam.allsearch` (`module-info.java`). Не убирать `opens ru.sarteam.allsearch to javafx.fxml` — FXML-инъекция полей в контроллер работает через рефлексию.
- FXML-файлы лежат в `src/main/resources/ru/sarteam/allsearch/` (путь повторяет пакет) и загружаются через `getResource("hello-view.fxml")` относительно класса — новые view класть по этому же пути.
- `Launcher.java` — не-модульная точка входа (`Application.launch(...)`), но в `pom.xml` mainClass плагина указывает на `HelloApplication`, а не на `Launcher`.
- Новые внешние библиотеки JavaFX должны попадать и в `pom.xml`, и в `requires` модуля `module-info.java` (как controlsfx 11.2.1 и ikonli-javafx 12.3.1).
- Кодировка проекта: UTF-8 (задана в `pom.xml`).
