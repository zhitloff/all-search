package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InstalledApplicationTest {
    @org.junit.jupiter.api.Test
    void linuxLaunchUsesDesktopEntryHandler() {
        var entry = new InstalledApplication.Entry("Editor", java.nio.file.Path.of("editor with spaces.desktop"));
        var file = java.nio.file.Path.of("file with spaces.txt");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("gio", "launch",
                entry.path().toAbsolutePath().toString(), file.toAbsolutePath().toString()),
                InstalledApplication.linuxLaunchCommand(entry, file));
    }
    private static final InstalledApplication.Entry CODE =
            new InstalledApplication.Entry("Visual Studio Code", Path.of("/Applications/Visual Studio Code.app"));

    // Путь в стиле Windows, чтобы проверить поиск по пути на обеих платформах.
    private static final InstalledApplication.Entry WINDOWS =
            new InstalledApplication.Entry("Visual Studio Code",
                    Path.of("C:\\Program Files\\Visual Studio Code\\Code.exe"));

    @Test
    void findsApplicationByWordInitials() {
        assertTrue(InstalledApplication.matches(CODE, "VSC"));
        assertTrue(InstalledApplication.matches(CODE, "vsc"));
        assertTrue(InstalledApplication.matches(CODE, "Visual"));
    }

    @Test
    void keepsPathSearchAndRejectsUnrelatedQuery() {
        assertTrue(InstalledApplication.matches(CODE, "Applications/Visual"));
        assertFalse(InstalledApplication.matches(CODE, "VST"));
    }

    @Test
    void matchesPathRegardlessOfSeparatorStyle() {
        // Запрос с любым разделителем совпадает и с Unix-, и с Windows-путём.
        assertTrue(InstalledApplication.matches(CODE, "Applications/Visual"));
        assertTrue(InstalledApplication.matches(CODE, "Applications\\Visual"));
        assertTrue(InstalledApplication.matches(WINDOWS, "Program Files/Visual"));
        assertTrue(InstalledApplication.matches(WINDOWS, "Program Files\\Visual"));
    }
}
