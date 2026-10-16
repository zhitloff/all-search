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

## Сборка portable EXE (Windows)

В `pom.xml` добавлен профиль `windows-portable`. Он использует `jpackage` и
создаёт portable application image без установки и без записи в реестр.

Сборку необходимо выполнять на Windows с JDK 25 и установленным WiX не требуется
для этого типа упаковки:

```bat
mvnw.cmd clean package -Pwindows-portable
```

Результат:

- `target/installer/all-search/all-search.exe`

Для запуска portable-версии нужно переносить всю папку
`target/installer/all-search`, а не только файл `.exe` — рядом с launcher находятся
runtime Java и библиотеки приложения.
