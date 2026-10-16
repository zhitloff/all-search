package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FontSettingsStoreTest {
    @TempDir
    Path directory;

    @Test
    void handlesMissingAndCorruptedSettings() throws Exception {
        Path file = directory.resolve("mask-history.json");
        FontSettingsStore store = new FontSettingsStore(file);
        assertEquals(new FontSettingsStore.Settings("monospace", 14), store.load());
        Files.writeString(file, "family=Example\nsize=invalid");
        assertEquals(new FontSettingsStore.Settings("monospace", 14), store.load());
    }

    @Test
    void preservesSpecialCharactersAndClampsSize() {
        Path file = directory.resolve("nested/mask-history.json");
        new FontSettingsStore(file).save("Шрифт = Test\\Name", 100);
        assertEquals(new FontSettingsStore.Settings("Шрифт = Test\\Name", 72),
                new FontSettingsStore(file).load());
    }

    @Test
    void preservesHistoryAndFontWhenSavingEither() {
        Path file = directory.resolve("mask-history.json");
        HistoryStore history = new HistoryStore(file);
        FontSettingsStore fonts = new FontSettingsStore(file);
        history.save(List.of("/example"), List.of("*.java"));
        fonts.save("Example Font", 23);
        assertEquals(new HistoryStore.History(List.of("/example"), List.of("*.java")), history.load());
        history.save(List.of("/other"), List.of());
        assertEquals(new FontSettingsStore.Settings("Example Font", 23), fonts.load());
        assertEquals(List.of(), history.load().masks());
    }

    @Test
    void usesDefaultsWhenJsonHasNoFontSettings() throws Exception {
        Path file = directory.resolve("mask-history.json");
        Files.writeString(file, "[\"*.txt\"]");
        FontSettingsStore fonts = new FontSettingsStore(file);
        assertEquals(new FontSettingsStore.Settings("monospace", 14), fonts.load());
        assertEquals(List.of("*.txt"), new HistoryStore(file).load().masks());
        fonts.save("Other", 20);
        assertEquals(new FontSettingsStore.Settings("Other", 20), fonts.load());
    }
}
