package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchEngineTest {

    @TempDir
    Path tempDir;

    private static SearchEngine.Options options(String query, boolean regex, boolean caseSensitive,
                                                List<String> masks, boolean textOnly) {
        return new SearchEngine.Options(query, regex, caseSensitive, masks, textOnly, 0);
    }

    @Test
    void findsMatchWithLineNumberAndText() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"),
                "первая строка\nвторая строка с ИСКОМЫМ\nтретья\n", StandardCharsets.UTF_8);
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("искомым", false, false, List.of(), true), null);
        assertEquals(1, res.size());
        SearchEngine.Result r = res.get(0);
        assertEquals(2, r.line());
        assertEquals("вторая строка с ИСКОМЫМ", r.text());
    }

    @Test
    void respectsCaseSensitivity() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "искомый\nИСКОМЫЙ\n", StandardCharsets.UTF_8);
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("искомый", false, true, List.of(), true), null);
        assertEquals(1, res.size());
        assertEquals(1, res.get(0).line());
    }

    @Test
    void supportsRegex() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "номер 42\nномер три\n", StandardCharsets.UTF_8);
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("номер \\d+", true, false, List.of(), true), null);
        assertEquals(1, res.size());
        assertEquals("номер 42", res.get(0).text());
    }

    @Test
    void filtersByMask() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "искомый\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("b.md"), "искомый\n", StandardCharsets.UTF_8);
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("искомый", false, false, List.of("*.txt"), true), null);
        assertEquals(1, res.size());
        assertTrue(res.get(0).file().getFileName().toString().endsWith(".txt"));
    }

    @Test
    void skipsBinaryInTextMode() throws IOException {
        byte[] bytes = new byte[]{'a', 0, 'b', 0, 'c'};
        Files.write(tempDir.resolve("bin.dat"), bytes);
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("a", false, false, List.of(), true), null);
        assertTrue(res.isEmpty());
    }

    @Test
    void readsWindows1251() throws IOException {
        Charset cp1251 = Charset.forName("windows-1251");
        Files.write(tempDir.resolve("ru.txt"), "привет искомый\n".getBytes(cp1251));
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("искомый", false, false, List.of(), true), null);
        assertEquals(1, res.size());
    }

    @Test
    void readsUtf16WithBom() throws IOException {
        Files.write(tempDir.resolve("u16.txt"), "привет искомый\n".getBytes(StandardCharsets.UTF_16));
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("искомый", false, false, List.of(), true), null);
        assertEquals(1, res.size());
    }

    @Test
    void searchesRecursively() throws IOException {
        Files.createDirectories(tempDir.resolve("sub/deep"));
        Files.writeString(tempDir.resolve("sub/deep/x.txt"), "найдено\n", StandardCharsets.UTF_8);
        List<SearchEngine.Result> res = new SearchEngine().search(
                tempDir, options("найдено", false, false, List.of(), true), null);
        assertEquals(1, res.size());
        assertTrue(res.get(0).file().endsWith(Path.of("sub", "deep", "x.txt")));
    }

    @Test
    void capsResults() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("искомый\n");
        }
        Files.writeString(tempDir.resolve("big.txt"), sb.toString(), StandardCharsets.UTF_8);
        SearchEngine.Options opts = new SearchEngine.Options("искомый", false, false, List.of(), true, 10);
        List<SearchEngine.Result> res = new SearchEngine().search(tempDir, opts, null);
        assertEquals(10, res.size());
    }
}
