package ru.sarteam.allsearch;

import java.nio.file.Path;

/** Сохраняет выбранный шрифт между запусками приложения. */
final class FontSettingsStore {
    private final Path file;

    FontSettingsStore(Path file) {
        this.file = file;
    }

    Settings load() {
        Settings settings = new HistoryStore(file).loadFontSettings();
        return settings == null ? new Settings("monospace", 14) : settings;
    }

    void save(String family, int size) {
        new HistoryStore(file).saveFontSettings(new Settings(family, size));
    }

    record Settings(String family, int size) {
        Settings {
            family = family == null || family.isBlank() ? "monospace" : family;
            size = Math.max(8, Math.min(72, size));
        }
    }
}
