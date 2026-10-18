package ru.sarteam.allsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HistoryStoreTest {
    @TempDir Path directory;
    @Test void preservesBracketsQuotesAndBackslashes() {
        var store = new HistoryStore(directory.resolve("history.json"));
        var paths = List.of("/data/report]2026", "C:\\[work]\\\"draft\"", "/data/open[");
        var masks = List.of("[ab]*.txt", "*.java");
        store.save(paths, masks);
        assertEquals(paths, store.load().directories());
        assertEquals(masks, store.load().masks());
        store.saveFontSettings(new FontSettingsStore.Settings("Verdana", 12));
        assertEquals(paths, store.load().directories());
        assertEquals(masks, store.load().masks());
    }
}
