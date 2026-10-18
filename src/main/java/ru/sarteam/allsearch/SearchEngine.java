package ru.sarteam.allsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Движок полнотекстового поиска по файлам — аналог AVSearch.
 *
 * <p>Рекурсивно обходит директорию, читает текстовые файлы (UTF-8, UTF-16 с BOM,
 * windows-1251 в качестве fallback), ищет совпадения по строкам и возвращает
 * файл, номер строки (с 1) и текст строки.
 */
public final class SearchEngine {

    public static final int DEFAULT_MAX_RESULTS = 100_000;

    private static final long MAX_FILE_BYTES = 256L * 1024 * 1024;

    /** Параметры одного поиска. */
    public record Options(
            String query,
            boolean regex,
            boolean caseSensitive,
            List<String> masks,
            boolean textOnly,
            int maxResults) {

        public Options {
            query = query == null ? "" : query;
            masks = masks == null ? List.of() : List.copyOf(masks);
            if (maxResults <= 0) {
                maxResults = DEFAULT_MAX_RESULTS;
            }
        }
    }

    /** Одно совпадение: файл, номер строки (с 1), текст строки. */
    public record Result(Path file, int line, String text) {}

    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** Кооперативно прервать текущий поиск (срабатывает на следующей итерации). */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Ищет совпадения в файлах под {@code root} (рекурсивно).
     *
     * @param onFile колбэк прогресса (полный путь текущего файла), может быть {@code null}
     */
    public List<Result> search(Path root, Options options, Consumer<String> onFile) {
        return search(root, options, onFile, null);
    }

    /**
     * Ищет совпадения в файлах под {@code root} (рекурсивно).
     *
     * @param onFile колбэк прогресса (полный путь текущего файла), может быть {@code null}
     * @param onMatchedFile колбэк при первом совпадении в файле, может быть {@code null}
     */
    public List<Result> search(Path root, Options options, Consumer<String> onFile, Consumer<Path> onMatchedFile) {
        cancelled.set(false);
        List<Result> results = new ArrayList<>();
        SearchPattern pattern = new SearchPattern(options.query(), options.regex(), options.caseSensitive());
        List<PathMatcher> matchers = compileMasks(options.masks());
        walk(root, options, pattern, matchers, results, onFile, onMatchedFile);
        return results;
    }

    /**
     * Читает файл и декодирует его в текст (те же кодировки, что и при поиске:
     * UTF-8, UTF-16 с BOM, windows-1251). Используется для показа содержимого
     * выбранного файла в правой панели.
     */
    public static String readFileContent(Path file) throws IOException {
        return TextFileReader.read(file);
    }

    private void walk(Path dir, Options options, SearchPattern pattern, List<PathMatcher> matchers,
                      List<Result> results, Consumer<String> onFile, Consumer<Path> onMatchedFile) {
        if (cancelled.get() || results.size() >= options.maxResults()) {
            return;
        }
        List<Path> files = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                if (cancelled.get()) {
                    return;
                }
                if (Files.isSymbolicLink(entry)) {
                    continue; // не заходим в симлинки — защита от циклов
                }
                if (Files.isDirectory(entry)) {
                    dirs.add(entry);
                } else if (Files.isRegularFile(entry)) {
                    files.add(entry);
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // недоступная директория — пропускаем
            return;
        }
        for (Path file : files) {
            if (cancelled.get() || results.size() >= options.maxResults()) {
                return;
            }
            searchFile(file, options, pattern, matchers, results, onFile, onMatchedFile);
        }
        for (Path sub : dirs) {
            if (cancelled.get() || results.size() >= options.maxResults()) {
                return;
            }
            walk(sub, options, pattern, matchers, results, onFile, onMatchedFile);
        }
    }

    private void searchFile(Path file, Options options, SearchPattern pattern, List<PathMatcher> matchers,
                            List<Result> results, Consumer<String> onFile, Consumer<Path> onMatchedFile) {
        if (!matchesMask(file, matchers)) {
            return;
        }
        if (onFile != null) {
            onFile.accept(file.toString());
        }
        byte[] bytes;
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return;
            }
            bytes = TextFileReader.readBytes(file);
        } catch (IOException e) {
            return; // битые или занятые файлы пропускаем
        }
        if (options.textOnly() && TextFileReader.isBinary(bytes) && !TextFileReader.hasUtf16Bom(bytes)) {
            return;
        }
        String content = TextFileReader.decodeBytes(bytes);
        int lineNo = 0;
        boolean matchedFileNotified = false;
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (pattern.matches(line)) {
                    if (!matchedFileNotified && onMatchedFile != null) {
                        onMatchedFile.accept(file);
                        matchedFileNotified = true;
                    }
                    results.add(new Result(file, lineNo, line));
                    if (results.size() >= options.maxResults()) {
                        return;
                    }
                }
                if (cancelled.get()) {
                    return;
                }
            }
        } catch (IOException e) {
            // со StringReader не должно случаться, но не прерываем обход
        }
    }

    private static List<PathMatcher> compileMasks(List<String> masks) {
        FileSystem fs = FileSystems.getDefault();
        List<PathMatcher> matchers = new ArrayList<>();
        for (String mask : masks) {
            String trimmed = mask.trim();
            if (!trimmed.isEmpty()) {
                matchers.add(fs.getPathMatcher("glob:" + trimmed));
            }
        }
        return matchers;
    }

    private static boolean matchesMask(Path file, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) {
            return true;
        }
        Path name = Path.of(file.getFileName().toString());
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(name)) {
                return true;
            }
        }
        return false;
    }

}
