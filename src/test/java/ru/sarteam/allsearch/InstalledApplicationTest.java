package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InstalledApplicationTest {
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
