# all-search

Десктоп-приложение на JavaFX для полнотекстового поиска по файлам.

## Требования

- JDK 25
- `JAVA_HOME` должен указывать на JDK 25

## Запуск приложения

```bash
./mvnw clean javafx:run
```

## Тесты

```bash
./mvnw clean test
```

Запуск одного тест-класса:

```bash
./mvnw -Dtest=SearchEngineTest test
```

## Сборка DMG (macOS)

В `pom.xml` добавлен профиль `mac-dmg`.

Сборка установочного `.dmg`:

```bash
./mvnw clean package -Pmac-dmg
```

Результат:

- `target/installer/*.dmg`
