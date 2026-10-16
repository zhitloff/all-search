package ru.sarteam.allsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
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

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final long MAX_FILE_BYTES = 256L * 1024 * 1024;
    private static final int BINARY_PROBE = 8192;

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
        cancelled.set(false);
        List<Result> results = new ArrayList<>();
        Pattern pattern = options.regex() ? compileRegex(options.query(), options.caseSensitive()) : null;
        String needle = options.caseSensitive() ? options.query() : options.query().toLowerCase(Locale.ROOT);
        List<PathMatcher> matchers = compileMasks(options.masks());
        walk(root, options, pattern, needle, matchers, results, onFile);
        return results;
    }

    /**
     * Читает файл и декодирует его в текст (те же кодировки, что и при поиске:
     * UTF-8, UTF-16 с BOM, windows-1251). Используется для показа содержимого
     * выбранного файла в правой панели.
     */
    public static String readFileContent(Path file) throws IOException {
        return decode(Files.readAllBytes(file));
    }

    private void walk(Path dir, Options options, Pattern pattern, String needle, List<PathMatcher> matchers,
                      List<Result> results, Consumer<String> onFile) {
        if (cancelled.get()) {
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
            if (cancelled.get()) {
                return;
            }
            searchFile(file, options, pattern, needle, matchers, results, onFile);
        }
        for (Path sub : dirs) {
            walk(sub, options, pattern, needle, matchers, results, onFile);
        }
    }

    private void searchFile(Path file, Options options, Pattern pattern, String needle, List<PathMatcher> matchers,
                            List<Result> results, Consumer<String> onFile) {
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
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return; // битые или занятые файлы пропускаем
        }
        if (options.textOnly() && isBinary(bytes) && !hasUtf16Bom(bytes)) {
            return;
        }
        String content = decode(bytes);
        int lineNo = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (matches(line, pattern, needle, options)) {
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

    private static boolean matches(String line, Pattern pattern, String needle, Options options) {
        if (pattern != null) {
            return pattern.matcher(line).find();
        }
        if (options.caseSensitive()) {
            return line.contains(needle);
        }
        return line.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Pattern compileRegex(String query, boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(query, flags);
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

    private static boolean isBinary(byte[] bytes) {
        int n = Math.min(bytes.length, BINARY_PROBE);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUtf16Bom(byte[] bytes) {
        return bytes.length >= 2 && (((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE)
                || ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF));
    }

    private static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (isValidUtf8(bytes)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return new String(bytes, WINDOWS_1251); // русскоязычный fallback
    }

    private static boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
