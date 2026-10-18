package ru.sarteam.allsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PreviewReadTest {
    @TempDir Path directory;
    @Test void limitsPreviewRead() throws Exception {
        Path file = directory.resolve("large.txt");
        Files.writeString(file, "a".repeat(10_000));
        String text = TextFileReader.readPreview(file, 100);
        assertTrue(text.startsWith("a".repeat(100)));
        assertTrue(text.contains("обрезан"));
        assertTrue(text.length() < 200);
        Files.writeString(file, "small");
        assertEquals("small", TextFileReader.readPreview(file, 100));
        Files.writeString(file, "я".repeat(100));
        assertTrue(TextFileReader.readPreview(file, 11).startsWith("я".repeat(5) + "\n"));
    }
}
