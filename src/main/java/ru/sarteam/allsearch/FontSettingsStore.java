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
        return settings == null
                ? new Settings(FontConstants.DEFAULT_FONT_FAMILY, FontConstants.DEFAULT_FONT_SIZE)
                : settings;
    }

    void save(String family, int size) {
        new HistoryStore(file).saveFontSettings(new Settings(family, size));
    }

    record Settings(String family, int size) {
        Settings {
            family = family == null || family.isBlank() ? FontConstants.DEFAULT_FONT_FAMILY : family;
            size = Math.max(8, Math.min(72, size));
        }
    }
}
