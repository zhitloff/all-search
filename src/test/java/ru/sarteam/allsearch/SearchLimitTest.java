package ru.sarteam.allsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchLimitTest {
    @TempDir Path directory;

    @Test void stopsAcrossFilesAndSubdirectories() throws Exception {
        Files.createDirectory(directory.resolve("sub"));
        for (String name : List.of("a.txt", "b.txt", "sub/c.txt")) {
            Files.writeString(directory.resolve(name), "hit\nhit\n");
        }
        List<String> visited = new ArrayList<>();
        var results = new SearchEngine().search(directory,
                new SearchEngine.Options("hit", false, true, List.of(), true, 1), visited::add);
        assertEquals(1, results.size());
        assertEquals(1, visited.size());
    }
}
