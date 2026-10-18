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
}
