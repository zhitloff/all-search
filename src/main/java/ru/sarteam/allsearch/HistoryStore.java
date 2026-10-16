package ru.sarteam.allsearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Персистентное хранилище истории полей поиска. */
final class HistoryStore {

    static final int DIRECTORY_LIMIT = 20;
    static final int MASK_LIMIT = 20;

    private final Path file;

    HistoryStore(Path file) {
        this.file = file;
    }

    History load() {
        if (!Files.isRegularFile(file)) {
            return new History(List.of(), List.of());
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<String> directories = limit(parseArrayByKey(json, "directories"), DIRECTORY_LIMIT);
            List<String> masks = limit(parseArrayByKey(json, "masks"), MASK_LIMIT);
            if (masks.isEmpty()) {
                masks = limit(parseLegacyArray(json), MASK_LIMIT);
            }
            return new History(directories, masks);
        } catch (IOException ignored) {
            return new History(List.of(), List.of());
        }
    }

    void save(List<String> directories, List<String> masks) {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder json = new StringBuilder("{\n  \"directories\": ");
            appendArray(json, directories);
            json.append(",\n  \"masks\": ");
            appendArray(json, masks);
            json.append("\n}\n");
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // История не критична для основной функциональности.
        }
    }

    static void remember(List<String> values, String rawValue, int limit) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return;
        }
        values.remove(value);
        values.add(0, value);
        if (values.size() > limit) {
            values.subList(limit, values.size()).clear();
        }
    }

    private static List<String> parseArrayByKey(String json, String key) {
        int keyPos = json.indexOf('"' + key + '"');
        if (keyPos < 0) {
            return List.of();
        }
        int start = json.indexOf('[', keyPos);
        int end = findArrayEnd(json, start);
        return end < 0 ? List.of() : parseStrings(json.substring(start + 1, end));
    }

    private static int findArrayEnd(String json, int start) {
        if (start < 0) {
            return -1;
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '[') {
                depth++;
            } else if (json.charAt(i) == ']' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> parseLegacyArray(String json) {
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        return start < 0 || end <= start ? List.of() : parseStrings(json.substring(start + 1, end));
    }

    private static List<String> parseStrings(String body) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (!inString) {
                if (ch == '"') {
                    inString = true;
                    value.setLength(0);
                }
                continue;
            }
            if (escaped) {
                value.append(switch (ch) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> ch;
                });
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                result.add(value.toString());
                inString = false;
            } else {
                value.append(ch);
            }
        }
        return result;
    }

    private static List<String> limit(List<String> values, int limit) {
        return values.size() <= limit ? values : List.copyOf(values.subList(0, limit));
    }

    private static void appendArray(StringBuilder out, List<String> values) {
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append('"').append(escape(values.get(i))).append('"');
        }
        out.append(']');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    record History(List<String> directories, List<String> masks) {}
}
